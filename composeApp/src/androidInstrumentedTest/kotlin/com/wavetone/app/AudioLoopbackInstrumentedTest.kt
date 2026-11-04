package com.wavetone.app

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wavetone.app.audio.AcousticDecoder
import com.wavetone.app.audio.AudioTransceiver
import com.wavetone.app.audio.FskModem
import com.wavetone.app.audio.SpectrogramState
import com.wavetone.app.crypto.HandshakeProtocol
import com.wavetone.app.domain.PairedContact
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * REAL over-the-air loopback on device hardware: the BFSK burst leaves the
 * speaker, flies through the air, re-enters the microphone, and must decode
 * through the full Goertzel streaming path. No PCM is fed directly.
 */
@RunWith(AndroidJUnit4::class)
class AudioLoopbackInstrumentedTest {

    private val contact = PairedContact(
        phoneNumber = "919876543210",
        displayName = "Peer",
        publicKeyBase64 = "",
        sharedSecretHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        isHardwareBacked = true
    )
    private val myPhone = "919999999999"

    @Before
    fun setup() {
        // Over-the-air loopback needs a physical speaker+microphone pair.
        val isEmulator = android.os.Build.FINGERPRINT.contains("emulator") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.PRODUCT.startsWith("sdk")
        org.junit.Assume.assumeFalse("requires real audio hardware", isEmulator)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        AndroidContext.init(instrumentation.targetContext)
        instrumentation.uiAutomation.grantRuntimePermission("com.wavetone.app", Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun speakerToMicrophoneAcousticLoopback() {
        runBlocking { runLoopback() }
    }

    private suspend fun runLoopback() {
        val now = System.currentTimeMillis() / 1000
        val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 0x4242)
        val pcm = FskModem.modulateFrame(payload)

        val latch = CountDownLatch(1)
        val decodedRef = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
        val decoder = AcousticDecoder(SpectrogramState()) { frame ->
            decodedRef.set(frame)
            latch.countDown()
        }
        val transceiver = AudioTransceiver()
        val rawPeak = java.util.concurrent.atomic.AtomicInteger(0)
        val diagDecoder = decoder
        val started = transceiver.startCapture(onChunk = { chunk ->
            var peak = 0
            for (s in chunk) {
                val v = if (s < 0) -s.toInt() else s.toInt()
                if (v > peak) peak = v
            }
            if (peak > rawPeak.get()) rawPeak.set(peak)
            diagDecoder.onChunk(chunk)
        })
        assertTrue("microphone must open on device", started)
        try {
            delay(400) // let capture settle
            transceiver.playPcm(pcm)
            android.util.Log.i("WavetoneAudio", "capture peak during playback=${rawPeak.get()}")
            withTimeoutOrNull(6000) {
                var waited = 0L
                while (decodedRef.get() == null && waited < 5500) {
                    delay(100); waited += 100
                }
            }
            val decoded = decodedRef.get()
            assertNotNull("burst played over speaker must be decoded from microphone input", decoded)
            assertTrue("decoded payload must match transmitted payload", payload.contentEquals(decoded!!))
            val verified = HandshakeProtocol.verifyAcousticPayload(decoded, myPhone, listOf(contact), System.currentTimeMillis() / 1000)
            assertEquals(contact.phoneNumber, verified?.phoneNumber)
        } finally {
            transceiver.stopCapture()
        }
        assertTrue(latch.count == 0L || decodedRef.get() != null)
        latch.await(1, TimeUnit.MILLISECONDS)

        // Replay attack: re-verifying the SAME recorded frame inside its
        // validity window must be rejected by the replay guard.
        val replayed = HandshakeProtocol.verifyAcousticPayload(
            decodedRef.get()!!, myPhone, listOf(contact), System.currentTimeMillis() / 1000
        )
        assertEquals("replayed frame must be rejected", null, replayed)
    }

    /** Realistic quiet-speaker case: burst at 25% amplitude must still decode over the air. */
    @Test
    fun quietSpeakerLoopbackStillDecodes() {
        runBlocking {
            val now = System.currentTimeMillis() / 1000
            val payload = HandshakeProtocol.buildAcousticPayload(contact, contact.phoneNumber, myPhone, now, salt = 0x5151)
            val loud = FskModem.modulateFrame(payload)
            val quiet = ShortArray(loud.size) { (loud[it] * 0.25).toInt().toShort() }

            val decodedRef = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
            val decoder = AcousticDecoder(SpectrogramState()) { decodedRef.set(it) }
            val transceiver = AudioTransceiver()
            assertTrue(transceiver.startCapture(onChunk = { chunk -> decoder.onChunk(chunk) }))
            try {
                delay(400)
                transceiver.playPcm(quiet)
                var waited = 0L
                while (decodedRef.get() == null && waited < 5500) {
                    delay(100); waited += 100
                }
                val decoded = decodedRef.get()
                assertNotNull("25%-amplitude burst must still decode over the air", decoded)
                assertTrue(payload.contentEquals(decoded!!))
            } finally {
                transceiver.stopCapture()
            }
        }
    }
}
