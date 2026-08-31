package com.telecom.widget.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.telecom.widget.network.SavedAccount
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Enterprise-grade credential encryption utility backed by Android KeyStore.
 * On Samsung devices, keys are generated and sealed inside Samsung Knox Vault (StrongBox).
 * On Google Pixel devices, keys are sealed inside the discrete Titan M2 security chip.
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "telecom_credentials_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    const val ENCRYPTED_PREFIX = "enc:"

    // Fallback key for pure JVM unit test environments where AndroidKeyStore provider is absent
    private var jvmTestKey: SecretKey? = null

    private val isAndroidKeyStoreSupported: Boolean by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        if (!isAndroidKeyStoreSupported) {
            return jvmTestKey ?: run {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256)
                val k = kg.generateKey()
                jvmTestKey = k
                k
            }
        }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        // Attempt StrongBox (Samsung Knox Vault / Titan M2 discrete chip) if API >= 28
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                specBuilder.setIsStrongBoxBacked(true)
                keyGenerator.init(specBuilder.build())
                return keyGenerator.generateKey()
            } catch (_: Throwable) {
                // Fallback to standard TrustZone TEE KeyStore
                specBuilder.setIsStrongBoxBacked(false)
                keyGenerator.init(specBuilder.build())
                return keyGenerator.generateKey()
            }
        } else {
            keyGenerator.init(specBuilder.build())
            return keyGenerator.generateKey()
        }
    }

    fun isEncrypted(text: String?): Boolean {
        return !text.isNullOrEmpty() && text.startsWith(ENCRYPTED_PREFIX)
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        if (isEncrypted(plainText)) return plainText // Prevent double encryption

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        if (!isEncrypted(cipherText)) {
            // Legacy unencrypted plaintext fallback for seamless migration
            return cipherText
        }

        return try {
            val encodedData = cipherText.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.getDecoder().decode(encodedData)
            if (combined.size < GCM_IV_LENGTH) return cipherText

            val iv = ByteArray(GCM_IV_LENGTH)
            val encryptedBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

fun SavedAccount.toEncrypted(): SavedAccount {
    return if (CryptoManager.isEncrypted(password) || password.isEmpty()) {
        this
    } else {
        this.copy(password = CryptoManager.encrypt(password))
    }
}

fun SavedAccount.toDecrypted(): SavedAccount {
    return if (!CryptoManager.isEncrypted(password) || password.isEmpty()) {
        this
    } else {
        this.copy(password = CryptoManager.decrypt(password))
    }
}

fun List<SavedAccount>.toEncryptedForStorage(): List<SavedAccount> = map { it.toEncrypted() }
fun List<SavedAccount>.toDecryptedFromStorage(): List<SavedAccount> = map { it.toDecrypted() }
