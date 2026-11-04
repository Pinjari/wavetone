package com.wavetone.app.domain

import platform.Foundation.NSUserDefaults

actual fun createPlatformStore(): KeyValueStore = IosKeyValueStore()

class IosKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun put(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}
