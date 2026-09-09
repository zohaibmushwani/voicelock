package com.example.voicelock.speakerid.embedding

import com.example.voicelock.speakerid.FBankExtractor
import com.example.voicelock.speakerid.VoiceLockLog
import com.example.voicelock.speakerid.audio.SpeechResult
import com.example.voicelock.speakerid.audio.VoiceActivityDetector
import com.example.voicelock.speakerid.auth.EmbeddingExtractionResult
import com.example.voicelock.speakerid.auth.UtteranceEmbeddingProvider
import com.example.voicelock.speakerid.auth.VoiceAuthFailure

/** Production bridge from raw 16 kHz PCM through VAD, FBank, and ECAPA embedding inference. */
class AndroidVoiceEmbeddingProvider(
    private val vad: VoiceActivityDetector,
    private val fbank: FBankExtractor,
    private val speakerEncoder: SpeakerEncoder,
) : UtteranceEmbeddingProvider {
    override suspend fun createEmbedding(utterance: FloatArray): EmbeddingExtractionResult {
        val startedAt = System.nanoTime()
        VoiceLockLog.info("Embedding pipeline started: ${utterance.size} input samples")
        return when (val speech = vad.trim(utterance, SAMPLE_RATE_HZ)) {
            is SpeechResult.Speech -> runCatching {
                VoiceLockLog.info("VAD accepted ${(speech.samples.size * 1000L) / SAMPLE_RATE_HZ} ms of speech")
                val features = fbank.computeFBank(speech.samples)
                VoiceLockLog.info("FBank ready: ${features.size / FBANK_BINS} frames x $FBANK_BINS bins")
                speakerEncoder.extractEmbedding(features)
            }.fold(
                onSuccess = {
                    VoiceLockLog.info("Embedding ready: ${it.size} values in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
                    EmbeddingExtractionResult.Success(it)
                },
                onFailure = {
                    VoiceLockLog.error("Embedding inference failed", it)
                    EmbeddingExtractionResult.Rejected(VoiceAuthFailure.EMBEDDING_INVALID)
                },
            )
            SpeechResult.TooShort -> rejected("VAD rejected speech: too short", VoiceAuthFailure.SPEECH_TOO_SHORT)
            SpeechResult.NoSpeech -> rejected("VAD rejected audio: no speech", VoiceAuthFailure.NO_SPEECH)
            SpeechResult.InvalidSampleRate -> rejected("VAD rejected audio: invalid sample rate", VoiceAuthFailure.AUDIO_REJECTED)
        }
    }

    private fun rejected(message: String, reason: VoiceAuthFailure): EmbeddingExtractionResult.Rejected {
        VoiceLockLog.warn(message)
        return EmbeddingExtractionResult.Rejected(reason)
    }

    override fun close() {
        (vad as? AutoCloseable)?.close()
        speakerEncoder.close()
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FBANK_BINS = 80
    }
}
