package com.example.voicelock.speakerid.auth

import kotlinx.coroutines.flow.StateFlow

/** Coordinates enrollment and verification without exposing biometric templates to callers. */
interface VoiceAuthEngine : AutoCloseable {
    val state: StateFlow<VoiceAuthState>

    /** Enrolls from three to five independent utterances. */
    suspend fun enroll(utterances: List<ShortArray>): EnrollmentResult

    /** Verifies one live 16 kHz PCM utterance against the enrolled template. */
    suspend fun verify(utterance: ShortArray): VerificationResult

    fun isEnrolled(): Boolean

    fun clearEnrollment()
}

sealed interface VoiceAuthState {
    data object Unenrolled : VoiceAuthState
    data object Enrolling : VoiceAuthState
    data object Ready : VoiceAuthState
    data object Verifying : VoiceAuthState
    data class Failed(val reason: VoiceAuthFailure) : VoiceAuthState
}

sealed interface EnrollmentResult {
    data class Success(val utteranceCount: Int) : EnrollmentResult
    data class Rejected(val reason: VoiceAuthFailure) : EnrollmentResult
}

sealed interface VerificationResult {
    data class Accepted(val similarity: Float) : VerificationResult
    data class Rejected(val similarity: Float?, val reason: VoiceAuthFailure) : VerificationResult
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
