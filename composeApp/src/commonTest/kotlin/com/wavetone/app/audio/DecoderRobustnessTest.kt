package com.wavetone.app.audio

import com.wavetone.app.crypto.HandshakeProtocol
import com.wavetone.app.domain.PairedContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecoderRobustnessTest {

    private val contact = PairedContact(
        phoneNumber = "919876543210",
        displayName = "Peer",
        publicKeyBase64 = "",
        sharedSecretHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        isHardwareBacked = true
    )
    private val myPhone = "919999999999"

    private fun payload(salt: Int): ByteArray =
        HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, 1_700_000_000L, salt)

    /** Mic buffers start at arbitrary sample offsets relative to a burst — sync must tolerate all of them. */
    @Test
    fun arbitrarySampleOffsetsStillDecode() {
        val p = payload(0x2222)
        val frame = FskModem.modulateFrame(p)
        for (offset in listOf(0, 37, 100, 175, 200, 351, 1000, 5000, 12345)) {
            val pcm = ShortArray(offset) + frame + ShortArray(2000)
            val frames = FskModem.extractFrames(pcm)
            assertEquals(1, frames.size, "offset $offset should decode exactly one frame")
            assertTrue(p.contentEquals(frames[0]), "offset $offset payload mismatch")
        }
    }

    /** Streaming path: the exact class the mic feeds, chunked at 2048 samples like AudioRecord. */
    @Test
    fun chunkedStreamingDecodeEmitsPayloadOnce() {
        val p = payload(0x3333)
        val stream = ShortArray(3000) + FskModem.modulateFrame(p) + ShortArray(6000)
        val received = mutableListOf<ByteArray>()
        val decoder = AcousticDecoder(SpectrogramState()) { received.add(it) }
        var i = 0
        while (i < stream.size) {
            val end = minOf(i + 2048, stream.size)
            decoder.onChunk(stream.copyOfRange(i, end))
            i = end
        }
        assertEquals(1, received.size)
        assertTrue(p.contentEquals(received[0]))
    }

    /** Silence must never produce a phantom frame. */
    @Test
    fun silenceDecodesNothing() {
        val received = mutableListOf<ByteArray>()
        val decoder = AcousticDecoder(SpectrogramState()) { received.add(it) }
        repeat(20) { decoder.onChunk(ShortArray(2048)) }
        assertTrue(received.isEmpty())
        assertTrue(FskModem.extractFrames(ShortArray(FskModem.FRAME_TOTAL_SAMPLES * 2)).isEmpty())
    }

    /** Bursts for a different receiver phone must not verify (message binds both numbers). */
    @Test
    fun wrongReceiverPhoneRejected() {
        val p = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, "910000000001", 1_700_000_000L, 0x4444)
        assertEquals(null, HandshakeProtocol.verifyAcousticPayload(p, myPhone, listOf(contact), 1_700_000_000L))
    }

    /** Exact 15 s window edges: W verifies at W and W+1, never at W+2. */
    @Test
    fun exactWindowBoundaries() {
        val boundary = 1_700_000_100L // divisible by 15
        val p = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, boundary, 0x5555)
        assertTrue(HandshakeProtocol.verifyAcousticPayload(p, myPhone, listOf(contact), boundary) != null)
        com.wavetone.app.crypto.ReplayGuard.clear()
        assertTrue(HandshakeProtocol.verifyAcousticPayload(p, myPhone, listOf(contact), boundary + 15) != null)
        com.wavetone.app.crypto.ReplayGuard.clear()
        assertEquals(null, HandshakeProtocol.verifyAcousticPayload(p, myPhone, listOf(contact), boundary + 30))
    }
}
