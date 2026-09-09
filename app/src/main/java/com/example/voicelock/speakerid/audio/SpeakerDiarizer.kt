package com.example.voicelock.speakerid.audio

import com.example.voicelock.speakerid.VoiceLockLog
import com.example.voicelock.speakerid.auth.EmbeddingExtractionResult
import com.example.voicelock.speakerid.auth.UtteranceEmbeddingProvider
import kotlin.math.sqrt

/**
 * Experimental, closed-set-free diarization for a short recording.
 *
 * This deliberately uses the app's existing WebRTC VAD and ECAPA embedding provider. It does
 * not claim to solve overlapping speech: overlap detection/separation requires a different
 * model and remains outside this experiment.
 */
class SpeakerDiarizer(
    private val vad: WebRtcVoiceActivityDetector,
    private val embeddingProvider: UtteranceEmbeddingProvider,
    private val clusteringThreshold: Float = DEFAULT_CLUSTERING_THRESHOLD,
) {
    suspend fun diarize(samples: FloatArray): DiarizationResult {
        val mask = vad.frameMask(samples, AudioCapture.SAMPLE_RATE_HZ)
            ?: return DiarizationResult(emptyList(), 0, samples.durationMs())
        val speechMs = mask.count { it } * FRAME_DURATION_MS
        val clusterer = OnlineSpeakerClusterer(clusteringThreshold)
        val diarized = buildList {
            speechRegions(mask).forEach { region ->
                var start = region.first * FRAME_SAMPLES
                val end = minOf(samples.size, (region.last + 1) * FRAME_SAMPLES)
                while (end - start >= MIN_EMBEDDING_SAMPLES) {
                    val analysisEnd = minOf(end, start + EMBEDDING_WINDOW_SAMPLES)
                    val window = samples.copyOfRange(start, analysisEnd)
                    val embedding = (embeddingProvider.createEmbedding(window) as? EmbeddingExtractionResult.Success)?.embedding
                    if (embedding != null) {
                        clusterer.assign(embedding)?.let { cluster ->
                            add(
                                DiarizedSegment(
                                    startMs = start.toMillis(),
                                    endMs = minOf(end, start + WINDOW_HOP_SAMPLES).toMillis(),
                                    speaker = cluster,
                                ),
                            )
                        }
                    }
                    start += WINDOW_HOP_SAMPLES
                }
            }
        }.mergeAdjacent()
        VoiceLockLog.info("Diarization complete: ${diarized.size} regions, ${clusterer.clusterCount} speaker clusters")
        return DiarizationResult(diarized, speechMs, samples.durationMs())
    }

    private fun speechRegions(mask: BooleanArray): List<IntRange> {
        val regions = mutableListOf<IntRange>()
        var firstSpeech = -1
        var silenceFrames = 0
        mask.forEachIndexed { index, speech ->
            if (speech) {
                if (firstSpeech < 0) firstSpeech = index
                silenceFrames = 0
            } else if (firstSpeech >= 0) {
                silenceFrames++
                if (silenceFrames > MAX_GAP_FRAMES) {
                    regions += firstSpeech..(index - silenceFrames)
                    firstSpeech = -1
                    silenceFrames = 0
                }
            }
        }
        if (firstSpeech >= 0) regions += firstSpeech..mask.lastIndex
        return regions
    }

    private fun List<DiarizedSegment>.mergeAdjacent(): List<DiarizedSegment> =
        fold(emptyList()) { result, segment ->
            val previous = result.lastOrNull()
            if (previous != null && previous.speaker == segment.speaker && segment.startMs <= previous.endMs + FRAME_DURATION_MS) {
                result.dropLast(1) + previous.copy(endMs = maxOf(previous.endMs, segment.endMs))
            } else {
                result + segment
            }
        }

    private fun Int.toMillis(): Long = toLong() * 1_000 / AudioCapture.SAMPLE_RATE_HZ
    private fun FloatArray.durationMs(): Long = size.toLong() * 1_000 / AudioCapture.SAMPLE_RATE_HZ

    companion object {
        const val MAX_SPEAKERS = 10
        const val DEFAULT_CLUSTERING_THRESHOLD = 0.62f
        private const val FRAME_SAMPLES = 320
        private const val FRAME_DURATION_MS = 20
        private const val MAX_GAP_FRAMES = 15 // 300 ms
        private const val EMBEDDING_WINDOW_SAMPLES = 24_000 // 1.5 s
        private const val WINDOW_HOP_SAMPLES = 12_000 // 0.75 s
        private const val MIN_EMBEDDING_SAMPLES = 12_000 // 0.75 s
    }
}

data class DiarizationResult(
    val segments: List<DiarizedSegment>,
    val speechDurationMs: Int,
    val durationMs: Long,
) {
    val speakerCount: Int get() = segments.map(DiarizedSegment::speaker).distinct().size
}

data class DiarizedSegment(val startMs: Long, val endMs: Long, val speaker: Int)

/** Small deterministic online centroid clusterer; no raw embedding escapes this component. */
class OnlineSpeakerClusterer(
    private val threshold: Float,
    private val maxSpeakers: Int = SpeakerDiarizer.MAX_SPEAKERS,
) {
    private val centroids = mutableListOf<FloatArray>()
    val clusterCount: Int get() = centroids.size

    fun assign(embedding: FloatArray): Int? {
        val normalized = embedding.normalized() ?: return null
        val best = centroids.indices.maxByOrNull { cosine(normalized, centroids[it]) }
        if (best != null && cosine(normalized, centroids[best]) >= threshold) {
            centroids[best] = average(centroids[best], normalized).normalized() ?: centroids[best]
            return best + 1
        }
        if (centroids.size >= maxSpeakers) return best?.plus(1)
        centroids += normalized
        return centroids.size
    }

    private fun FloatArray.normalized(): FloatArray? {
        val norm = sqrt(sumOf { it.toDouble() * it }.toFloat())
        return if (norm > 0f && norm.isFinite()) FloatArray(size) { this[it] / norm } else null
    }

    private fun average(first: FloatArray, second: FloatArray): FloatArray =
        FloatArray(first.size) { index -> (first[index] + second[index]) / 2f }

    private fun cosine(first: FloatArray, second: FloatArray): Float =
        if (first.size != second.size) -1f else first.indices.sumOf { (first[it] * second[it]).toDouble() }.toFloat()
}
