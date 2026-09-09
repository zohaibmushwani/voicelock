package com.example.voicelock.speakerid.auth

import com.example.voicelock.speakerid.VoiceLockLog
import com.example.voicelock.speakerid.storage.SpeakerProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Security-first orchestration. A false reject is preferred to accepting malformed,
 * non-normalized, or inconsistent embedding data.
 */
class DefaultVoiceAuthEngine(
    private val embeddingProvider: UtteranceEmbeddingProvider,
    private val profileStore: SpeakerProfileRepository,
    private val similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VoiceAuthEngine, AutoCloseable {
    private val scope = CoroutineScope(workerDispatcher + SupervisorJob())
    private val mutableState = MutableStateFlow<VoiceAuthState>(VoiceAuthState.Unenrolled)
    override val state: StateFlow<VoiceAuthState> = mutableState.asStateFlow()
    private var closed = false

    init {
        scope.launch {
            try {
                if (profileStore.profiles().isNotEmpty()) {
                    mutableState.value = VoiceAuthState.Ready
                }
            } catch (_: Exception) {
                // Initial check failed, keep as Unenrolled
            }
        }
    }

    override suspend fun enroll(displayName: String, utterances: List<ShortArray>): EnrollmentResult = withContext(workerDispatcher) {
        VoiceLockLog.info("Enrollment requested with ${utterances.size} samples")
        if (closed) return@withContext rejectedEnrollment(VoiceAuthFailure.ENGINE_CLOSED)
        if (utterances.size !in MIN_ENROLLMENT_UTTERANCES..MAX_ENROLLMENT_UTTERANCES) {
            return@withContext rejectedEnrollment(VoiceAuthFailure.INVALID_UTTERANCE_COUNT)
        }

        mutableState.value = VoiceAuthState.Enrolling
        val embeddings = ArrayList<FloatArray>(utterances.size)
        for ((index, utterance) in utterances.withIndex()) {
            VoiceLockLog.info("Processing enrollment sample ${index + 1}/${utterances.size}")
            val floatUtterance = pcmToFloat(utterance)
            when (val result = embeddingProvider.createEmbedding(floatUtterance)) {
                is EmbeddingExtractionResult.Rejected -> return@withContext rejectedEnrollment(result.reason)
                is EmbeddingExtractionResult.Success -> {
                    val normalized = normalize(result.embedding)
                        ?: return@withContext rejectedEnrollment(VoiceAuthFailure.EMBEDDING_INVALID)
                    embeddings += normalized
                }
            }
        }

        val template = averageAndNormalize(embeddings)
            ?: return@withContext rejectedEnrollment(VoiceAuthFailure.EMBEDDING_INVALID)
        try {
            val profile = profileStore.create(displayName, template)
            mutableState.value = VoiceAuthState.Ready
            VoiceLockLog.info("Enrollment profile encrypted and saved (${template.size} values)")
            EnrollmentResult.Success(profile.id, profile.displayName, embeddings.size)
        } catch (error: Exception) {
            VoiceLockLog.error("Unable to save enrollment template", error)
            rejectedEnrollment(VoiceAuthFailure.STORAGE_FAILURE)
        }
    }

    override suspend fun verify(profileId: String, utterance: ShortArray): VerificationResult = withContext(workerDispatcher) {
        VoiceLockLog.info("Verification requested: ${utterance.size} samples")
        if (closed) return@withContext rejectedVerification(null, VoiceAuthFailure.ENGINE_CLOSED)
        mutableState.value = VoiceAuthState.Verifying
        val profile = try {
            profileStore.profile(profileId)
        } catch (_: Exception) {
            return@withContext rejectedVerification(null, VoiceAuthFailure.STORAGE_FAILURE)
        } ?: return@withContext rejectedVerification(null, VoiceAuthFailure.NOT_ENROLLED)

        val normalizedTemplate = normalize(profile.embedding)
            ?: return@withContext rejectedVerification(null, VoiceAuthFailure.EMBEDDING_INVALID)
        
        val floatUtterance = pcmToFloat(utterance)
        when (val result = embeddingProvider.createEmbedding(floatUtterance)) {
            is EmbeddingExtractionResult.Rejected -> rejectedVerification(null, result.reason)
            is EmbeddingExtractionResult.Success -> {
                val embedding = normalize(result.embedding)
                    ?: return@withContext rejectedVerification(null, VoiceAuthFailure.EMBEDDING_INVALID)
                if (embedding.size != normalizedTemplate.size) {
                    return@withContext rejectedVerification(null, VoiceAuthFailure.EMBEDDING_INVALID)
                }
                val similarity = cosineSimilarity(normalizedTemplate, embedding)
                VoiceLockLog.info("Verification score=${"%.4f".format(similarity)}, threshold=${"%.4f".format(similarityThreshold)}")
                if (similarity >= similarityThreshold) {
                    mutableState.value = VoiceAuthState.Ready
                    VerificationResult.Accepted(similarity)
                } else {
                    rejectedVerification(similarity, VoiceAuthFailure.AUDIO_REJECTED)
                }
            }
        }
    }

    override suspend fun identify(utterance: ShortArray): IdentificationResult = withContext(workerDispatcher) {
        VoiceLockLog.info("Speaker identification requested: ${utterance.size} samples")
        if (closed) return@withContext IdentificationResult.Rejected(VoiceAuthFailure.ENGINE_CLOSED)
        mutableState.value = VoiceAuthState.Verifying
        val profiles = try {
            profileStore.profiles().sortedBy { it.id }
        } catch (_: Exception) {
            return@withContext IdentificationResult.Rejected(VoiceAuthFailure.STORAGE_FAILURE)
        }
        if (profiles.isEmpty()) return@withContext IdentificationResult.Rejected(VoiceAuthFailure.NOT_ENROLLED)
        val embedding = when (val result = embeddingProvider.createEmbedding(pcmToFloat(utterance))) {
            is EmbeddingExtractionResult.Rejected -> return@withContext IdentificationResult.Rejected(result.reason)
            is EmbeddingExtractionResult.Success -> normalize(result.embedding)
                ?: return@withContext IdentificationResult.Rejected(VoiceAuthFailure.EMBEDDING_INVALID)
        }
        val scored = profiles.mapNotNull { profile ->
            normalize(profile.embedding)
                ?.takeIf { it.size == embedding.size }
                ?.let { profile to cosineSimilarity(it, embedding) }
        }
        if (scored.isEmpty()) return@withContext IdentificationResult.Rejected(VoiceAuthFailure.EMBEDDING_INVALID)
        val winner = scored.maxWithOrNull(compareBy<Pair<SpeakerProfile, Float>> { it.second }.thenBy { it.first.id })!!
        VoiceLockLog.info("Identification compared ${scored.size} profiles; best score=${"%.4f".format(winner.second)}")
        mutableState.value = VoiceAuthState.Ready
        if (winner.second >= similarityThreshold) {
            IdentificationResult.Identified(winner.first.id, winner.second)
        } else {
            IdentificationResult.Unknown(winner.second)
        }
    }

    override fun isEnrolled(): Boolean = state.value == VoiceAuthState.Ready

    override fun deleteProfile(profileId: String) {
        if (closed) return
        scope.launch {
            try {
                profileStore.delete(profileId)
                mutableState.value = if (profileStore.profiles().isEmpty()) VoiceAuthState.Unenrolled else VoiceAuthState.Ready
            } catch (_: Exception) {
                mutableState.value = VoiceAuthState.Failed(VoiceAuthFailure.STORAGE_FAILURE)
            }
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
        embeddingProvider.close()
        mutableState.value = VoiceAuthState.Unenrolled
    }

    private fun rejectedEnrollment(reason: VoiceAuthFailure): EnrollmentResult.Rejected {
        VoiceLockLog.warn("Enrollment rejected: $reason")
        mutableState.value = VoiceAuthState.Failed(reason)
        return EnrollmentResult.Rejected(reason)
    }

    private fun rejectedVerification(similarity: Float?, reason: VoiceAuthFailure): VerificationResult.Rejected {
        VoiceLockLog.warn("Verification rejected: $reason${similarity?.let { ", score=${"%.4f".format(it)}" } ?: ""}")
        mutableState.value = if (reason == VoiceAuthFailure.NOT_ENROLLED) VoiceAuthState.Unenrolled else VoiceAuthState.Failed(reason)
        return VerificationResult.Rejected(similarity, reason)
    }

    private fun pcmToFloat(samples: ShortArray): FloatArray {
        return FloatArray(samples.size) { samples[it].toFloat() / 32768.0f }
    }

    private fun averageAndNormalize(embeddings: List<FloatArray>): FloatArray? {
        val size = embeddings.firstOrNull()?.size ?: return null
        if (size == 0 || embeddings.any { it.size != size }) return null
        return FloatArray(size) { index -> embeddings.sumOf { it[index].toDouble() }.toFloat() / embeddings.size }
            .let(::normalize)
    }

    private fun normalize(vector: FloatArray): FloatArray? {
        if (vector.isEmpty() || vector.any { !it.isFinite() }) return null
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() })
        if (!magnitude.isFinite() || magnitude <= MIN_VECTOR_MAGNITUDE) return null
        return FloatArray(vector.size) { index -> (vector[index] / magnitude).toFloat() }
    }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float =
        left.indices.sumOf { (left[it] * right[it]).toDouble() }.toFloat().coerceIn(-1f, 1f)

    companion object {
        /** Conservative device-calibrated start: genuine 0.744–0.843 vs impostor 0.391–0.450. */
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.70f
        private const val MIN_ENROLLMENT_UTTERANCES = 3
        private const val MAX_ENROLLMENT_UTTERANCES = 5
        private const val MIN_VECTOR_MAGNITUDE = 1e-12
    }
}
