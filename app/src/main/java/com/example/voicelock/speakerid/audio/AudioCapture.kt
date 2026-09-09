package com.example.voicelock.speakerid.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.voicelock.speakerid.VoiceLockLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 16 kHz mono, PCM-float microphone capture. The caller must obtain RECORD_AUDIO permission. */
class AudioCapture(
    private val sampleRate: Int = SAMPLE_RATE_HZ,
) : AutoCloseable {
    private var recorder: AudioRecord? = null

    @SuppressLint("MissingPermission")
    suspend fun capture(onSamples: suspend (FloatArray) -> Boolean) = withContext(Dispatchers.IO) {
        check(recorder == null) { "Audio capture is already running" }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minBuffer > 0) { "PCM float capture is not supported on this device" }
        VoiceLockLog.info("Microphone setup: ${sampleRate} Hz mono, minBuffer=$minBuffer bytes")
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, BUFFER_SAMPLES * Float.SIZE_BYTES))
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Unable to initialize microphone capture" }

        recorder = record
        var capturedSamples = 0
        try {
            record.startRecording()
            VoiceLockLog.info("Microphone recording started")
            val buffer = FloatArray(BUFFER_SAMPLES)
            while (coroutineContext.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    capturedSamples += count
                    if (!onSamples(buffer.copyOf(count))) break
                } else if (count < 0) {
                    error("Microphone read failed with code $count")
                }
                coroutineContext.ensureActive()
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
            recorder = null
            VoiceLockLog.info("Microphone recording stopped: $capturedSamples samples")
        }
    }

    override fun close() {
        recorder?.let { record ->
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
        recorder = null
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val BUFFER_SAMPLES = 1_600 // 100 ms
    }
}
