package com.leon.be_nobat.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.leon.be_nobat.domain.interfaces.ICryptoManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager: ICryptoManager {
    private const val KEY_ALIAS = "my_token_key"

    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val GCM_IV_SIZE_BYTES = 12

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        return createKey()
    }

    private fun createKey(): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }
        return keyGenerator.generateKey()
    }

    override fun encrypt(threshold: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(threshold.toByteArray())
        return Base64.encodeToString(iv + encrypted, Base64.DEFAULT)
    }

    override fun decrypt(encryptedData: String): String {
        val data = Base64.decode(encryptedData, Base64.DEFAULT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        require(data.size > GCM_IV_SIZE_BYTES) { "Invalid encrypted value" }
        val iv = data.sliceArray(0 until GCM_IV_SIZE_BYTES)
        val encrypted = data.sliceArray(GCM_IV_SIZE_BYTES until data.size)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }
}
