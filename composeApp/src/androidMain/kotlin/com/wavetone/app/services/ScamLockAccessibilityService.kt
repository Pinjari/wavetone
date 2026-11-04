package com.wavetone.app.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.wavetone.app.AndroidContext
import com.wavetone.app.domain.CallSecuritySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Scam-Breaker: during a FAILED/UNPAIRED active call session, blocks the
 * bottom 65% of high-risk payment/remote-access apps (and any window
 * advertising "Pay ₹" / "Enter UPI PIN") with a TYPE_ACCESSIBILITY_OVERLAY
 * shield — no SYSTEM_ALERT_WINDOW permission required.
 */
class ScamLockAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var shieldView: View? = null
    private var sessionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var overrideRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AndroidContext.init(applicationContext)
        CallSecuritySession.attachStore(com.wavetone.app.domain.AndroidKeyValueStore())
        CallSecuritySession.shieldInstanceConnected(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sessionJob?.cancel()
        sessionJob = scope.launch {
            // Evaluate against the ACTUAL foreground window, not the last seen
            // package — otherwise a status flip while the user sits on the
            // dashboard could raise the shield over the wrong app.
            CallSecuritySession.status.collectLatest {
                val current = try {
                    rootInActiveWindow?.packageName?.toString()
                } catch (e: Exception) {
                    null
                }
                evaluateLockdown(current ?: lastKnownPackage)
            }
        }
    }

    private var lastKnownPackage: String = ""
    private var lastEvalMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty()) lastKnownPackage = pkg
        // Content-change events fire in storms on complex payment apps; the
        // full node-tree scan is throttled (window-state changes always run).
        // Throttling must be trailing-edge: if the burst ends inside the
        // window, the final state still gets evaluated once it closes —
        // otherwise a static payment screen could strand the shield.
        val now = android.os.SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            shieldView == null && now - lastEvalMs < EVAL_THROTTLE_MS
        ) {
            scheduleTrailingEvaluation()
            return
        }
        lastEvalMs = now
        evaluateLockdown(pkg)
    }

    private val trailingEvalRunnable = Runnable {
        lastEvalMs = android.os.SystemClock.uptimeMillis()
        evaluateLockdown(lastKnownPackage)
    }

    private fun scheduleTrailingEvaluation() {
        handler.removeCallbacks(trailingEvalRunnable)
        handler.postDelayed(trailingEvalRunnable, EVAL_THROTTLE_MS)
    }

    override fun onInterrupt() {
        removeShield()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        CallSecuritySession.shieldInstanceDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CallSecuritySession.shieldInstanceDisconnected(this)
        sessionJob?.cancel()
        removeShield()
        super.onDestroy()
    }

    private fun evaluateLockdown(foregroundPackage: String) {
        if (!CallSecuritySession.shouldLockdown()) {
            removeShield()
            return
        }
        // Once up, the shield persists until the session unlocks, the user
        // overrides, or End Call is pressed. Re-scanning here would let the
        // overlay's own window events (root = shield, no payment text)
        // instantly dismiss it.
        if (shieldView != null) return
        if (isHighRiskSurface(foregroundPackage)) showShield()
    }

    private fun isHighRiskSurface(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        if (pkg in HIGH_RISK_PACKAGES) return true
        // Own app only counts when its window actually shows payment UI (Mock UPI demo screen).
        if (pkg == packageName) return windowHasPaymentText()
        return false
    }

    private fun windowHasPaymentText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val found = containsPaymentText(root)
        root.recycle()
        return found
    }

    private fun containsPaymentText(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (PAYMENT_MARKERS.any { text.contains(it, ignoreCase = true) || desc.contains(it, ignoreCase = true) }) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val hit = containsPaymentText(child)
                child.recycle()
                if (hit) return true
            }
        }
        return false
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun showShield() {
        if (shieldView != null) return
        val wm = windowManager ?: return
        val session = CallSecuritySession.session.value

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(245, 120, 4, 24))
            setPadding(48, 48, 48, 48)
        }
        val isUnpaired = CallSecuritySession.status.value ==
            com.wavetone.app.domain.VerificationStatus.UNPAIRED_CALLER_LOCKDOWN
        val title = TextView(this).apply {
            text = if (isUnpaired) "CRITICAL: UNPAIRED CALLER" else "CRITICAL: HARDWARE HANDSHAKE FAILED"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val body = TextView(this).apply {
            text = if (isUnpaired) {
                "Active caller (${session.callerName} ${session.callerPhone}) is not paired with this device. " +
                    "UPI & payment actions are locked during unverified calls to prevent deepfake / coercion fraud."
            } else {
                "Active caller (${session.callerName} ${session.callerPhone}) failed Wavetone cryptographic " +
                    "attestation. UPI & payment actions are locked to prevent deepfake / coercion fraud."
            }
            setTextColor(Color.rgb(255, 205, 210))
            textSize = 14f
        }
        val endCallButton = Button(this).apply {
            text = "End Call & Unlock"
            setOnClickListener {
                val canEndCall = androidx.core.content.ContextCompat.checkSelfPermission(
                    this@ScamLockAccessibilityService, android.Manifest.permission.ANSWER_PHONE_CALLS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (canEndCall) {
                    try {
                        val telecom = getSystemService(TELECOM_SERVICE) as? TelecomManager
                        @Suppress("DEPRECATION")
                        telecom?.endCall()
                    } catch (e: SecurityException) {
                        // End-call not permitted for this role; still clear the local session.
                    }
                }
                CallSecuritySession.endSession()
                removeShield()
            }
        }
        val overrideButton = Button(this).apply {
            text = "Hold 3 Seconds to Override"
            setOnTouchListener { _, motionEvent ->
                when (motionEvent.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        text = "Keep holding..."
                        overrideRunnable = Runnable {
                            CallSecuritySession.overrideLockdown()
                            removeShield()
                        }
                        handler.postDelayed(overrideRunnable!!, 3000L)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        text = "Hold 3 Seconds to Override"
                        overrideRunnable?.let { handler.removeCallbacks(it) }
                        overrideRunnable = null
                        true
                    }
                    else -> false
                }
            }
        }
        container.addView(title)
        container.addView(body)
        container.addView(endCallButton)
        container.addView(overrideButton)

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (metrics.heightPixels * 0.65).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        try {
            wm.addView(container, params)
            shieldView = container
        } catch (e: Exception) {
            shieldView = null
        }
    }

    private fun removeShield() {
        val view = shieldView ?: return
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            // Already detached.
        }
        shieldView = null
    }

    companion object {
        private const val EVAL_THROTTLE_MS = 150L

        val HIGH_RISK_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "in.org.npci.upiapp",                     // BHIM UPI
            "com.whatsapp",                           // WhatsApp (Pay / call screen-share risk)
            "com.anydesk.anydeskandroid",             // AnyDesk
            "com.teamviewer.teamviewer.market.mobile" // TeamViewer
        )
        val PAYMENT_MARKERS = listOf("Pay ₹", "Pay Rs", "Send Money", "Enter UPI PIN", "UPI PIN")
    }
}
