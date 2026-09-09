package com.example.voicelock.speakerid.storage

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializes [SpeakerTemplate] into an encrypted stream using [CryptoManager].
 */
class SpeakerTemplateSerializer(private val cryptoManager: CryptoManager) : Serializer<SpeakerTemplate> {

    override val defaultValue: SpeakerTemplate = SpeakerTemplate(FloatArray(0), 0)

    override suspend fun readFrom(input: InputStream): SpeakerTemplate {
        val encryptedBytes = input.readBytes()
        if (encryptedBytes.isEmpty()) return defaultValue

        return try {
            val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
            Json.decodeFromString<SpeakerTemplate>(decryptedBytes.decodeToString())
        } catch (e: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: SpeakerTemplate, output: OutputStream) {
        val jsonString = Json.encodeToString(t)
        val encryptedBytes = cryptoManager.encrypt(jsonString.encodeToByteArray())
        withContext(Dispatchers.IO) {
            output.write(encryptedBytes)
        }
    }
}
