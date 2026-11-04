package com.wavetone.app.ui

import com.wavetone.app.audio.AcousticDecoder
import com.wavetone.app.audio.AudioTransceiver
import com.wavetone.app.audio.FskModem
import com.wavetone.app.audio.SpectrogramState
import com.wavetone.app.crypto.HandshakeProtocol
import com.wavetone.app.crypto.HardwareSigner
import com.wavetone.app.crypto.hexToBytes
import com.wavetone.app.crypto.secureRandomBytes
import com.wavetone.app.crypto.toHexString
import com.wavetone.app.domain.CallSecuritySession
import com.wavetone.app.domain.ContactVault
import com.wavetone.app.domain.PairedContact
import com.wavetone.app.domain.VerificationStatus
import com.wavetone.app.domain.normalizePhone
import com.wavetone.app.network.OobRelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/** Orchestrates identity, vault, modem, OOB handshake and demo simulations. */
class WavetoneController(
    private val scope: CoroutineScope,
    val vault: ContactVault
) {
    val signer = HardwareSigner()
    val transceiver = AudioTransceiver()
    val spectrogram = SpectrogramState()
    private val oobClient = OobRelayClient()

    private val _identityReady = MutableStateFlow(false)
    val identityReady: StateFlow<Boolean> = _identityReady.asStateFlow()

    private val _strongBox = MutableStateFlow(false)
    val strongBox: StateFlow<Boolean> = _strongBox.asStateFlow()

    private val _publicKeyBase64 = MutableStateFlow("")
    val publicKeyBase64: StateFlow<String> = _publicKeyBase64.asStateFlow()

    private val _statusLine = MutableStateFlow("Booting secure enclave...")
    val statusLine: StateFlow<String> = _statusLine.asStateFlow()

    private val decoder = AcousticDecoder(spectrogram) { payload -> onAcousticPayload(payload) }

    init {
        // The mic is released the moment a real call starts or the app is
        // backgrounded — call audio always wins over the demo listener.
        scope.launch {
            CallSecuritySession.stopListeningRequests.collect { count ->
                if (count > 0 && spectrogram.listening.value) {
                    stopListeningInternal()
                    spectrogram.log("Microphone released (call started or app backgrounded).")
                }
            }
        }
    }

    private fun stopListeningInternal() {
        transceiver.stopCapture()
        spectrogram.setListening(false)
    }

    @OptIn(ExperimentalTime::class)
    fun nowSeconds(): Long = Clock.System.now().epochSeconds

    fun initIdentity() {
        scope.launch {
            try {
                val strong = signer.ensureIdentityKeyGenerated()
                _strongBox.value = strong
                _publicKeyBase64.value = signer.getPublicKeyBase64()
                _identityReady.value = true
                _statusLine.value = "Identity ready: ${signer.hardwareLabel()}"
            } catch (e: Exception) {
                _statusLine.value = "Identity unavailable on this platform: ${e.message}"
            }
        }
    }

    /** SHA-256 fingerprint of the DER (X.509 SPKI) key bytes, grouped for readability. */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun publicKeyFingerprint(): String {
        val key = _publicKeyBase64.value
        if (key.isEmpty()) return "—"
        val der = try {
            kotlin.io.encoding.Base64.decode(key)
        } catch (e: Exception) {
            return "—"
        }
        return com.wavetone.app.crypto.Sha256.hash(der).toHexString()
            .take(16).uppercase().chunked(4).joinToString(" ")
    }

    /** True when the contact's key is this device's own key (demo peer) — loopback OOB can sign as them. */
    fun isLoopbackCapable(contact: PairedContact): Boolean =
        _publicKeyBase64.value.isNotEmpty() && contact.publicKeyBase64 == _publicKeyBase64.value

    /** Plays my own acoustic burst (as caller = my number) for the selected contact pairing. */
    fun emitAcousticBurst(contact: PairedContact) {
        scope.launch(Dispatchers.Default) {
            val salt = (secureRandomBytes(2).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) })
            val payload = HandshakeProtocol.buildAcousticPayload(
                contact, vault.myPhone(), contact.phoneNumber, nowSeconds(), salt
            )
            spectrogram.onFrameDecoded(payload, null)
            spectrogram.log("Emitting burst as ${vault.myPhone()} -> ${contact.phoneNumber} (640 ms BFSK)")
            try {
                transceiver.playPcm(FskModem.modulateFrame(payload))
            } catch (e: Exception) {
                spectrogram.log("Playback failed: ${e.message}")
            }
        }
    }

    /** Simulates the paired peer device emitting its burst over the speaker. */
    fun simulatePeerBurst(contact: PairedContact) {
        scope.launch(Dispatchers.Default) {
            val simulated = if (CallSecuritySession.sessionActive.value) {
                CallSecuritySession.session.value.isSimulated
            } else {
                true
            }
            CallSecuritySession.beginSession(
                contact.phoneNumber, contact.displayName, simulated = simulated,
                status = VerificationStatus.VERIFYING_HANDSHAKE
            )
            val salt = (secureRandomBytes(2).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) })
            val payload = HandshakeProtocol.buildAcousticPayload(
                contact, contact.phoneNumber, vault.myPhone(), nowSeconds(), salt
            )
            spectrogram.log("Peer burst incoming from ${contact.displayName}...")
            val pcm = FskModem.modulateFrame(payload)
            try {
                transceiver.playPcm(pcm)
            } catch (e: Exception) {
                spectrogram.log("Playback failed (continuing with decoder feed): ${e.message}")
            }
            // Also feed the synthesized PCM through the real decoder path so the
            // demo verifies even with no mic (emulator / muted stage). Duplicate
            // delivery via a live mic is neutralized by the dedup guard below.
            decoder.onChunk(pcm)
        }
    }

    fun toggleListening() {
        if (spectrogram.listening.value) {
            stopListeningInternal()
            spectrogram.log("Microphone decoder stopped.")
        } else {
            if (signer.isDeviceInActiveCall()) {
                spectrogram.log("Can't listen during a phone call — the call owns the microphone. Verify after hanging up, or use the OOB handshake.")
                _statusLine.value = "Microphone busy: phone call in progress."
                return
            }
            decoder.reset()
            val started = transceiver.startCapture(
                onChunk = { chunk -> decoder.onChunk(chunk) },
                onError = {
                    spectrogram.setListening(false)
                    spectrogram.log("Microphone stopped (a call started or the device seized the mic).")
                }
            )
            if (started) {
                spectrogram.setListening(true)
                spectrogram.log("Listening: Goertzel decoder @ 1600/2400 Hz, hunting 0xAA7E...")
            } else {
                spectrogram.setListening(false)
                spectrogram.log("Microphone unavailable — grant RECORD_AUDIO permission and try again (not during a call).")
                _statusLine.value = "Microphone unavailable (permission, active call, or device busy)."
            }
        }
    }

    private val bootMark = TimeSource.Monotonic.markNow()
    private var lastPayloadHex: String = ""
    private var lastPayloadAtMs: Long = -10000L

    private fun onAcousticPayload(payload: ByteArray) {
        // Same burst can arrive twice (direct feed + live mic). Ignore exact
        // duplicates for 6 s so replay protection doesn't flip VERIFIED to FAILED.
        val hex = payload.toHexString()
        val nowMs = bootMark.elapsedNow().inWholeMilliseconds
        if (hex == lastPayloadHex && nowMs - lastPayloadAtMs < 6000L) return
        lastPayloadHex = hex
        lastPayloadAtMs = nowMs
        val start = TimeSource.Monotonic.markNow()
        val contact = HandshakeProtocol.verifyAcousticPayload(payload, vault.myPhone(), vault.contacts.value, nowSeconds())
        spectrogram.onFrameDecoded(payload, contact?.displayName)
        if (contact != null) {
            val latency = start.elapsedNow().inWholeMilliseconds
            // Preserve real-call origin: overwriting a screened call session as
            // "simulated" would disable hang-up auto-clear in MainActivity.
            val simulated = if (CallSecuritySession.sessionActive.value) {
                CallSecuritySession.session.value.isSimulated
            } else {
                true
            }
            CallSecuritySession.beginSession(
                contact.phoneNumber, contact.displayName, simulated = simulated,
                status = VerificationStatus.VERIFIED_PAIRED_DEVICE
            )
            CallSecuritySession.setStatus(
                VerificationStatus.VERIFIED_PAIRED_DEVICE,
                "Acoustic HMAC verified for ${contact.displayName} (paired-device possession)",
                latency
            )
        } else if (CallSecuritySession.sessionActive.value) {
            CallSecuritySession.setStatus(
                VerificationStatus.FAILED_SPOOF_DETECTED,
                "Acoustic frame failed HMAC verification — possible spoof"
            )
        }
    }

    /** Full ECDSA StrongBox/TEE challenge-response via the loopback relay simulation. */
    fun runOobHandshake(contact: PairedContact) {
        // The loopback signs with THIS device's key, so it can only verify
        // against a contact whose public key IS this device's key (demo peer).
        // Running it for a real peer would always fail verification and raise
        // a false SPOOF alarm — refuse and explain instead.
        val ownKey = _publicKeyBase64.value
        if (ownKey.isEmpty() || contact.publicKeyBase64 != ownKey) {
            _statusLine.value = "OOB loopback needs the Demo Peer (self-key). For ${contact.displayName}, " +
                "the peer device must answer via the relay — use the acoustic path for on-stage verification."
            spectrogram.log("OOB skipped: ${contact.displayName} is a real peer; loopback cannot sign as them.")
            return
        }
        scope.launch {
            val simulated = if (CallSecuritySession.sessionActive.value) {
                CallSecuritySession.session.value.isSimulated
            } else {
                true
            }
            CallSecuritySession.beginSession(
                contact.phoneNumber, contact.displayName, simulated = simulated,
                status = VerificationStatus.VERIFYING_HANDSHAKE
            )
            _statusLine.value = "OOB challenge sent to ${contact.displayName}..."
            val (verified, latency) = oobClient.runLoopbackHandshake(signer, contact, vault.myPhone(), nowSeconds())
            if (verified) {
                CallSecuritySession.setStatus(
                    VerificationStatus.VERIFIED_HARDWARE,
                    "ECDSA P-256 signature verified in ${signer.hardwareLabel()}",
                    latency
                )
                _statusLine.value = "OOB hardware handshake OK (${latency} ms)"
            } else {
                CallSecuritySession.setStatus(
                    VerificationStatus.FAILED_SPOOF_DETECTED,
                    "OOB ECDSA verification failed — no valid hardware signature"
                )
                _statusLine.value = "OOB hardware handshake FAILED"
            }
        }
    }

    /** Stage trigger: paired name, no valid signature -> spoof + UPI lockdown armed. */
    fun simulateDeepfakeAttack(contact: PairedContact?) {
        val name = contact?.displayName ?: "Unknown Caller"
        val phone = contact?.phoneNumber ?: "910000000000"
        CallSecuritySession.beginSession(phone, name, simulated = true, status = VerificationStatus.VERIFYING_HANDSHAKE)
        scope.launch {
            kotlinx.coroutines.delay(900)
            CallSecuritySession.setStatus(
                VerificationStatus.FAILED_SPOOF_DETECTED,
                "Caller claims to be $name but produced NO hardware signature — deepfake/coercion risk"
            )
            spectrogram.log("SPOOF: challenge to $name timed out with no valid ECDSA/HMAC response.")
        }
    }

    fun simulateUnpairedCaller() {
        CallSecuritySession.beginSession("917777777777", "Unpaired Caller", simulated = true, status = VerificationStatus.UNPAIRED_CALLER_LOCKDOWN)
        CallSecuritySession.setStatus(
            VerificationStatus.UNPAIRED_CALLER_LOCKDOWN,
            "Caller is not in the paired vault — payment lockdown armed"
        )
    }

    fun endSession() {
        CallSecuritySession.endSession()
        _statusLine.value = "Session ended. Status reset."
    }

    /** Creates a local demo peer (second identity) so one-device stage demos verify green. */
    fun createDemoPeer(): PairedContact {
        val secret = secureRandomBytes(32).toHexString()
        val peer = PairedContact(
            phoneNumber = normalizePhone("919876543210"),
            displayName = "Demo Peer (Co-Founder)",
            publicKeyBase64 = _publicKeyBase64.value,
            sharedSecretHex = secret,
            isHardwareBacked = _strongBox.value
        )
        vault.add(peer)
        _statusLine.value = "Demo peer created with fresh 256-bit shared secret."
        return peer
    }

    fun addContact(name: String, phone: String, publicKeyBase64: String, sharedSecretHex: String) {
        vault.add(
            PairedContact(
                phoneNumber = normalizePhone(phone),
                displayName = name.ifBlank { "Contact" },
                publicKeyBase64 = publicKeyBase64.trim(),
                sharedSecretHex = sharedSecretHex.trim().lowercase(),
                isHardwareBacked = false
            )
        )
        _statusLine.value = "Paired with $name."
    }

    fun generateSharedSecretHex(): String = secureRandomBytes(32).toHexString()

    fun sharedSecretValid(hex: String): Boolean = try {
        hex.hexToBytes().size == 32
    } catch (e: Exception) {
        false
    }
}
