package com.example.voicelock.speakerid.embedding

import com.example.voicelock.speakerid.FBankExtractor
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
    override suspend fun createEmbedding(utterance: FloatArray): EmbeddingExtractionResult =
        when (val speech = vad.trim(utterance, 16000)) {
            is SpeechResult.Speech -> runCatching { speakerEncoder.extractEmbedding(fbank.computeFBank(speech.samples)) }
                .fold({ EmbeddingExtractionResult.Success(it) }, { EmbeddingExtractionResult.Rejected(VoiceAuthFailure.EMBEDDING_INVALID) })
            SpeechResult.TooShort -> EmbeddingExtractionResult.Rejected(VoiceAuthFailure.SPEECH_TOO_SHORT)
            SpeechResult.NoSpeech -> EmbeddingExtractionResult.Rejected(VoiceAuthFailure.NO_SPEECH)
            SpeechResult.InvalidSampleRate -> EmbeddingExtractionResult.Rejected(VoiceAuthFailure.AUDIO_REJECTED)
        }

    override fun close() {
        (vad as? AutoCloseable)?.close()
        speakerEncoder.close()
    }
}
