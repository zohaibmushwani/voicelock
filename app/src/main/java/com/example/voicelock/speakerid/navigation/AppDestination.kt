package com.example.voicelock.speakerid.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed, serializable navigation contract. Never place raw audio or embeddings in a route. */
@Serializable
sealed interface AppDestination : NavKey {
    @Serializable data object Permission : AppDestination
    @Serializable data object Hub : AppDestination
    @Serializable data object Profiles : AppDestination
    @Serializable data class Enroll(val completedUtterances: Int = 0) : AppDestination
    @Serializable data object ProfilePicker : AppDestination
    @Serializable data class Verify(val profileId: String) : AppDestination
    @Serializable data object Identify : AppDestination
    @Serializable data object Diarization : AppDestination
    @Serializable data class Result(
        val type: ResultType,
        val profileId: String? = null,
        val accepted: Boolean? = null,
        val similarityPercent: Int? = null,
        val unknown: Boolean = false,
    ) : AppDestination
}

@Serializable
enum class ResultType { ENROLLMENT, VERIFICATION, IDENTIFICATION }
