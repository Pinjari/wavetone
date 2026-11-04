package com.wavetone.app.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import com.wavetone.app.AndroidContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

actual class HardwareSigner {

    companion object {
        private const val KEY_ALIAS = "wavetone_ecdsa_p256"
    }

    actual suspend fun ensureIdentityKeyGenerated(): Boolean = withContext(Dispatchers.IO) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) return@withContext isStrongBoxBackedKey()
        try {
            generateKeyPair(useStrongBox = deviceHasStrongBox())
            isStrongBoxBackedKey()
        } catch (e: StrongBoxUnavailableException) {
            generateKeyPair(useStrongBox = false)
            false
        } catch (e: Exception) {
            generateKeyPair(useStrongBox = false)
            false
        }
    }

    private fun deviceHasStrongBox(): Boolean {
        val ctx = AndroidContext.appContext ?: return false
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    private fun generateKeyPair(useStrongBox: Boolean) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(useStrongBox)
        kpg.initialize(builder.build())
        kpg.generateKeyPair()
    }

    /** True only if the EXISTING key actually resides in StrongBox (not just device-advertised). */
    private fun isStrongBoxBackedKey(): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return false
            val keyInfo = KeyFactory.getInstance(entry.privateKey.algorithm, "AndroidKeyStore")
                .getKeySpec(entry.privateKey, KeyInfo::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware && deviceHasStrongBox()
            }
        } catch (e: Exception) {
            false
        }
    }

    actual fun hardwareLabel(): String {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(KEY_ALIAS)) return "Not generated yet"
            val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return "Unavailable"
            val keyInfo = KeyFactory.getInstance(entry.privateKey.algorithm, "AndroidKeyStore")
                .getKeySpec(entry.privateKey, KeyInfo::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox (discrete secure element)"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE (Trusted Execution Environment)"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software (no hardware isolation)"
                    else -> "Unknown security level"
                }
            } else if (@Suppress("DEPRECATION") keyInfo.isInsideSecureHardware) {
                "Hardware secure element (pre-S reporting)"
            } else {
                "Software (no hardware isolation)"
            }
        } catch (e: Exception) {
            "Unavailable: ${e.message}"
        }
    }

    actual fun getPublicKeyBase64(): String {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val pubKey = ks.getCertificate(KEY_ALIAS)?.publicKey ?: return ""
        return Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
    }

    actual suspend fun signPayload(data: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        Signature.getInstance("SHA256withECDSA").run {
            initSign(entry.privateKey)
            update(data)
            sign()
        }
    }

    actual fun verifySignature(data: ByteArray, signature: ByteArray, publicKeyBase64: String): Boolean {
        return try {
            if (publicKeyBase64.isBlank()) return false
            val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val pubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(pubKey)
                update(data)
                verify(signature)
            }
        } catch (e: Exception) {
            false
        }
    }

    actual fun isDeviceInActiveCall(): Boolean {
        val ctx = AndroidContext.appContext ?: return false
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audioManager.mode == AudioManager.MODE_RINGTONE
    }
}
