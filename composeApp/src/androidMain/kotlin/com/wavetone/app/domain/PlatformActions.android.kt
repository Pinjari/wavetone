package com.wavetone.app.domain

import android.content.Intent
import android.provider.Settings
import com.wavetone.app.AndroidContext

actual fun openAccessibilitySettings() {
    val ctx = AndroidContext.appContext ?: return
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        // Settings screen unavailable on this device; dashboard text still guides the user.
    }
}
