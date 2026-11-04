package com.wavetone.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wavetone.app.domain.AndroidKeyValueStore
import com.wavetone.app.domain.ContactVault
import com.wavetone.app.domain.PairedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The vault (incl. shared secrets) must be ciphertext at rest and round-trip losslessly. */
@RunWith(AndroidJUnit4::class)
class VaultEncryptionInstrumentedTest {

    private val secret = "0123456789abcdeffedcba987654321000112233445566778899aabbccddeeff"

    @Before
    fun setup() {
        AndroidContext.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun vaultIsEncryptedAtRestAndRoundTrips() {
        val vault = ContactVault(AndroidKeyValueStore())
        val contact = PairedContact(
            phoneNumber = "919111111111",
            displayName = "Vault Test",
            publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE",
            sharedSecretHex = secret,
            isHardwareBacked = true
        )
        vault.add(contact)
        try {
            val raw = InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences(AndroidKeyValueStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ContactVault.KEY_CONTACTS, "") ?: ""
            assertTrue("stored vault must carry the encryption prefix", raw.startsWith("v1:"))
            assertFalse("shared secret must not appear in plaintext at rest", raw.contains(secret))
            assertFalse("contact JSON must not be readable at rest", raw.contains("Vault Test"))

            val reloaded = ContactVault(AndroidKeyValueStore())
            assertEquals(contact, reloaded.find(contact.phoneNumber))
        } finally {
            vault.remove(contact.phoneNumber)
        }
    }
}
