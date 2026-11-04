package com.wavetone.app.crypto

expect class HardwareSigner() {
    /** Generates EC P-256 keypair in StrongBox (TEE fallback). Returns true iff StrongBox used. */
    suspend fun ensureIdentityKeyGenerated(): Boolean

    /** X.509 SPKI Base64 public key for pairing. Empty string if unavailable on this platform. */
    fun getPublicKeyBase64(): String

    /** SHA256withECDSA signature (DER) produced inside the hardware enclave. */
    suspend fun signPayload(data: ByteArray): ByteArray

    /** Verifies an ECDSA signature against a paired contact's X.509 Base64 public key. */
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKeyBase64: String): Boolean

    /** True when telephony/VoIP audio mode indicates an active call. */
    fun isDeviceInActiveCall(): Boolean

    /** Human label, e.g. "StrongBox", "TEE", "Software", "Unavailable (iOS stub)". */
    fun hardwareLabel(): String
}

/** Platform secure random source for shared-secret / salt generation. */
expect fun secureRandomBytes(count: Int): ByteArray
