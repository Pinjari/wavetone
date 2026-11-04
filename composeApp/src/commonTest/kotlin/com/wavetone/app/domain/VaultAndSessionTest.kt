package com.wavetone.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultAndSessionTest {

    private class FakeStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private val contact = PairedContact(
        phoneNumber = "919876543210",
        displayName = "Peer",
        publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE",
        sharedSecretHex = "ab".repeat(32),
        isHardwareBacked = true
    )

    @Test
    fun vaultPersistsAndReloadsContacts() {
        val store = FakeStore()
        val vault = ContactVault(store)
        vault.add(contact)
        vault.setMyPhone("+91 99999 99999")
        val reloaded = ContactVault(store)
        assertEquals(1, reloaded.contacts.value.size)
        assertEquals(contact, reloaded.contacts.value.first())
        assertEquals("919999999999", reloaded.myPhone())
        assertEquals(contact, reloaded.find("919876543210"))
        assertNull(reloaded.find("910000000000"))
    }

    @Test
    fun vaultReplaceAndRemove() {
        val vault = ContactVault(FakeStore())
        vault.add(contact)
        vault.add(contact.copy(displayName = "Renamed"))
        assertEquals(1, vault.contacts.value.size)
        assertEquals("Renamed", vault.contacts.value.first().displayName)
        vault.remove(contact.phoneNumber)
        assertTrue(vault.contacts.value.isEmpty())
    }

    @Test
    fun lockdownStateMachine() {
        CallSecuritySession.endSession()
        assertFalse(CallSecuritySession.shouldLockdown())

        CallSecuritySession.beginSession("911", "X", simulated = true, status = VerificationStatus.FAILED_SPOOF_DETECTED)
        assertTrue(CallSecuritySession.shouldLockdown())

        CallSecuritySession.overrideLockdown()
        assertFalse(CallSecuritySession.shouldLockdown())

        CallSecuritySession.beginSession("911", "X", simulated = true, status = VerificationStatus.VERIFIED_PAIRED_DEVICE)
        assertFalse(CallSecuritySession.shouldLockdown())

        CallSecuritySession.beginSession("912", "Y", simulated = true, status = VerificationStatus.UNPAIRED_CALLER_LOCKDOWN)
        assertTrue(CallSecuritySession.shouldLockdown())

        CallSecuritySession.endSession()
        assertFalse(CallSecuritySession.shouldLockdown())
        assertEquals(VerificationStatus.IDLE, CallSecuritySession.status.value)
    }
}
