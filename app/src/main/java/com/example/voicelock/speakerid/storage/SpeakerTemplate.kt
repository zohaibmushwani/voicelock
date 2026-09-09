package com.example.voicelock.speakerid.storage

import kotlinx.serialization.Serializable

/**
 * Represents a speaker embedding template with versioning.
 */
@Serializable
data class SpeakerTemplate(
    val embedding: FloatArray,
    val version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SpeakerTemplate
        if (!embedding.contentEquals(other.embedding)) return false
        if (version != other.version) return false
        return true
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + version
        return result
    }
}
