package com.wavetone.app.crypto

import platform.Foundation.NSLock

actual class PlatformLock {
    private val nsLock = NSLock()

    actual fun <T> withLock(block: () -> T): T {
        nsLock.lock()
        try {
            return block()
        } finally {
            nsLock.unlock()
        }
    }
}
