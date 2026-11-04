package com.wavetone.app.crypto

/** Minimal mutual-exclusion lock for decoder/replay state shared across audio threads. */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
