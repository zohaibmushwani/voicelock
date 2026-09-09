package com.example.voicelock.speakerid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicelock.speakerid.FBankExtractor
import com.example.voicelock.speakerid.VoiceLockLog
import com.example.voicelock.speakerid.audio.AudioCapture
import com.example.voicelock.speakerid.audio.DiarizationResult
import com.example.voicelock.speakerid.audio.DiarizationAudioDecoder
import com.example.voicelock.speakerid.audio.SpeakerDiarizer
import com.example.voicelock.speakerid.audio.WebRtcVoiceActivityDetector
import com.example.voicelock.speakerid.auth.DefaultVoiceAuthEngine
import com.example.voicelock.speakerid.auth.EnrollmentResult
import com.example.voicelock.speakerid.auth.IdentificationResult
import com.example.voicelock.speakerid.auth.VerificationResult
import com.example.voicelock.speakerid.embedding.AndroidVoiceEmbeddingProvider
import com.example.voicelock.speakerid.embedding.ModelAssetInstaller
import com.example.voicelock.speakerid.embedding.SpeakerEncoder
import com.example.voicelock.speakerid.storage.ProfileStoreException
import com.example.voicelock.speakerid.storage.SpeakerProfile
import com.example.voicelock.speakerid.storage.SpeakerProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class VoiceLockViewModel(application: Application) : AndroidViewModel(application) {
    private val capture = AudioCapture()
    private val audioDecoder = DiarizationAudioDecoder(application)
    private val profileStore = SpeakerProfileStore(application)
    private val embeddingProviderDelegate = lazy {
        AndroidVoiceEmbeddingProvider(WebRtcVoiceActivityDetector(), FBankExtractor(), SpeakerEncoder(ModelAssetInstaller.embeddingModel(application)))
    }
    private val embeddingProvider by embeddingProviderDelegate
    private val engineDelegate = lazy { DefaultVoiceAuthEngine(embeddingProvider, profileStore) }
    private val diarizerDelegate = lazy { SpeakerDiarizer(WebRtcVoiceActivityDetector(), embeddingProvider) }
    private val engine by engineDelegate
    private val diarizer by diarizerDelegate
    private val enrollment = mutableListOf<ShortArray>()
    private var pendingEnrollmentName: String? = null

    private val mutableUi = MutableStateFlow(VoiceFlowUi())
    val ui = mutableUi.asStateFlow()
    private val eventChannel = Channel<VoiceFlowEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        VoiceLockLog.info("Voice profile flow initialized")
        refreshProfiles()
    }

    fun refreshProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { profileStore.profiles() }
                .onSuccess { profiles ->
                    mutableUi.value = mutableUi.value.copy(profiles = profiles, profilesLoaded = true)
                    log(if (profiles.isEmpty()) "No voice profiles yet" else "Loaded ${profiles.size} local voice profiles")
                }
                .onFailure { error ->
                    VoiceLockLog.error("Unable to load voice profiles", error)
                    mutableUi.value = mutableUi.value.copy(profilesLoaded = true, error = "Secure profile storage is unavailable")
                }
        }
    }

    fun beginEnrollment(name: String): Boolean = runCatching {
        pendingEnrollmentName = SpeakerProfileStore.requireValidName(name)
        enrollment.clear()
        clearError()
        true
    }.getOrElse { error ->
        mutableUi.value = mutableUi.value.copy(error = error.userMessage)
        false
    }

    fun recordEnrollmentSample(completed: Int) = runCapture("Recording enrollment sample ${completed + 1}") { pcm ->
        val displayName = pendingEnrollmentName ?: return@runCapture fail("Choose a profile name before recording")
        if (completed == 0) enrollment.clear()
        enrollment += pcm
        if (enrollment.size < REQUIRED_SAMPLES) {
            log("Sample ${enrollment.size} captured")
            eventChannel.send(VoiceFlowEvent.EnrollmentProgress(enrollment.size))
        } else {
            log("Creating encrypted voice profile")
            when (val result = engine.enroll(displayName, enrollment.toList())) {
                is EnrollmentResult.Success -> {
                    enrollment.clear()
                    pendingEnrollmentName = null
                    refreshProfiles()
                    log("Enrollment complete — profile saved")
                    eventChannel.send(VoiceFlowEvent.EnrollmentComplete(result.profileId))
                }
                is EnrollmentResult.Rejected -> {
                    enrollment.clear()
                    eventChannel.send(VoiceFlowEvent.EnrollmentProgress(0))
                    fail("Enrollment rejected: ${result.reason.userMessage}")
                }
            }
        }
    }

    fun verify(profileId: String) = runCapture("Recording verification sample") { pcm ->
        log("Comparing selected voice profile")
        when (val result = engine.verify(profileId, pcm)) {
            is VerificationResult.Accepted -> {
                val percent = result.similarity.toPercent()
                log("Voice accepted — similarity $percent%")
                eventChannel.send(VoiceFlowEvent.Verification(profileId, true, percent))
            }
            is VerificationResult.Rejected -> {
                val percent = result.similarity?.toPercent()
                log(percent?.let { "Voice rejected — similarity $it%" } ?: result.reason.userMessage)
                eventChannel.send(VoiceFlowEvent.Verification(profileId, false, percent))
            }
        }
    }

    fun identify() = runCapture("Recording speaker identification sample") { pcm ->
        log("Comparing against ${mutableUi.value.profiles.size} local profiles")
        when (val result = engine.identify(pcm)) {
            is IdentificationResult.Identified -> {
                val percent = result.similarity.toPercent()
                log("Speaker identified — similarity $percent%")
                eventChannel.send(VoiceFlowEvent.Identification(result.profileId, percent, unknown = false))
            }
            is IdentificationResult.Unknown -> {
                val percent = result.bestSimilarity?.toPercent()
                log(percent?.let { "Unknown speaker — best similarity $it%" } ?: "Unknown speaker")
                eventChannel.send(VoiceFlowEvent.Identification(null, percent, unknown = true))
            }
            is IdentificationResult.Rejected -> fail(result.reason.userMessage)
        }
    }

    /** Captures one longer sample, publishes live level-only telemetry, then clusters speech windows. */
    fun diarize() {
        if (mutableUi.value.busy) return
        viewModelScope.launch {
            mutableUi.value = mutableUi.value.copy(
                busy = true,
                error = null,
                diarization = DiarizationUiState(isRecording = true),
            )
            log("Diarization recording started — play speech near the microphone")
            try {
                val samples = ArrayList<Float>(AudioCapture.SAMPLE_RATE_HZ * DIARIZATION_CAPTURE_SECONDS)
                capture.capture { chunk ->
                    val remaining = AudioCapture.SAMPLE_RATE_HZ * DIARIZATION_CAPTURE_SECONDS - samples.size
                    val accepted = chunk.take(remaining.coerceAtLeast(0))
                    samples.addAll(accepted)
                    val level = accepted.rms()
                    val current = mutableUi.value.diarization
                    mutableUi.value = mutableUi.value.copy(
                        diarization = (current ?: DiarizationUiState()).copy(
                            isRecording = true,
                            capturedMs = samples.size.toMillis(),
                            waveform = ((current?.waveform ?: emptyList()) + level).takeLast(MAX_WAVEFORM_POINTS),
                        ),
                    )
                    samples.size < AudioCapture.SAMPLE_RATE_HZ * DIARIZATION_CAPTURE_SECONDS
                }
                if (samples.size < AudioCapture.SAMPLE_RATE_HZ * MIN_DIARIZATION_SECONDS) error("Record at least $MIN_DIARIZATION_SECONDS seconds of speech")
                mutableUi.value = mutableUi.value.copy(
                    diarization = mutableUi.value.diarization?.copy(isRecording = false, isProcessing = true),
                )
                log("Clustering speech windows")
                val result = withContext(Dispatchers.Default) { diarizer.diarize(samples.toFloatArray()) }
                mutableUi.value = mutableUi.value.copy(diarization = result.toUiState(mutableUi.value.diarization?.waveform.orEmpty()))
                log("Diarization complete — ${result.speakerCount} speaker clusters, ${result.segments.size} speech regions")
            } catch (error: Exception) {
                VoiceLockLog.error("Diarization failed: ${error.message ?: error.javaClass.simpleName}", error)
                fail(error.message ?: "Diarization failed")
            } finally {
                mutableUi.value = mutableUi.value.copy(
                    busy = false,
                    diarization = mutableUi.value.diarization?.copy(isRecording = false, isProcessing = false),
                )
            }
        }
    }

    fun diarizeFixture(assetName: String) = runFileDiarization("Testing included fixture $assetName") {
        audioDecoder.decodeAsset(assetName)
    }

    fun diarizeIncludedFixtureSet() = runFileDiarization("Testing three included speaker fixtures") {
        concatenateWithGaps(
            audioDecoder.decodeAsset("speaker_1.flac"),
            audioDecoder.decodeAsset("speaker_2.flac"),
            audioDecoder.decodeAsset("speaker_3.flac"),
        )
    }

    fun diarizeFile(uri: Uri) = runFileDiarization("Testing selected audio file") {
        audioDecoder.decodeUri(uri)
    }

    fun renameProfile(id: String, name: String) = mutateProfiles("Profile renamed") { profileStore.rename(id, name) }

    fun deleteProfile(id: String) = mutateProfiles("Profile deleted") {
        if (!profileStore.delete(id)) throw ProfileStoreException.ProfileNotFound
    }

    fun clearError() { mutableUi.value = mutableUi.value.copy(error = null) }

    private fun mutateProfiles(successMessage: String, action: suspend () -> Unit) {
        if (mutableUi.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableUi.value = mutableUi.value.copy(busy = true, error = null)
            runCatching { action() }
                .onSuccess {
                    log(successMessage)
                    mutableUi.value = mutableUi.value.copy(profiles = profileStore.profiles())
                }
                .onFailure { error ->
                    VoiceLockLog.error("Profile operation failed", error)
                    mutableUi.value = mutableUi.value.copy(error = error.userMessage)
                }
            mutableUi.value = mutableUi.value.copy(busy = false)
        }
    }

    private fun runFileDiarization(message: String, loadAudio: suspend () -> FloatArray) {
        if (mutableUi.value.busy) return
        viewModelScope.launch {
            mutableUi.value = mutableUi.value.copy(busy = true, error = null, diarization = DiarizationUiState(isProcessing = true))
            log(message)
            try {
                val samples = withContext(Dispatchers.IO) { loadAudio() }
                require(samples.size >= AudioCapture.SAMPLE_RATE_HZ * MIN_DIARIZATION_SECONDS) { "Audio must be at least $MIN_DIARIZATION_SECONDS seconds" }
                require(samples.size <= AudioCapture.SAMPLE_RATE_HZ * MAX_FILE_DIARIZATION_SECONDS) { "Choose an audio file no longer than $MAX_FILE_DIARIZATION_SECONDS seconds" }
                val result = withContext(Dispatchers.Default) { diarizer.diarize(samples) }
                mutableUi.value = mutableUi.value.copy(diarization = result.toUiState(samples.decimatedLevels()))
                log("Fixture diarization complete — ${result.speakerCount} speaker clusters, ${result.segments.size} speech regions")
            } catch (error: Exception) {
                VoiceLockLog.error("Audio-file diarization failed: ${error.message ?: error.javaClass.simpleName}", error)
                fail(error.message ?: "Audio-file diarization failed")
            } finally {
                mutableUi.value = mutableUi.value.copy(busy = false, diarization = mutableUi.value.diarization?.copy(isProcessing = false))
            }
        }
    }

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
                log("Captured ${samples.size / AudioCapture.SAMPLE_RATE_HZ}.${(samples.size % AudioCapture.SAMPLE_RATE_HZ) * 10 / AudioCapture.SAMPLE_RATE_HZ}s of audio")
                val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort() }
                withContext(Dispatchers.Default) { action(pcm) }
            } catch (error: Exception) {
                VoiceLockLog.error("Voice operation failed: ${error.message ?: error.javaClass.simpleName}", error)
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
        VoiceLockLog.info(message)
        mutableUi.value = mutableUi.value.copy(logs = (mutableUi.value.logs + message).takeLast(MAX_STATUS_LINES))
    }

    override fun onCleared() {
        capture.close()
        if (engineDelegate.isInitialized()) engine.close()
        else if (embeddingProviderDelegate.isInitialized()) embeddingProvider.close()
        super.onCleared()
    }

    private companion object {
        const val REQUIRED_SAMPLES = 3
        const val CAPTURE_SECONDS = 4
        const val DIARIZATION_CAPTURE_SECONDS = 20
        const val MIN_DIARIZATION_SECONDS = 3
        const val MAX_FILE_DIARIZATION_SECONDS = 60
        const val MAX_WAVEFORM_POINTS = 48
        const val MAX_STATUS_LINES = 8
    }
}

