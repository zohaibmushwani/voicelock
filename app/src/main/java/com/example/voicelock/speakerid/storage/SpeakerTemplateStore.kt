package com.example.voicelock.speakerid.storage

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.voicelock.speakerid.auth.VoiceTemplateStore
import java.nio.ByteBuffer
import androidx.core.content.edit

/**
 * Securely stores speaker embeddings (templates) using Android Keystore and EncryptedSharedPreferences.
 * Ensures that embeddings are encrypted at rest and never leave the device.
 */
class SpeakerTemplateStore(context: Context) : VoiceTemplateStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "speaker_template_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun read(): FloatArray? {
        if (sharedPrefs.getInt(KEY_TEMPLATE_VERSION, 0) != CURRENT_TEMPLATE_VERSION) {
            sharedPrefs.edit { remove(KEY_TEMPLATE).remove(KEY_TEMPLATE_VERSION) }
            return null
        }
        val base64String = sharedPrefs.getString(KEY_TEMPLATE, null) ?: return null
        return try {
            val bytes = Base64.decode(base64String, Base64.DEFAULT)
            val buffer = ByteBuffer.wrap(bytes)
            FloatArray(bytes.size / 4) { buffer.float }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun write(template: FloatArray) {
        val buffer = ByteBuffer.allocate(template.size * 4)
        template.forEach { buffer.putFloat(it) }
        val base64String = Base64.encodeToString(buffer.array(), Base64.DEFAULT)
        sharedPrefs.edit {
            putString(KEY_TEMPLATE, base64String)
                .putInt(KEY_TEMPLATE_VERSION, CURRENT_TEMPLATE_VERSION)
        }
    }

    override suspend fun clear() {
        sharedPrefs.edit { remove(KEY_TEMPLATE).remove(KEY_TEMPLATE_VERSION) }
    }

    companion object {
        private const val KEY_TEMPLATE = "default_speaker_template"
        private const val KEY_TEMPLATE_VERSION = "default_speaker_template_version"
        private const val CURRENT_TEMPLATE_VERSION = 2
    }
}
