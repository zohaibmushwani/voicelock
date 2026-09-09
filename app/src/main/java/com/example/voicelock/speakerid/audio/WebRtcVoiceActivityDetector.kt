package com.example.voicelock.speakerid.audio

import com.example.voicelock.speakerid.VoiceLockLog
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate

/**
 * Adapter for the maintained android-vad WebRTC implementation.
 *
 * A fresh native detector is used for each utterance so its duration counters cannot leak from
 * one enrollment or verification recording into the next one.
 */
class WebRtcVoiceActivityDetector : VoiceActivityDetector {
    override suspend fun trim(samples: FloatArray, sampleRate: Int): SpeechResult {
        if (sampleRate != AudioCapture.SAMPLE_RATE_HZ) return SpeechResult.InvalidSampleRate
        if (samples.isEmpty()) return SpeechResult.NoSpeech

        val frameCount = (samples.size + FRAME_SAMPLES - 1) / FRAME_SAMPLES
        val speechFrames = BooleanArray(frameCount)
        VadWebRTC(
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_320,
            mode = Mode.NORMAL,
            speechDurationMs = MIN_CONTINUOUS_SPEECH_MS,
            silenceDurationMs = ALLOWED_PAUSE_MS,
        ).use { detector ->
            repeat(frameCount) { index ->
                val start = index * FRAME_SAMPLES
                val frame = FloatArray(FRAME_SAMPLES)
                samples.copyInto(
                    destination = frame,
                    startIndex = start,
                    endIndex = minOf(start + FRAME_SAMPLES, samples.size),
                )
                speechFrames[index] = detector.isSpeech(frame)
            }
        }

        val speechFrameCount = speechFrames.count { it }
        VoiceLockLog.info("WebRTC VAD: $speechFrameCount/$frameCount speech frames")
        return SpeechTrimmer.trim(samples, speechFrames, FRAME_SAMPLES, sampleRate)
    }

    private companion object {
        const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
        const val MIN_CONTINUOUS_SPEECH_MS = 40
        const val ALLOWED_PAUSE_MS = 200
    }
}
