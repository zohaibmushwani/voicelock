package com.example.voicelock.speakerid.storage

import kotlinx.serialization.Serializable

/** A named, local-only speaker template. The embedding is encrypted before it reaches disk. */
@Serializable
data class SpeakerProfile(
    val id: String,
    val displayName: String,
    val embedding: FloatArray,
    val createdAtEpochMillis: Long,
    val version: Int = CURRENT_PROFILE_VERSION,
) {
    companion object {
        const val CURRENT_PROFILE_VERSION = 1
    }
}

@Serializable
data class SpeakerProfileRegistry(
    val profiles: List<SpeakerProfile> = emptyList(),
    val version: Int = CURRENT_REGISTRY_VERSION,
) {
    companion object {
        const val CURRENT_REGISTRY_VERSION = 1
    }
}

sealed class ProfileStoreException(message: String) : IllegalStateException(message) {
    data object InvalidName : ProfileStoreException("Enter a name between 1 and 32 characters")
    data object DuplicateName : ProfileStoreException("That profile name already exists")
    data object CapacityReached : ProfileStoreException("This experiment supports up to 10 profiles")
    data object ProfileNotFound : ProfileStoreException("That profile is no longer available")
}
