package com.example.voicelock.speakerid.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.example.voicelock.speakerid.auth.SpeakerProfileRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Encrypted, bounded registry for the closed-set speaker experiment.
 */
class SpeakerProfileStore(
    context: Context,
) : SpeakerProfileRepository {
    private val dataStore: DataStore<SpeakerProfileRegistry> = DataStoreFactory.create(
        serializer = SpeakerProfileRegistrySerializer(CryptoManager(context)),
        produceFile = { context.dataStoreFile(FILE_NAME) },
    )

    override suspend fun profiles(): List<SpeakerProfile> {
        return dataStore.data.first().profiles.sortedBy { it.displayName.lowercase() }
    }

    override suspend fun profile(id: String): SpeakerProfile? = profiles().firstOrNull { it.id == id }

    override suspend fun create(displayName: String, template: FloatArray): SpeakerProfile {
        val cleanName = requireValidName(displayName)
        require(template.isNotEmpty()) { "A profile needs a valid embedding" }
        lateinit var created: SpeakerProfile
        dataStore.updateData { registry ->
            validateRegistry(registry)
            requireCapacity(registry.profiles.size)
            requireUniqueName(registry.profiles, cleanName)
            created = SpeakerProfile(
                id = UUID.randomUUID().toString(),
                displayName = cleanName,
                embedding = template.copyOf(),
                createdAtEpochMillis = System.currentTimeMillis(),
            )
            registry.copy(profiles = registry.profiles + created)
        }
        return created
    }

    override suspend fun rename(id: String, displayName: String): SpeakerProfile {
        val cleanName = requireValidName(displayName)
        lateinit var renamed: SpeakerProfile
        dataStore.updateData { registry ->
            validateRegistry(registry)
            val existing = registry.profiles.firstOrNull { it.id == id } ?: throw ProfileStoreException.ProfileNotFound
            requireUniqueName(registry.profiles, cleanName, excludingId = id)
            renamed = existing.copy(displayName = cleanName)
            registry.copy(profiles = registry.profiles.map { if (it.id == id) renamed else it })
        }
        return renamed
    }

    override suspend fun delete(id: String): Boolean {
        var deleted = false
        dataStore.updateData { registry ->
            val remaining = registry.profiles.filterNot { it.id == id }
            deleted = remaining.size != registry.profiles.size
            registry.copy(profiles = remaining)
        }
        return deleted
    }

    private fun validateRegistry(registry: SpeakerProfileRegistry) {
        if (registry.version != SpeakerProfileRegistry.CURRENT_REGISTRY_VERSION) {
            throw IllegalStateException("Unsupported speaker registry version")
        }
    }

    companion object {
        const val MAX_PROFILES = 10
        private const val FILE_NAME = "speaker_profiles.json"
        fun requireValidName(value: String): String {
            val clean = value.trim().replace(Regex("\\s+"), " ")
            if (clean.length !in 1..32) throw ProfileStoreException.InvalidName
            return clean
        }

        fun requireCapacity(existingCount: Int) {
            if (existingCount >= MAX_PROFILES) throw ProfileStoreException.CapacityReached
        }

        fun requireUniqueName(profiles: List<SpeakerProfile>, candidate: String, excludingId: String? = null) {
            if (profiles.any { it.id != excludingId && it.displayName.equals(candidate, ignoreCase = true) }) {
                throw ProfileStoreException.DuplicateName
            }
        }
    }
}
