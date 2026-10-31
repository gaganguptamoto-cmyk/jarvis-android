package com.jarvis.assistant.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_secure", Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun saveApiKey(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString(PREF_NAME, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun loadApiKey(): String? {
        val raw = prefs.getString(PREF_NAME, null) ?: return null
        return try {
            val data = Base64.decode(raw, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, 12)
            val encrypted = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasApiKey(): Boolean = loadApiKey() != null

    fun clearApiKey() {
        prefs.edit().remove(PREF_NAME).apply()
    }

    private companion object {
        const val ALIAS = "jarvis_api_key_alias"
        const val PREF_NAME = "gemini_key"
    }
}
