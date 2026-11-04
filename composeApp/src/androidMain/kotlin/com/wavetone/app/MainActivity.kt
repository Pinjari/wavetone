package com.wavetone.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.wavetone.app.services.CallHudOverlayManager

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Dashboard remains usable; audio features degrade gracefully without mic. */ }

    /**
     * When a REAL (non-simulated) call returns to IDLE, the attestation
     * session is over: clear it so the Scam-Breaker shield disarms itself
     * instead of lingering after hang-up.
     */
    private val callStateListener = object : android.telephony.PhoneStateListener() {
        @Deprecated("Legacy listener; TelephonyCallback requires API 31 and this covers minSdk 28")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            val session = com.wavetone.app.domain.CallSecuritySession
            if (state != android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                // A call is ringing/starting: the demo mic must never contend
                // with call audio — release it before the call connects.
                session.requestStopListening()
            }
            if (state == android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                if (session.sessionActive.value && !session.session.value.isSimulated) {
                    session.endSession()
                }
            }
        }
    }

    override fun onStop() {
        // Never leave the microphone running while the app is in the
        // background (privacy, battery, and call-audio contention).
        com.wavetone.app.domain.CallSecuritySession.requestStopListening()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContext.init(applicationContext)
        com.wavetone.app.domain.CallSecuritySession.attachStore(com.wavetone.app.domain.AndroidKeyValueStore())
        requestRuntimePermissions()
        registerCallStateListener()
        setContent { App() }
        // HUD pill only when the user has already granted overlay permission;
        // never hijack first launch into system settings.
        if (CallHudOverlayManager.canDraw(this)) {
            CallHudOverlayManager.show(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()
        // listen() needs READ_PHONE_STATE; if it was granted after onCreate
        // (or denied at first ask), retrying here keeps auto-clear working.
        registerCallStateListener()
    }

    private fun registerCallStateListener() {
        try {
            val tm = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            @Suppress("DEPRECATION")
            tm.listen(callStateListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            // No telephony on this device; simulated sessions are unaffected.
        }
    }

    override fun onDestroy() {
        try {
            val tm = getSystemService(TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            @Suppress("DEPRECATION")
            tm.listen(callStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
            // Already unregistered.
        }
        super.onDestroy()
    }

    private fun requestRuntimePermissions() {
        // Ask once, never nag on every launch. If the user declines, the
        // dashboard explains what each permission unlocks instead of
        // re-prompting; grants can be changed in system settings anytime.
        val prefs = getSharedPreferences("wavetone_prefs", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_PERMISSIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_PERMISSIONS, true).apply()
        val wanted = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private companion object {
        const val KEY_ASKED_PERMISSIONS = "asked_runtime_permissions_v1"
    }
}
