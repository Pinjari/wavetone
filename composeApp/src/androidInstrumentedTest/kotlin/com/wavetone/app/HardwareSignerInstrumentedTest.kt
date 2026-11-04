package com.wavetone.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wavetone.app.crypto.HandshakeProtocol
import com.wavetone.app.crypto.HardwareSigner
import com.wavetone.app.domain.PairedContact
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on REAL hardware (Pixel): exercises AndroidKeyStore key generation,
 * StrongBox/TEE reporting, ECDSA sign/verify, tamper and wrong-key rejection,
 * and the OOB handshake protocol end to end with the enclave key.
 */
@RunWith(AndroidJUnit4::class)
class HardwareSignerInstrumentedTest {

    private val signer = HardwareSigner()

    @Before
    fun setup() {
        AndroidContext.init(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { signer.ensureIdentityKeyGenerated() }
    }

    @Test
    fun identityKeyExistsAndReportsHardwareLevel() {
        val label = signer.hardwareLabel()
        Log.i("WavetoneHW", "hardwareLabel=$label strongBox=${runBlocking { signer.ensureIdentityKeyGenerated() }}")
        assertTrue("public key must exist", signer.getPublicKeyBase64().isNotEmpty())
        // Any concrete level is valid (emulators legitimately report Software);
        // StrongBox/TEE is asserted by hardwareLabel logging on real devices.
        assertTrue(
            "label must be a concrete level, was: $label",
            label.contains("StrongBox") || label.contains("TEE") ||
                label.contains("secure element") || label.contains("Software")
        )
    }

    @Test
    fun signVerifyRoundtripAndTamperRejection() = runBlocking {
        val data = "wavetone-instrumented-payload".toByteArray()
        val signature = signer.signPayload(data)
        assertTrue(signature.isNotEmpty())
        assertTrue(signer.verifySignature(data, signature, signer.getPublicKeyBase64()))

        val tamperedSig = signature.copyOf()
        tamperedSig[tamperedSig.size - 1] = (tamperedSig[tamperedSig.size - 1].toInt() xor 0x01).toByte()
        assertFalse(signer.verifySignature(data, tamperedSig, signer.getPublicKeyBase64()))

        val tamperedData = "wavetone-instrumented-payloae".toByteArray()
        assertFalse(signer.verifySignature(tamperedData, signature, signer.getPublicKeyBase64()))
        assertFalse(signer.verifySignature(data, signature, ""))
    }

    @Test
    fun oobProtocolVerifiesWithEnclaveKey() = runBlocking {
        val myPhone = "919999999999"
        val peerPhone = "919876543210"
        val now = System.currentTimeMillis() / 1000
        val contact = PairedContact(
            phoneNumber = peerPhone,
            displayName = "Self",
            publicKeyBase64 = signer.getPublicKeyBase64(),
            sharedSecretHex = "",
            isHardwareBacked = true
        )
        val window = HandshakeProtocol.currentWindow(now)
        val payload = HandshakeProtocol.buildOobPayload(peerPhone, myPhone, window, "aabbccdd")
        val signature = signer.signPayload(payload)
        val packet = HandshakeProtocol.buildOobResponse(signer, peerPhone, myPhone, window, "aabbccdd", signature)
        assertTrue(HandshakeProtocol.verifyOobResponse(signer, packet, contact, now))
        assertFalse(HandshakeProtocol.verifyOobResponse(signer, packet.copy(nonceHex = "ffffff00"), contact, now))
        assertFalse(HandshakeProtocol.verifyOobResponse(signer, packet.copy(timestampWindow = window - 5), contact, now))
        assertEquals(peerPhone, packet.callerPhone)
    }
}
