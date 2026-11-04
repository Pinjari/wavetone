package com.wavetone.app.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistenceRobustnessTest {

    private class FakeStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    @Test
    fun corruptVaultJsonLoadsEmptyInsteadOfCrashing() {
        val store = FakeStore()
        store.map[ContactVault.KEY_CONTACTS] = "{not valid json!!!"
        val vault = ContactVault(store)
        assertTrue(vault.contacts.value.isEmpty())
    }

    @Test
    fun oobPacketJsonRoundTrip() {
        val packet = OobHandshakePacket(
            type = "RESPONSE",
            callerPhone = "919876543210",
            receiverPhone = "919999999999",
            timestampWindow = 113333340L,
            nonceHex = "abcd1234",
            signatureBase64 = "MEUCIQD=",
            isInActiveCall = true
        )
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(OobHandshakePacket.serializer(), packet)
        assertEquals(packet, json.decodeFromString(OobHandshakePacket.serializer(), encoded))
    }

    /** Simulated process restart: a fresh store read must restore the lockdown session. */
    @Test
    fun sessionPersistsAndRestores() {
        val store = FakeStore()
        CallSecuritySession.endSession()
        CallSecuritySession.attachStore(store)
        CallSecuritySession.beginSession(
            "919876543210", "Peer", simulated = false,
            status = VerificationStatus.FAILED_SPOOF_DETECTED
        )
        CallSecuritySession.setStatus(VerificationStatus.FAILED_SPOOF_DETECTED, "detail-x", 42L)
        assertTrue(CallSecuritySession.shouldLockdown())

        // "Restart": wipe in-memory flows back to defaults, then re-attach the same store.
        CallSecuritySession.endSession()
        // endSession persisted the cleared state; re-arm and re-restore manually:
        CallSecuritySession.beginSession(
            "919876543210", "Peer", simulated = false,
            status = VerificationStatus.FAILED_SPOOF_DETECTED
        )
        val raw = store.map["wavetone_call_session"] ?: error("session was not persisted")
        assertTrue(raw.contains("FAILED_SPOOF_DETECTED"))

        // Fresh attach after simulated death restores from the same backing map:
        val store2 = FakeStore()
        store2.map.putAll(store.map)
        CallSecuritySession.endSession()
        store.map.putAll(store2.map) // endSession overwrote with IDLE; restore armed snapshot
        store.map["wavetone_call_session"] = raw
        CallSecuritySession.attachStore(store)
        assertEquals(VerificationStatus.FAILED_SPOOF_DETECTED, CallSecuritySession.status.value)
        assertTrue(CallSecuritySession.sessionActive.value)
        assertTrue(CallSecuritySession.shouldLockdown())
        CallSecuritySession.endSession()
    }
}
