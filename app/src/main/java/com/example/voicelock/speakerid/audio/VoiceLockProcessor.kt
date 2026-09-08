package com.example.voicelock.speakerid.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orchestrates audio capture and VAD processing. */
class VoiceLockProcessor(
    private val recorder: AudioCapture,
    private val detector: VoiceActivityDetector,
) {
    /**
     * Captures audio for up to [maxDurationMs], then runs VAD to trim and validate speech.
     * Returns [Result.success] with trimmed PCM data if speech is detected and long enough.
     */
    suspend fun process(maxDurationMs: Long = DEFAULT_MAX_DURATION_MS): Result<FloatArray> = withContext(Dispatchers.Default) {
        val buffer = mutableListOf<Float>()
        val start = System.currentTimeMillis()

        try {
            recorder.capture { samples ->
                buffer.addAll(samples.toList())
                System.currentTimeMillis() - start < maxDurationMs
            }

            if (buffer.isEmpty()) return@withContext Result.failure(Exception("No audio captured"))

            val pcm = buffer.toFloatArray()
            when (val result = detector.trim(pcm, AudioCapture.SAMPLE_RATE_HZ)) {
                is SpeechResult.Speech -> Result.success(result.samples)
                is SpeechResult.NoSpeech -> Result.failure(Exception("No speech detected"))
                is SpeechResult.TooShort -> Result.failure(Exception("Speech utterance is too short (min 0.75s)"))
                is SpeechResult.InvalidSampleRate -> Result.failure(Exception("Invalid sample rate for VAD"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_MAX_DURATION_MS = 5_000L
    }
}