data class VoiceFlowUi(
    val busy: Boolean = false,
    val profilesLoaded: Boolean = false,
    val profiles: List<SpeakerProfile> = emptyList(),
    val error: String? = null,
    val diarization: DiarizationUiState? = null,
    val logs: List<String> = listOf("App ready — processing stays on device"),
)

data class DiarizationUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val capturedMs: Long = 0,
    val durationMs: Long = 0,
    val speechDurationMs: Int = 0,
    val waveform: List<Float> = emptyList(),
    val segments: List<com.example.voicelock.speakerid.audio.DiarizedSegment> = emptyList(),
)

sealed interface VoiceFlowEvent {
    data class EnrollmentProgress(val completed: Int) : VoiceFlowEvent
    data class EnrollmentComplete(val profileId: String) : VoiceFlowEvent
    data class Verification(val profileId: String, val accepted: Boolean, val percent: Int?) : VoiceFlowEvent
    data class Identification(val profileId: String?, val percent: Int?, val unknown: Boolean) : VoiceFlowEvent
    data class Failed(val message: String) : VoiceFlowEvent
}

private fun Float.toPercent(): Int = (this * 100).roundToInt().coerceIn(0, 100)

private fun Collection<Float>.rms(): Float {
    if (isEmpty()) return 0f
    val meanSquare = sumOf { (it * it).toDouble() } / size
    return kotlin.math.sqrt(meanSquare).toFloat().coerceIn(0f, 1f)
}

