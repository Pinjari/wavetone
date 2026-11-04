package com.wavetone.app.crypto

import com.wavetone.app.domain.PairedContact
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Dual-mode handshake protocol.
 *
 * OOB (WebSocket): full ECDSA P-256 signature over a canonical payload.
 * Acoustic: 8-byte payload (2B salt + 5B truncated HMAC-SHA256 + 1B CRC-8)
 * framed by 0xAA 0x7E preamble in [com.wavetone.app.audio.FskModem].
 *
 * NOTE: the acoustic token proves possession of the paired shared secret
 * ("paired device"), NOT hardware enclave possession. Only the OOB ECDSA
 * path may produce a hardware-verified result.
 */
@OptIn(ExperimentalEncodingApi::class)
object HandshakeProtocol {

    const val WINDOW_SECONDS = 15L
    const val ACOUSTIC_PAYLOAD_SIZE = 8
    const val MAC_SIZE = 5

    fun currentWindow(nowEpochSeconds: Long): Long = nowEpochSeconds / WINDOW_SECONDS

    /** Canonical OOB payload bytes that get ECDSA-signed. Delimiter-joined, no ambiguity. */
    fun buildOobPayload(callerPhone: String, receiverPhone: String, window: Long, nonceHex: String): ByteArray =
        "WAVETONE-OOB-v1|$callerPhone|$receiverPhone|$window|$nonceHex".encodeToByteArray()

    fun buildOobResponse(
        signer: HardwareSigner,
        callerPhone: String,
        receiverPhone: String,
        window: Long,
        nonceHex: String,
        signature: ByteArray
    ): com.wavetone.app.domain.OobHandshakePacket = com.wavetone.app.domain.OobHandshakePacket(
        type = "RESPONSE",
        callerPhone = callerPhone,
        receiverPhone = receiverPhone,
        timestampWindow = window,
        nonceHex = nonceHex,
        signatureBase64 = Base64.encode(signature),
        isInActiveCall = signer.isDeviceInActiveCall()
    )

    fun verifyOobResponse(
        signer: HardwareSigner,
        packet: com.wavetone.app.domain.OobHandshakePacket,
        contact: PairedContact,
        nowEpochSeconds: Long
    ): Boolean {
        if (packet.type != "RESPONSE") return false
        val nowWindow = currentWindow(nowEpochSeconds)
        if (packet.timestampWindow < nowWindow - 1 || packet.timestampWindow > nowWindow + 1) return false
        if (packet.callerPhone != contact.phoneNumber) return false
        val signature = try {
            Base64.decode(packet.signatureBase64)
        } catch (e: Exception) {
            return false
        }
        val payload = buildOobPayload(packet.callerPhone, packet.receiverPhone, packet.timestampWindow, packet.nonceHex)
        return signer.verifySignature(payload, signature, contact.publicKeyBase64)
    }

    private fun acousticMessage(callerPhone: String, receiverPhone: String, window: Long, salt: Int): ByteArray =
        "WAVETONE-AC-v1|$callerPhone|$receiverPhone|$window|$salt".encodeToByteArray()

    /**
     * Builds the 8-byte acoustic payload: [salt:2 BE][HMAC-SHA256 truncated:5][CRC-8:1].
     * CRC-8 covers the first 7 bytes (salt + MAC).
     */
    fun buildAcousticPayload(contact: PairedContact, callerPhone: String, receiverPhone: String, nowEpochSeconds: Long, salt: Int): ByteArray {
        val key = contact.sharedSecretHex.hexToBytes()
        val mac = Sha256.hmac(key, acousticMessage(callerPhone, receiverPhone, currentWindow(nowEpochSeconds), salt))
        val payload = ByteArray(ACOUSTIC_PAYLOAD_SIZE)
        payload[0] = (salt ushr 8).toByte()
        payload[1] = salt.toByte()
        mac.copyInto(payload, destinationOffset = 2, endIndex = MAC_SIZE)
        payload[7] = Sha256.crc8(payload, 0, 7)
        return payload
    }

    fun acousticCrcValid(payload: ByteArray): Boolean =
        payload.size == ACOUSTIC_PAYLOAD_SIZE && Sha256.crc8(payload, 0, 7) == payload[7]

    /**
     * Verifies a decoded acoustic payload against every paired contact (as caller)
     * with [myPhone] as receiver, tolerating window T-1/T/T+1. Successful salts are
     * remembered for 60s to block in-window replay.
     */
    fun verifyAcousticPayload(payload: ByteArray, myPhone: String, contacts: List<PairedContact>, nowEpochSeconds: Long): PairedContact? {
        if (!acousticCrcValid(payload)) return null
        val salt = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val receivedMac = payload.copyOfRange(2, 2 + MAC_SIZE)
        ReplayGuard.prune(nowEpochSeconds)
        for (contact in contacts) {
            val key = try {
                contact.sharedSecretHex.hexToBytes()
            } catch (e: Exception) {
                continue
            }
            if (key.isEmpty()) continue
            for (delta in -1L..1L) {
                val window = currentWindow(nowEpochSeconds) + delta
                val expected = Sha256.hmac(key, acousticMessage(contact.phoneNumber, myPhone, window, salt))
                    .copyOfRange(0, MAC_SIZE)
                if (Sha256.constantTimeEquals(expected, receivedMac)) {
                    if (ReplayGuard.isReplay(contact.phoneNumber, salt, window, nowEpochSeconds)) return null
                    ReplayGuard.record(contact.phoneNumber, salt, window, nowEpochSeconds)
                    return contact
                }
            }
        }
        return null
    }
}

/** In-memory replay cache: (contact, salt, window) accepted within the last 60 seconds. */
object ReplayGuard {
    private data class Entry(val contactPhone: String, val salt: Int, val window: Long, val seenAtSeconds: Long)

    private val entries = mutableListOf<Entry>()
    private val lock = PlatformLock()
    private const val TTL_SECONDS = 60L

    fun prune(nowEpochSeconds: Long) = lock.withLock {
        entries.removeAll { nowEpochSeconds - it.seenAtSeconds > TTL_SECONDS }
    }

    fun isReplay(contactPhone: String, salt: Int, window: Long, nowEpochSeconds: Long): Boolean = lock.withLock {
        entries.removeAll { nowEpochSeconds - it.seenAtSeconds > TTL_SECONDS }
        entries.any { it.contactPhone == contactPhone && it.salt == salt && it.window == window }
    }

    fun record(contactPhone: String, salt: Int, window: Long, nowEpochSeconds: Long) = lock.withLock {
        entries.add(Entry(contactPhone, salt, window, nowEpochSeconds))
    }

    fun clear() = lock.withLock { entries.clear() }
}
