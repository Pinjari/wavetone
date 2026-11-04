package com.wavetone.app.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.wavetone.app.domain.CallSecuritySession
import com.wavetone.app.domain.VerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating status pill (system-alert window). Requires SYSTEM_ALERT_WINDOW
 * grant; [show] is a no-op without it. The lockdown shield itself uses
 * TYPE_ACCESSIBILITY_OVERLAY and needs no such permission.
 */
object CallHudOverlayManager {

    private var windowManager: WindowManager? = null
    private var pill: TextView? = null
    private var observeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (!canDraw(context) || pill != null) return
        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(36, 18, 36, 18)
            background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(Color.argb(230, 13, 22, 38))
            }
            text = "WAVETONE: IDLE"
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 150
        }
        try {
            wm.addView(view, params)
            pill = view
        } catch (e: Exception) {
            pill = null
            return
        }
        observeJob?.cancel()
        observeJob = scope.launch {
            CallSecuritySession.status.collectLatest { status -> update(status) }
        }
    }

    private fun update(status: VerificationStatus) {
        val view = pill ?: return
        val (label, color) = when (status) {
            VerificationStatus.IDLE -> "WAVETONE: IDLE" to Color.argb(230, 13, 22, 38)
            VerificationStatus.VERIFYING_HANDSHAKE -> "WAVETONE: VERIFYING..." to Color.argb(235, 0, 90, 110)
            VerificationStatus.VERIFIED_HARDWARE -> "WAVETONE: HARDWARE VERIFIED" to Color.argb(235, 0, 110, 60)
            VerificationStatus.VERIFIED_PAIRED_DEVICE -> "WAVETONE: PAIRED DEVICE VERIFIED" to Color.argb(235, 0, 110, 60)
            VerificationStatus.FAILED_SPOOF_DETECTED -> "WAVETONE: SPOOF DETECTED" to Color.argb(240, 176, 0, 32)
            VerificationStatus.UNPAIRED_CALLER_LOCKDOWN -> "WAVETONE: UNPAIRED — LOCKDOWN" to Color.argb(240, 176, 0, 32)
        }
        view.text = label
        (view.background as? GradientDrawable)?.setColor(color)
    }

    fun hide() {
        observeJob?.cancel()
        observeJob = null
        val view = pill
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View already detached.
            }
        }
        pill = null
    }
}
