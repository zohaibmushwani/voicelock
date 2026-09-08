package com.example.voicelock.speakerid.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Stateful Silero VAD wrapper for the pinned 16 kHz ONNX export. */
class OnnxSileroVad(
    private val speechThreshold: Float = DEFAULT_SPEECH_THRESHOLD,
    private val sessionProvider: () -> OrtSession
) : VoiceActivityDetector, AutoCloseable {
    private var _session: OrtSession? = null
    private val session: OrtSession
        get() = _session ?: sessionProvider().also { _session = it }
    
    private val environment = OrtEnvironment.getEnvironment()
    private val inputName = "input"

    constructor(session: OrtSession, speechThreshold: Float = DEFAULT_SPEECH_THRESHOLD) : this(
        speechThreshold,
        { session }
    )

    constructor(modelFile: File, speechThreshold: Float = DEFAULT_SPEECH_THRESHOLD) : this(
        speechThreshold,
        { OrtEnvironment.getEnvironment().createSession(modelFile.absolutePath, OrtSession.SessionOptions()) }
    )

    constructor(modelBytes: ByteArray, speechThreshold: Float = DEFAULT_SPEECH_THRESHOLD) : this(
        speechThreshold,
        { OrtEnvironment.getEnvironment().createSession(modelBytes, OrtSession.SessionOptions()) }
    )

    override suspend fun trim(samples: FloatArray, sampleRate: Int): SpeechResult {
        if (sampleRate != AudioCapture.SAMPLE_RATE_HZ) return SpeechResult.InvalidSampleRate
        if (samples.isEmpty()) return SpeechResult.NoSpeech
        val frameCount = (samples.size + FRAME_SAMPLES - 1) / FRAME_SAMPLES
        val speech = BooleanArray(frameCount)
        var state = FloatArray(2 * STATE_SIZE)
        repeat(frameCount) { index ->
            val frame = FloatArray(FRAME_SAMPLES)
            val start = index * FRAME_SAMPLES
            samples.copyInto(frame, destinationOffset = 0, startIndex = start, endIndex = minOf(start + FRAME_SAMPLES, samples.size))
            val scored = score(frame, state)
            speech[index] = scored.first >= speechThreshold
            state = scored.second
        }
        return SpeechTrimmer.trim(samples, speech, FRAME_SAMPLES, sampleRate)
    }

    private fun score(frame: FloatArray, state: FloatArray): Pair<Float, FloatArray> {
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(frame), longArrayOf(1, FRAME_SAMPLES.toLong())).use { input ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(state), longArrayOf(2, 1, STATE_SIZE.toLong())).use { stateTensor ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(AudioCapture.SAMPLE_RATE_HZ.toLong())), longArrayOf()).use { sampleRate ->
                    session.run(mapOf(inputName to input, "state" to stateTensor, "sr" to sampleRate)).use { output ->
                        val outputValue = output.get("output").orElseThrow { error("Silero VAD missing 'output'") }
                        val stateValue = output.get("stateN").orElseThrow { error("Silero VAD missing 'stateN'") }
                        return flattenFirstFloat(outputValue.value) to flatten(stateValue.value)
                    }
                }
            }
        }
    }

    private fun flattenFirstFloat(value: Any): Float = when (value) {
        is Float -> value
        is FloatArray -> value.first()
        is Array<*> -> flattenFirstFloat(requireNotNull(value.firstOrNull()))
        else -> error("Unexpected Silero VAD output type: ${value::class.java.name}")
    }
    private fun flatten(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flatten(requireNotNull(it)).asIterable() }.toFloatArray()
        else -> error("Unexpected Silero VAD state type: ${value::class.java.name}")
    }

    override fun close() {
        _session?.close()
        _session = null
    }

    companion object {
        const val FRAME_SAMPLES = 512
        const val DEFAULT_SPEECH_THRESHOLD = 0.50f
        const val STATE_SIZE = 128

        /** Helper to load the model from the app's assets. */
        fun fromAssets(assets: android.content.res.AssetManager, path: String): OnnxSileroVad {
            val bytes = assets.open(path).use { it.readBytes() }
            return OnnxSileroVad(bytes)
        }
    }
}
