package com.wavetone.app.crypto

import com.wavetone.app.domain.PairedContact
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandshakeProtocolTest {

    private val myPhone = "919999999999"
    private val contact = PairedContact(
        phoneNumber = "919876543210",
        displayName = "Peer",
        publicKeyBase64 = "",
        sharedSecretHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        isHardwareBacked = true
    )
    private val now = 1_700_000_000L

    @BeforeTest
    fun resetReplay() {
        ReplayGuard.clear()
    }

    @Test
    fun acousticRoundTripVerifies() {
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 4242)
        assertEquals(8, payload.size)
        assertTrue(HandshakeProtocol.acousticCrcValid(payload))
        val verified = HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now)
        assertNotNull(verified)
        assertEquals(contact.phoneNumber, verified?.phoneNumber)
    }

    @Test
    fun acousticWindowTolerance() {
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 777)
        // One window later (15 s) must still verify via T-1 tolerance.
        assertNotNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now + 15))
    }

    @Test
    fun acousticExpiredWindowFails() {
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 778)
        assertNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now + 60))
    }

    @Test
    fun acousticTamperedMacFails() {
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 99)
        payload[4] = (payload[4].toInt() xor 0x01).toByte()
        payload[7] = Sha256.crc8(payload, 0, 7) // fix CRC so only the MAC is wrong
        assertNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now))
    }

    @Test
    fun acousticTamperedCrcFails() {
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 100)
        payload[7] = (payload[7].toInt() xor 0xFF).toByte()
        assertNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now))
    }

    @Test
    fun acousticReplayRejected() {
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 31337)
        assertNotNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now))
        assertNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now))
    }

    @Test
    fun acousticWrongSecretFails() {
        val attacker = contact.copy(sharedSecretHex = "ff".repeat(32))
        val payload = HandshakeProtocol.buildAcousticPayload(attacker, contact.phoneNumber, myPhone, now, salt = 55)
        assertNull(HandshakeProtocol.verifyAcousticPayload(payload, myPhone, listOf(contact), now))
    }

    @Test
    fun oobPayloadIsCanonicalAndWindowed() {
        val a = HandshakeProtocol.buildOobPayload("111", "222", 42L, "abcd")
        val b = HandshakeProtocol.buildOobPayload("111", "222", 42L, "abcd")
        assertTrue(a.contentEquals(b))
        assertEquals(113L, HandshakeProtocol.currentWindow(1700L))
    }
}
