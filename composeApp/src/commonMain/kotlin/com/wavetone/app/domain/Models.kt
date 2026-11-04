package com.wavetone.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class PairedContact(
    val phoneNumber: String,
    val displayName: String,
    val publicKeyBase64: String,
    val sharedSecretHex: String,
    val isHardwareBacked: Boolean
)

enum class VerificationStatus {
    IDLE,
    VERIFYING_HANDSHAKE,
    VERIFIED_HARDWARE,
    VERIFIED_PAIRED_DEVICE,
    FAILED_SPOOF_DETECTED,
    UNPAIRED_CALLER_LOCKDOWN
}

@Serializable
data class OobHandshakePacket(
    val type: String,
    val callerPhone: String,
    val receiverPhone: String,
    val timestampWindow: Long,
    val nonceHex: String,
    val signatureBase64: String = "",
    val isInActiveCall: Boolean = false
)

data class CallSession(
    val callerPhone: String = "",
    val callerName: String = "",
    val isSimulated: Boolean = false
)

fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }
