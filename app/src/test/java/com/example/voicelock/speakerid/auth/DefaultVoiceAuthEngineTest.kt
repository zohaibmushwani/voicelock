package com.example.voicelock.speakerid.auth

import com.example.voicelock.speakerid.storage.SpeakerProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DefaultVoiceAuthEngineTest {
    @Test
    fun enrollsNamedProfileAndVerifiesSelectedProfile() = runTest {
        val store = InMemoryProfiles()
        val engine = DefaultVoiceAuthEngine(EchoProvider, store)

        val enrolled = engine.enroll("Owner", List(3) { shortArrayOf(300, 400) })
        assertTrue(enrolled is EnrollmentResult.Success)
        enrolled as EnrollmentResult.Success

        val verification = engine.verify(enrolled.profileId, shortArrayOf(600, 800))
        assertTrue(verification is VerificationResult.Accepted)
        assertEquals("Owner", store.profile(enrolled.profileId)?.displayName)
    }

    @Test
    fun enrollmentRequiresThreeSamples() = runTest {
        val engine = DefaultVoiceAuthEngine(EchoProvider, InMemoryProfiles())
        assertEquals(
            EnrollmentResult.Rejected(VoiceAuthFailure.INVALID_UTTERANCE_COUNT),
            engine.enroll("Owner", List(2) { shortArrayOf(1, 0) }),
        )
    }

    @Test
    fun identifyReturnsHighestProfileAboveThreshold() = runTest {
        val store = InMemoryProfiles(
            SpeakerProfile("a", "Ada", floatArrayOf(1f, 0f), 1),
            SpeakerProfile("b", "Ben", floatArrayOf(0f, 1f), 2),
        )
        val engine = DefaultVoiceAuthEngine(FixedProvider(floatArrayOf(0.1f, 0.99f)), store)

        val result = engine.identify(shortArrayOf(1))

        assertTrue(result is IdentificationResult.Identified)
        assertEquals("b", (result as IdentificationResult.Identified).profileId)
    }

    @Test
    fun identifyReturnsUnknownBelowThreshold() = runTest {
        val store = InMemoryProfiles(SpeakerProfile("a", "Ada", floatArrayOf(1f, 0f), 1))
        val engine = DefaultVoiceAuthEngine(FixedProvider(floatArrayOf(0.5f, sqrt(0.75f))), store)

        val result = engine.identify(shortArrayOf(1))

        assertEquals(IdentificationResult.Unknown(0.5f), result)
    }

    @Test
    fun identifyWithoutProfilesIsRejected() = runTest {
        val result = DefaultVoiceAuthEngine(FixedProvider(floatArrayOf(1f, 0f)), InMemoryProfiles()).identify(shortArrayOf(1))
        assertEquals(IdentificationResult.Rejected(VoiceAuthFailure.NOT_ENROLLED), result)
    }

    @Test
    fun tiesUseStableProfileIdOrdering() = runTest {
        val store = InMemoryProfiles(
            SpeakerProfile("a", "Ada", floatArrayOf(1f, 0f), 1),
            SpeakerProfile("b", "Ben", floatArrayOf(1f, 0f), 2),
        )
        val result = DefaultVoiceAuthEngine(FixedProvider(floatArrayOf(1f, 0f)), store).identify(shortArrayOf(1))
        assertEquals(IdentificationResult.Identified("b", 1f), result)
    }

    @Test
    fun defaultThresholdAcceptsAboveAndRejectsBelowBoundary() = runTest {
        suspend fun identifyAt(score: Float): IdentificationResult {
            val provider = FixedProvider(floatArrayOf(score, sqrt(1f - score * score)))
            return DefaultVoiceAuthEngine(provider, InMemoryProfiles(SpeakerProfile("a", "Ada", floatArrayOf(1f, 0f), 1))).identify(shortArrayOf(1))
        }
        assertTrue(identifyAt(0.71f) is IdentificationResult.Identified)
        assertTrue(identifyAt(0.69f) is IdentificationResult.Unknown)
    }

    private object EchoProvider : UtteranceEmbeddingProvider {
        override suspend fun createEmbedding(utterance: FloatArray) = EmbeddingExtractionResult.Success(utterance)
    }

    private class FixedProvider(private val embedding: FloatArray) : UtteranceEmbeddingProvider {
        override suspend fun createEmbedding(utterance: FloatArray) = EmbeddingExtractionResult.Success(embedding)
    }

    private class InMemoryProfiles(vararg initial: SpeakerProfile) : SpeakerProfileRepository {
        private val profiles = initial.toMutableList()

        override suspend fun profiles(): List<SpeakerProfile> = profiles.toList()
        override suspend fun profile(id: String): SpeakerProfile? = profiles.firstOrNull { it.id == id }
        override suspend fun create(displayName: String, template: FloatArray): SpeakerProfile {
            val profile = SpeakerProfile("profile-${profiles.size + 1}", displayName, template, profiles.size.toLong())
            profiles += profile
            return profile
        }
        override suspend fun rename(id: String, displayName: String): SpeakerProfile {
            val index = profiles.indexOfFirst { it.id == id }
            require(index >= 0)
            return profiles[index].copy(displayName = displayName).also { profiles[index] = it }
        }
        override suspend fun delete(id: String): Boolean = profiles.removeAll { it.id == id }
    }
}
