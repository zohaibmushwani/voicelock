package com.example.voicelock.speakerid.storage

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.voicelock.speakerid.auth.VoiceTemplateStore
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import androidx.core.content.edit

/**
 * Securely stores speaker embeddings (templates) using Jetpack DataStore and Google Tink.
 * Includes a migration path from the deprecated EncryptedSharedPreferences.
 */
class SpeakerTemplateStore(private val context: Context) : VoiceTemplateStore {

    private val cryptoManager = CryptoManager(context)

    private val dataStore: DataStore<SpeakerTemplate> = DataStoreFactory.create(
        serializer = SpeakerTemplateSerializer(cryptoManager),
        produceFile = { context.dataStoreFile("speaker_template.json") },
        migrations = listOf(
            SharedPreferencesMigration(context)
        )
    )

    override suspend fun read(): FloatArray? {
        val template = dataStore.data.first()
        // If the store is empty or version is wrong, return null
        if (template.version != CURRENT_TEMPLATE_VERSION || template.embedding.isEmpty()) {
            return null
        }
        return template.embedding
    }

    override suspend fun write(template: FloatArray) {
        dataStore.updateData {
            SpeakerTemplate(template, CURRENT_TEMPLATE_VERSION)
        }
    }

    override suspend fun clear() {
        dataStore.updateData {
            SpeakerTemplate(FloatArray(0), 0)
        }
    }

    /**
     * Migration logic from the deprecated EncryptedSharedPreferences to DataStore.
     */
    private class SharedPreferencesMigration(private val context: Context) : DataMigration<SpeakerTemplate> {
        
        override suspend fun shouldMigrate(currentData: SpeakerTemplate): Boolean {
            // Only migrate if DataStore is at default/empty state and old prefs exist
            val prefsFile = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            return currentData.version == 0 && prefsFile.all.isNotEmpty()
        }

        override suspend fun migrate(currentData: SpeakerTemplate): SpeakerTemplate {
            return try {
                @Suppress("DEPRECATION")
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                @Suppress("DEPRECATION")
                val sharedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                val base64String = sharedPrefs.getString(KEY_TEMPLATE, null)
                val version = sharedPrefs.getInt(KEY_TEMPLATE_VERSION, 0)

                if (base64String != null && version == CURRENT_TEMPLATE_VERSION) {
                    val bytes = Base64.decode(base64String, Base64.DEFAULT)
                    val buffer = ByteBuffer.wrap(bytes)
                    val embedding = FloatArray(bytes.size / 4) { buffer.float }
                    SpeakerTemplate(embedding, version)
                } else {
                    currentData
                }
            } catch (e: Exception) {
                currentData
            }
        }

        override suspend fun cleanUp() {
            // Clear old preferences after successful migration
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                .edit {
                    clear()
                }
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "speaker_template_prefs"
        private const val KEY_TEMPLATE = "default_speaker_template"
        private const val KEY_TEMPLATE_VERSION = "default_speaker_template_version"
        private const val CURRENT_TEMPLATE_VERSION = 2
    }
}
