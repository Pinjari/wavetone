package com.wavetone.app.crypto

actual class PlatformLock {
    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
