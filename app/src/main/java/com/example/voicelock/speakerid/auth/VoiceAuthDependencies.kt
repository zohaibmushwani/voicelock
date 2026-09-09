package com.example.voicelock.speakerid.auth

import com.example.voicelock.speakerid.storage.SpeakerProfile

/** Implemented by the audio/VAD and embedding tasks. It must return an L2-normalized vector. */
interface UtteranceEmbeddingProvider : AutoCloseable {
    suspend fun createEmbedding(utterance: FloatArray): EmbeddingExtractionResult
    override fun close() {}
}

sealed interface EmbeddingExtractionResult {
    data class Success(val embedding: FloatArray) : EmbeddingExtractionResult
    data class Rejected(val reason: VoiceAuthFailure = VoiceAuthFailure.AUDIO_REJECTED) : EmbeddingExtractionResult
}

/** Keystore-backed multi-profile storage used by verification and identification. */
interface SpeakerProfileRepository {
    suspend fun profiles(): List<SpeakerProfile>
    suspend fun profile(id: String): SpeakerProfile?
    suspend fun create(displayName: String, template: FloatArray): SpeakerProfile
    suspend fun rename(id: String, displayName: String): SpeakerProfile
    suspend fun delete(id: String): Boolean
}
