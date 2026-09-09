package com.example.voicelock.speakerid.storage

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Encrypts the complete profile registry as one authenticated local document. */
class SpeakerProfileRegistrySerializer(private val cryptoManager: CryptoManager) : Serializer<SpeakerProfileRegistry> {
    override val defaultValue = SpeakerProfileRegistry()

    override suspend fun readFrom(input: InputStream): SpeakerProfileRegistry {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return defaultValue
        return runCatching {
            Json.decodeFromString<SpeakerProfileRegistry>(cryptoManager.decrypt(encrypted).decodeToString())
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: SpeakerProfileRegistry, output: OutputStream) {
        val encrypted = cryptoManager.encrypt(Json.encodeToString(t).encodeToByteArray())
        withContext(Dispatchers.IO) { output.write(encrypted) }
    }
}
