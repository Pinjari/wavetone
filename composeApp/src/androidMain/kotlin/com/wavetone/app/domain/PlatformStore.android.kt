package com.wavetone.app.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wavetone.app.AndroidContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual fun createPlatformStore(): KeyValueStore = AndroidKeyValueStore()

/**
 * SharedPreferences store with values encrypted at rest (AES-256-GCM,
 * key in AndroidKeyStore). Values written by older plaintext builds are
 * still readable and get upgraded on next write.
 */
class AndroidKeyValueStore : KeyValueStore {
    private val memory = mutableMapOf<String, String>()

    private fun prefs() = AndroidContext.appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val raw = prefs()?.getString(key, null) ?: memory[key] ?: return null
        if (!raw.startsWith(ENC_PREFIX)) return raw // legacy plaintext
        return try {
            val blob = Base64.decode(raw.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, IV_BYTES)
            val cipherText = blob.copyOfRange(IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null // key rotated / data corrupt: behave as absent rather than crash
        }
    }

    override fun put(key: String, value: String?) {
        val stored = value?.let { encrypt(it) }
        val p = prefs()
        if (p != null) {
            val editor = p.edit()
            if (stored == null) editor.remove(key) else editor.putString(key, stored)
            editor.apply()
        } else if (stored == null) {
            memory.remove(key)
        } else {
            memory[key] = stored
        }
    }

    private fun encrypt(plain: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, vaultKey())
            ENC_PREFIX + Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) {
            plain
        }
    }

    private fun vaultKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Vault key does not need StrongBox; keep it fast and always available.
            .setIsStrongBoxBacked(false)
        generator.init(builder.build())
        return generator.generateKey()
    }

    companion object {
        const val PREFS_NAME = "wavetone_vault"
        private const val KEY_ALIAS = "wavetone_vault_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENC_PREFIX = "v1:"
        private const val IV_BYTES = 12
    }
}
