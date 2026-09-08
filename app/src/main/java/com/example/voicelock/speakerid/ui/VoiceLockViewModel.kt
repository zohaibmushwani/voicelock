package com.example.voicelock.speakerid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicelock.speakerid.FBankExtractor
import com.example.voicelock.speakerid.audio.AudioCapture
import com.example.voicelock.speakerid.audio.OnnxSileroVad
import com.example.voicelock.speakerid.auth.DefaultVoiceAuthEngine
import com.example.voicelock.speakerid.auth.EnrollmentResult
import com.example.voicelock.speakerid.auth.VerificationResult
import com.example.voicelock.speakerid.embedding.AndroidVoiceEmbeddingProvider
import com.example.voicelock.speakerid.embedding.ModelAssetInstaller
import com.example.voicelock.speakerid.embedding.SpeakerEncoder
import com.example.voicelock.speakerid.storage.SpeakerTemplateStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class VoiceLockViewModel(application: Application) : AndroidViewModel(application) {
    private val capture = AudioCapture()
    private val storeDelegate = lazy { SpeakerTemplateStore(application) }
    private val store by storeDelegate
    private val engineDelegate = lazy {
        val vad = OnnxSileroVad(ModelAssetInstaller.vadModel(application))
        val encoder = SpeakerEncoder(ModelAssetInstaller.embeddingModel(application))
        DefaultVoiceAuthEngine(AndroidVoiceEmbeddingProvider(vad, FBankExtractor(), encoder), store)
    }
    private val engine by engineDelegate
    private val enrollment = mutableListOf<ShortArray>()

    private val mutableUi = MutableStateFlow(VoiceFlowUi())
    val ui = mutableUi.asStateFlow()
    private val eventChannel = Channel<VoiceFlowEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.read() != null }.onSuccess { enrolled ->
                mutableUi.value = mutableUi.value.copy(enrolled = enrolled)
                if (enrolled) log("Encrypted voice template loaded")
            }
        }
    }

    fun recordEnrollmentSample(completed: Int) = runCapture("Recording enrollment sample ${completed + 1}") { pcm ->
        if (completed == 0) enrollment.clear()
        enrollment += pcm
        if (enrollment.size < REQUIRED_SAMPLES) {
            log("Sample ${enrollment.size} captured successfully")
            eventChannel.send(VoiceFlowEvent.EnrollmentProgress(enrollment.size))
        } else {
            log("Generating encrypted voice template")
            when (val result = engine.enroll(enrollment.toList())) {
                is EnrollmentResult.Success -> {
                    enrollment.clear()
                    mutableUi.value = mutableUi.value.copy(enrolled = true)
                    log("Enrollment complete — ${result.utteranceCount} samples secured")
                    eventChannel.send(VoiceFlowEvent.EnrollmentComplete)
                }
                is EnrollmentResult.Rejected -> {
                    enrollment.clear()
                    eventChannel.send(VoiceFlowEvent.EnrollmentProgress(0))
                    fail("Enrollment rejected: ${result.reason.userMessage}")
                }
            }
        }
    }

    fun verify() = runCapture("Recording verification sample") { pcm ->
        log("Comparing speaker embeddings")
        when (val result = engine.verify(pcm)) {
            is VerificationResult.Accepted -> {
                val percent = (result.similarity * 100).roundToInt().coerceIn(0, 100)
                log("Voice accepted — $percent% match")
                eventChannel.send(VoiceFlowEvent.Verification(true, percent))
            }
            is VerificationResult.Rejected -> {
                val percent = result.similarity?.times(100)?.roundToInt()?.coerceIn(0, 100)
                log(percent?.let { "Voice rejected — $it% match" } ?: result.reason.userMessage)
                eventChannel.send(VoiceFlowEvent.Verification(false, percent))
            }
        }
    }

    fun clearError() { mutableUi.value = mutableUi.value.copy(error = null) }

    private fun runCapture(message: String, action: suspend (ShortArray) -> Unit) {
        if (mutableUi.value.busy) return
        viewModelScope.launch {
            mutableUi.value = mutableUi.value.copy(busy = true, error = null)
            log(message)
            try {
                val samples = ArrayList<Float>(AudioCapture.SAMPLE_RATE_HZ * CAPTURE_SECONDS)
                capture.capture { chunk ->
                    val remaining = AudioCapture.SAMPLE_RATE_HZ * CAPTURE_SECONDS - samples.size
                    chunk.take(remaining.coerceAtLeast(0)).forEach(samples::add)
                    samples.size < AudioCapture.SAMPLE_RATE_HZ * CAPTURE_SECONDS
                }
                if (samples.isEmpty()) error("No audio was captured")
                val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort() }
                withContext(Dispatchers.Default) { action(pcm) }
            } catch (error: Exception) {
                fail(error.message ?: "Audio processing failed")
            } finally {
                mutableUi.value = mutableUi.value.copy(busy = false)
            }
        }
    }

    private suspend fun fail(message: String) {
        log(message)
        mutableUi.value = mutableUi.value.copy(error = message)
        eventChannel.send(VoiceFlowEvent.Failed(message))
    }

    private fun log(message: String) {
        mutableUi.value = mutableUi.value.copy(logs = (mutableUi.value.logs + message).takeLast(4))
    }

    override fun onCleared() {
        capture.close()
        if (engineDelegate.isInitialized()) engine.close()
        super.onCleared()
    }

    private companion object { const val REQUIRED_SAMPLES = 3; const val CAPTURE_SECONDS = 4 }
}

data class VoiceFlowUi(
    val busy: Boolean = false,
    val enrolled: Boolean = false,
    val error: String? = null,
    val logs: List<String> = listOf("App ready — processing stays on device"),
)

sealed interface VoiceFlowEvent {
    data class EnrollmentProgress(val completed: Int) : VoiceFlowEvent
    data object EnrollmentComplete : VoiceFlowEvent
    data class Verification(val accepted: Boolean, val percent: Int?) : VoiceFlowEvent
    data class Failed(val message: String) : VoiceFlowEvent
}

private val com.example.voicelock.speakerid.auth.VoiceAuthFailure.userMessage: String
    get() = when (this) {
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.NOT_ENROLLED -> "Enroll your voice first"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.INVALID_UTTERANCE_COUNT -> "Three clear samples are required"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.AUDIO_REJECTED -> "Speech was unclear or too short"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.NO_SPEECH -> "No speech detected — tap first, then speak"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.SPEECH_TOO_SHORT -> "Speech was too short — say the complete sentence"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.EMBEDDING_INVALID -> "The voice model could not create a valid template"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.STORAGE_FAILURE -> "Secure storage is unavailable"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.ENGINE_CLOSED -> "Voice engine is unavailable"
    }
