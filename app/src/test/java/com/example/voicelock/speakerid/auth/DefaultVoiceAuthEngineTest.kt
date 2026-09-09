package com.example.voicelock.speakerid.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DefaultVoiceAuthEngineTest {
    @Test
    fun enrollAveragesEmbeddingsAndVerifyAcceptsMatchingVoice() = runTest {
        val store = InMemoryStore()
        val provider = object : UtteranceEmbeddingProvider {
            override suspend fun createEmbedding(utterance: FloatArray) = EmbeddingExtractionResult.Success(utterance)
        }
        val engine = DefaultVoiceAuthEngine(provider, store)

        val enrollment = engine.enroll(List(3) { shortArrayOf(300, 400) })
        val verification = engine.verify(shortArrayOf(600, 800))

        assertTrue(enrollment is EnrollmentResult.Success)
        assertTrue(verification is VerificationResult.Accepted)
        assertEquals(0.6f, store.template!![0], 0.0001f)
        assertEquals(0.8f, store.template!![1], 0.0001f)
    }

    @Test
    fun enrollmentRequiresAtLeastThreeUtterances() = runTest {
        val provider = object : UtteranceEmbeddingProvider {
            override suspend fun createEmbedding(utterance: FloatArray) = EmbeddingExtractionResult.Success(utterance)
        }
        val engine = DefaultVoiceAuthEngine(provider, InMemoryStore())

        val result = engine.enroll(List(2) { shortArrayOf(1, 0) })

        assertEquals(EnrollmentResult.Rejected(VoiceAuthFailure.INVALID_UTTERANCE_COUNT), result)
    }

    @Test
    fun verificationRejectsBelowThresholdAndKeepsScore() = runTest {
        val store = InMemoryStore(floatArrayOf(1f, 0f))
        val provider = object : UtteranceEmbeddingProvider {
            override suspend fun createEmbedding(utterance: FloatArray) = EmbeddingExtractionResult.Success(utterance)
        }
        val engine = DefaultVoiceAuthEngine(provider, store)

        val result = engine.verify(shortArrayOf(0, 32767))

        assertEquals(VerificationResult.Rejected(0f, VoiceAuthFailure.AUDIO_REJECTED), result)
    }

    @Test
    fun calibratedDefaultThresholdAcceptsAboveAndRejectsBelowBoundary() = runTest {
        suspend fun verifyAt(score: Float): VerificationResult {
            val provider = object : UtteranceEmbeddingProvider {
                override suspend fun createEmbedding(utterance: FloatArray) =
                    EmbeddingExtractionResult.Success(
                        floatArrayOf(score, sqrt(1f - score * score)),
                    )
            }
            return DefaultVoiceAuthEngine(provider, InMemoryStore(floatArrayOf(1f, 0f)))
                .verify(shortArrayOf(1))
        }

        assertTrue(verifyAt(0.71f) is VerificationResult.Accepted)
        assertTrue(verifyAt(0.69f) is VerificationResult.Rejected)
    }

    private class InMemoryStore(var template: FloatArray? = null) : VoiceTemplateStore {
        override suspend fun read(): FloatArray? = template
        override suspend fun write(template: FloatArray) { this.template = template.copyOf() }
        override suspend fun clear() { template = null }
    }
}
