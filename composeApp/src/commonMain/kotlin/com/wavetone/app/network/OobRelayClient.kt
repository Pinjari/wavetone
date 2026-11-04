package com.wavetone.app.network

import com.wavetone.app.crypto.HandshakeProtocol
import com.wavetone.app.crypto.HardwareSigner
import com.wavetone.app.crypto.secureRandomBytes
import com.wavetone.app.crypto.toHexString
import com.wavetone.app.domain.OobHandshakePacket
import com.wavetone.app.domain.PairedContact
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/**
 * Out-of-band relay client.
 *
 * Production path: both devices keep a Ktor WebSocket open to a relay; a
 * CHALLENGE (caller, receiver, window, nonce) is answered with a RESPONSE
 * carrying a full hardware ECDSA signature over [HandshakeProtocol.buildOobPayload].
 *
 * Stage path: [runLoopbackHandshake] performs the identical sign/verify
 * locally (real StrongBox/TEE signing, real verification) with simulated
 * relay latency so the demo works with zero network.
 */
class OobRelayClient {

    private val json = Json { ignoreUnknownKeys = true }
    private var client: HttpClient? = null
    private var session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null

    suspend fun connect(relayUrl: String) {
        disconnect()
        val httpClient = HttpClient { install(WebSockets) }
        client = httpClient
        session = httpClient.webSocketSession(relayUrl)
    }

    suspend fun sendChallenge(packet: OobHandshakePacket) {
        session?.send(json.encodeToString(OobHandshakePacket.serializer(), packet))
    }

    suspend fun receivePacket(): OobHandshakePacket? {
        val frame = session?.incoming?.receive() as? Frame.Text ?: return null
        return try {
            json.decodeFromString(OobHandshakePacket.serializer(), frame.readText())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun disconnect() {
        try {
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        } catch (e: Exception) {
            // Session already closed; nothing to do.
        }
        session = null
        client?.close()
        client = null
    }

    /**
     * Real ECDSA challenge-response executed locally as both parties.
     * [signer] signs as this device; verification runs against [contact]'s key.
     * In the two-device relay flow the same verify call runs on the peer's packet.
     * Returns (verified, latencyMs).
     */
    suspend fun runLoopbackHandshake(
        signer: HardwareSigner,
        contact: PairedContact,
        myPhone: String,
        nowEpochSeconds: Long,
        simulatedLatencyMs: Long = 80L
    ): Pair<Boolean, Long> {
        val start = TimeSource.Monotonic.markNow()
        val nonceHex = secureRandomBytes(16).toHexString()
        val window = HandshakeProtocol.currentWindow(nowEpochSeconds)
        val payload = HandshakeProtocol.buildOobPayload(contact.phoneNumber, myPhone, window, nonceHex)
        val signature = try {
            signer.signPayload(payload)
        } catch (e: Exception) {
            return false to start.elapsedNow().inWholeMilliseconds
        }
        delay(simulatedLatencyMs)
        val packet = HandshakeProtocol.buildOobResponse(signer, contact.phoneNumber, myPhone, window, nonceHex, signature)
        val verified = HandshakeProtocol.verifyOobResponse(signer, packet, contact, nowEpochSeconds)
        return verified to start.elapsedNow().inWholeMilliseconds
    }

    companion object {
        /** Point this at your deployed relay (wss://...) for the two-device OOB flow. */
        const val DEFAULT_RELAY_URL = "wss://relay.wavetone.example/handshake"
    }
}
