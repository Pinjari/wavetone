package com.wavetone.app

import android.content.Context

/** Process-wide application context holder, initialized by MainActivity and services. */
object AndroidContext {
    @Volatile
    var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }
}
