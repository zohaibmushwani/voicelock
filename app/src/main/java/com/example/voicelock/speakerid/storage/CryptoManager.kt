package com.example.voicelock.speakerid.storage

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Handles encryption and decryption using Google Tink.
 * Uses a Keystore-backed master key to secure the keyset stored in SharedPreferences.
 */
class CryptoManager(context: Context) {

    init {
        AeadConfig.register()
    }

    private val aead: Aead by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    /**
     * Encrypts the provided data using AES-GCM.
     */
    fun encrypt(data: ByteArray): ByteArray {
        return aead.encrypt(data, null)
    }

    /**
     * Decrypts the provided data using AES-GCM.
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        return aead.decrypt(encryptedData, null)
    }

    companion object {
        private const val KEYSET_NAME = "speaker_keyset"
        private const val PREF_FILE_NAME = "speaker_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://speaker_master_key"
    }
}
