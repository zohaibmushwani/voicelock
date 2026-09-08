package com.example.voicelock.speakerid.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Typed, serializable navigation contract. Never place raw audio or embeddings in a route. */
@Serializable
sealed interface AppDestination : NavKey {
    @Serializable data object Permission : AppDestination
    @Serializable data object Home : AppDestination
    @Serializable data class Enroll(val completedUtterances: Int = 0) : AppDestination
    @Serializable data object Verify : AppDestination
    @Serializable data class Result(
        val type: ResultType,
        val accepted: Boolean? = null,
        val similarityPercent: Int? = null,
    ) : AppDestination
}

@Serializable
enum class ResultType { ENROLLMENT, VERIFICATION }
