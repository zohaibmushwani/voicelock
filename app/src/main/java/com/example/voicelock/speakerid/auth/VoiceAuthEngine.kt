package com.example.voicelock.speakerid.auth

import kotlinx.coroutines.flow.StateFlow

/** Coordinates enrollment and verification without exposing biometric templates to callers. */
interface VoiceAuthEngine : AutoCloseable {
    val state: StateFlow<VoiceAuthState>

    /** Enrolls from three to five independent utterances. */
    suspend fun enroll(displayName: String, utterances: List<ShortArray>): EnrollmentResult

    /** Verifies one live 16 kHz PCM utterance against the enrolled template. */
    suspend fun verify(profileId: String, utterance: ShortArray): VerificationResult

    /** Identifies a speaker from the locally enrolled closed set. */
    suspend fun identify(utterance: ShortArray): IdentificationResult

    fun isEnrolled(): Boolean

    fun deleteProfile(profileId: String)
}

sealed interface VoiceAuthState {
    data object Unenrolled : VoiceAuthState
    data object Enrolling : VoiceAuthState
    data object Ready : VoiceAuthState
    data object Verifying : VoiceAuthState
    data class Failed(val reason: VoiceAuthFailure) : VoiceAuthState
}

sealed interface EnrollmentResult {
    data class Success(val profileId: String, val displayName: String, val utteranceCount: Int) : EnrollmentResult
    data class Rejected(val reason: VoiceAuthFailure) : EnrollmentResult
}

sealed interface VerificationResult {
    data class Accepted(val similarity: Float) : VerificationResult
    data class Rejected(val similarity: Float?, val reason: VoiceAuthFailure) : VerificationResult
}

sealed interface IdentificationResult {
    data class Identified(val profileId: String, val similarity: Float) : IdentificationResult
    data class Unknown(val bestSimilarity: Float?) : IdentificationResult
    data class Rejected(val reason: VoiceAuthFailure) : IdentificationResult
}

enum class VoiceAuthFailure {
    NOT_ENROLLED,
    INVALID_UTTERANCE_COUNT,
    AUDIO_REJECTED,
    NO_SPEECH,
    SPEECH_TOO_SHORT,
    EMBEDDING_INVALID,
    STORAGE_FAILURE,
    ENGINE_CLOSED,
}
