package com.wavetone.app.audio

import com.wavetone.app.crypto.HandshakeProtocol
import com.wavetone.app.crypto.Sha256
import com.wavetone.app.domain.PairedContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FskModemTest {

    private fun samplePayload(): ByteArray {
        val contact = PairedContact(
            phoneNumber = "919876543210",
            displayName = "Peer",
            publicKeyBase64 = "",
            sharedSecretHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            isHardwareBacked = true
        )
        return HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, "919999999999", 1_700_000_000L, salt = 0x1234)
    }

    @Test
    fun modulateProducesExpectedLength() {
        val pcm = FskModem.modulateFrame(samplePayload())
        assertEquals(FskModem.FRAME_TOTAL_SAMPLES, pcm.size)
        assertEquals(28160, pcm.size) // 80 bits * 352 samples = ~640 ms @ 44.1 kHz
    }

    @Test
    fun syntheticLoopbackDecodesIdenticalPayload() {
        val payload = samplePayload()
        val pcm = FskModem.modulateFrame(payload)
        val frames = FskModem.extractFrames(pcm)
        assertEquals(1, frames.size)
        assertTrue(payload.contentEquals(frames[0]))
    }

    @Test
    fun loopbackWithLeadingSilenceStillSyncs() {
        val payload = samplePayload()
        val pcm = ShortArray(5000) + FskModem.modulateFrame(payload) + ShortArray(3000)
        val frames = FskModem.extractFrames(pcm)
        assertEquals(1, frames.size)
        assertTrue(payload.contentEquals(frames[0]))
    }

    @Test
    fun loopbackWithAttenuationAndNoiseStillDecodes() {
        val payload = samplePayload()
        val clean = FskModem.modulateFrame(payload)
        val noisy = ShortArray(clean.size) { i ->
            val attenuated = (clean[i] * 0.3).toInt()
            val noise = ((i * 1103515245 + 12345) ushr 16) % 1200 - 600
            (attenuated + noise).coerceIn(-32768, 32767).toShort()
        }
        val frames = FskModem.extractFrames(noisy)
        assertEquals(1, frames.size)
        assertTrue(payload.contentEquals(frames[0]))
    }

    @Test
    fun corruptedBitFailsCrcAndIsRejected() {
        val payload = samplePayload()
        val bad = payload.copyOf()
        bad[3] = (bad[3].toInt() xor 0x40).toByte() // flip a MAC bit, leave stale CRC
        val pcm = FskModem.modulateFrame(bad)
        assertTrue(FskModem.extractFrames(pcm).isEmpty())
        assertTrue(!HandshakeProtocol.acousticCrcValid(bad))
    }

    @Test
    fun goertzelDiscriminatesMarkAndSpace() {
        val payload = samplePayload()
        val pcm = FskModem.modulateFrame(payload)
        // First preamble bit of 0xAA is 1 -> 2400 Hz must dominate in chunk 0.
        val magOne = FskModem.goertzelMagnitude(pcm, 0, FskModem.SAMPLES_PER_BIT, FskModem.FREQ_ONE)
        val magZero = FskModem.goertzelMagnitude(pcm, 0, FskModem.SAMPLES_PER_BIT, FskModem.FREQ_ZERO)
        assertTrue(magOne > magZero, "mark magnitude $magOne should exceed space $magZero")
        // Second preamble bit of 0xAA is 0 -> 1600 Hz must dominate in chunk 1.
        val magOne2 = FskModem.goertzelMagnitude(pcm, FskModem.SAMPLES_PER_BIT, FskModem.SAMPLES_PER_BIT, FskModem.FREQ_ONE)
        val magZero2 = FskModem.goertzelMagnitude(pcm, FskModem.SAMPLES_PER_BIT, FskModem.SAMPLES_PER_BIT, FskModem.FREQ_ZERO)
        assertTrue(magZero2 > magOne2, "space magnitude $magZero2 should exceed mark $magOne2")
        assertEquals(Sha256.crc8(payload, 0, 7), payload[7])
    }
}
