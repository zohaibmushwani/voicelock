package com.example.voicelock.speakerid.audio

/** VAD must trim non-speech and reject utterances too short for speaker verification. */
fun interface VoiceActivityDetector {
    suspend fun trim(samples: FloatArray, sampleRate: Int): SpeechResult
}

sealed interface SpeechResult {
    data class Speech(val samples: FloatArray) : SpeechResult
    data object NoSpeech : SpeechResult
    data object TooShort : SpeechResult
    data object InvalidSampleRate : SpeechResult
}

/** Shared policy used after Silero scores speech frames. */
object SpeechTrimmer {
    const val MIN_SPEECH_DURATION_MS = 750

    fun trim(
        source: FloatArray,
        speechFrames: BooleanArray,
        frameSamples: Int,
        sampleRate: Int,
    ): SpeechResult {
        if (sampleRate != AudioCapture.SAMPLE_RATE_HZ || frameSamples <= 0) return SpeechResult.InvalidSampleRate
        val first = speechFrames.indexOfFirst { it }
        val last = speechFrames.indexOfLast { it }
        if (first < 0) return SpeechResult.NoSpeech
        val start = (first * frameSamples).coerceAtMost(source.size)
        val end = ((last + 1) * frameSamples).coerceAtMost(source.size)
        val speech = source.copyOfRange(start, end)
        return if (speech.size < sampleRate * MIN_SPEECH_DURATION_MS / 1_000) SpeechResult.TooShort else SpeechResult.Speech(speech)
    }
}