private fun Int.toMillis(): Long = toLong() * 1_000 / AudioCapture.SAMPLE_RATE_HZ

private fun FloatArray.decimatedLevels(): List<Float> =
    asList().chunked((size / 48).coerceAtLeast(1)).map { it.rms() }.takeLast(48)

private fun concatenateWithGaps(vararg clips: FloatArray): FloatArray {
    val gap = FloatArray(AudioCapture.SAMPLE_RATE_HZ / 2)
    val result = FloatArray(clips.sumOf { it.size } + gap.size * (clips.size - 1).coerceAtLeast(0))
    var position = 0
    clips.forEachIndexed { index, clip ->
        clip.copyInto(result, destinationOffset = position)
        position += clip.size
        if (index < clips.lastIndex) {
            gap.copyInto(result, destinationOffset = position)
            position += gap.size
        }
    }
    return result
}

private fun DiarizationResult.toUiState(waveform: List<Float>) = DiarizationUiState(
    durationMs = durationMs,
    speechDurationMs = speechDurationMs,
    waveform = waveform,
    segments = segments,
)

private val Throwable.userMessage: String
    get() = when (this) {
        is ProfileStoreException -> message ?: "Profile storage is unavailable"
        else -> message ?: "Voice operation failed"
    }

private val com.example.voicelock.speakerid.auth.VoiceAuthFailure.userMessage: String
    get() = when (this) {
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.NOT_ENROLLED -> "Enroll a voice profile first"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.INVALID_UTTERANCE_COUNT -> "Three clear samples are required"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.AUDIO_REJECTED -> "Speech was unclear or below the required similarity"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.NO_SPEECH -> "No speech detected — tap first, then speak"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.SPEECH_TOO_SHORT -> "Speech was too short — say the complete sentence"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.EMBEDDING_INVALID -> "The voice model could not create a valid template"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.STORAGE_FAILURE -> "Secure profile storage is unavailable"
        com.example.voicelock.speakerid.auth.VoiceAuthFailure.ENGINE_CLOSED -> "Voice engine is unavailable"
    }
