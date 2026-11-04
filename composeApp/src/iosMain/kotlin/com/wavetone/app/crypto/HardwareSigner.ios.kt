package com.wavetone.app.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.arc4random_buf

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    if (count == 0) return bytes
    bytes.usePinned { pinned ->
        arc4random_buf(pinned.addressOf(0), count.toULong())
    }
    return bytes
}

/**
 * iOS companion build: Secure Enclave signing requires a CryptoKit Swift
 * bridge and is intentionally NOT faked here. Verification entry points
 * report unavailable/false so the UI can never show a false "verified".
 */
actual class HardwareSigner {

    actual suspend fun ensureIdentityKeyGenerated(): Boolean = false

    actual fun getPublicKeyBase64(): String = ""

    actual suspend fun signPayload(data: ByteArray): ByteArray =
        throw UnsupportedOperationException("Secure Enclave signing requires the CryptoKit Swift bridge in the full iOS build")

    actual fun verifySignature(data: ByteArray, signature: ByteArray, publicKeyBase64: String): Boolean = false

    actual fun isDeviceInActiveCall(): Boolean = false

    actual fun hardwareLabel(): String = "Unavailable (iOS companion build)"
}
