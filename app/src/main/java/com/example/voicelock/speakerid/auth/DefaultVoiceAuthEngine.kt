package com.example.voicelock.speakerid.auth

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
    private val templateStore: VoiceTemplateStore,
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
                if (templateStore.read() != null) {
                    mutableState.value = VoiceAuthState.Ready
                }
            } catch (_: Exception) {
                // Initial check failed, keep as Unenrolled
            }
        }
    }

    override suspend fun enroll(utterances: List<ShortArray>): EnrollmentResult = withContext(workerDispatcher) {
        if (closed) return@withContext rejectedEnrollment(VoiceAuthFailure.ENGINE_CLOSED)
        if (utterances.size !in MIN_ENROLLMENT_UTTERANCES..MAX_ENROLLMENT_UTTERANCES) {
            return@withContext rejectedEnrollment(VoiceAuthFailure.INVALID_UTTERANCE_COUNT)
        }

        mutableState.value = VoiceAuthState.Enrolling
        val embeddings = ArrayList<FloatArray>(utterances.size)
        for (utterance in utterances) {
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
            templateStore.write(template)
            mutableState.value = VoiceAuthState.Ready
            EnrollmentResult.Success(embeddings.size)
        } catch (_: Exception) {
            rejectedEnrollment(VoiceAuthFailure.STORAGE_FAILURE)
        }
    }

    override suspend fun verify(utterance: ShortArray): VerificationResult = withContext(workerDispatcher) {
        if (closed) return@withContext rejectedVerification(null, VoiceAuthFailure.ENGINE_CLOSED)
        mutableState.value = VoiceAuthState.Verifying
        val template = try {
            templateStore.read()
        } catch (_: Exception) {
            return@withContext rejectedVerification(null, VoiceAuthFailure.STORAGE_FAILURE)
        } ?: return@withContext rejectedVerification(null, VoiceAuthFailure.NOT_ENROLLED)

        val normalizedTemplate = normalize(template)
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
                if (similarity >= similarityThreshold) {
                    mutableState.value = VoiceAuthState.Ready
                    VerificationResult.Accepted(similarity)
                } else {
                    rejectedVerification(similarity, VoiceAuthFailure.AUDIO_REJECTED)
                }
            }
        }
    }

    override fun isEnrolled(): Boolean = state.value == VoiceAuthState.Ready

    override fun clearEnrollment() {
        if (closed) return
        scope.launch {
            try {
                templateStore.clear()
                mutableState.value = VoiceAuthState.Unenrolled
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
        mutableState.value = VoiceAuthState.Failed(reason)
        return EnrollmentResult.Rejected(reason)
    }

    private fun rejectedVerification(similarity: Float?, reason: VoiceAuthFailure): VerificationResult.Rejected {
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

    private companion object {
        const val MIN_ENROLLMENT_UTTERANCES = 3
        const val MAX_ENROLLMENT_UTTERANCES = 5
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.85f
        const val MIN_VECTOR_MAGNITUDE = 1e-12
    }
}
