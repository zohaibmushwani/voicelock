package com.example.voicelock.speakerid.auth

/** Implemented by the audio/VAD and embedding tasks. It must return an L2-normalized vector. */
interface UtteranceEmbeddingProvider : AutoCloseable {
    suspend fun createEmbedding(utterance: FloatArray): EmbeddingExtractionResult
    override fun close() {}
}

sealed interface EmbeddingExtractionResult {
    data class Success(val embedding: FloatArray) : EmbeddingExtractionResult
    data class Rejected(val reason: VoiceAuthFailure = VoiceAuthFailure.AUDIO_REJECTED) : EmbeddingExtractionResult
}

/** Implemented by the Keystore-backed template storage task. */
interface VoiceTemplateStore {
    suspend fun read(): FloatArray?
    suspend fun write(template: FloatArray)
    suspend fun clear()
}
