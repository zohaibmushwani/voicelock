package com.example.voicelock.speakerid.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Encodes audio FBank frames into the model's 192-dimensional speaker embedding.
 * Uses the ECAPA-TDNN model (voxceleb_ECAPA512_LM.onnx).
 */
class SpeakerEncoder(private val modelFile: File) : AutoCloseable {
    private var _env: OrtEnvironment? = null
    private val env: OrtEnvironment
        get() = _env ?: OrtEnvironment.getEnvironment().also { _env = it }

    private var _session: OrtSession? = null
    private val session: OrtSession
        get() = _session ?: createSession().also { _session = it }

    private fun createSession(): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            // Configure NNAPI for hardware acceleration
            addNnapi()
            // Add XNNPACK as a fallback/optimized CPU provider
            addXnnpack(mapOf("intra_op_num_threads" to "2"))
            
            setInterOpNumThreads(1)
            setIntraOpNumThreads(2)
        }
        return env.createSession(modelFile.absolutePath, options)
    }

    constructor(context: Context) : this(ModelAssetInstaller.embeddingModel(context))

    /**
     * Extracts a 192-dimensional embedding from FBank frames.
     * @param fbank Audio FBank frames of shape (frames, 80).
     * @return L2-normalized 512-dim embedding vector.
     */
    fun extractEmbedding(fbank: FloatArray): FloatArray {
        require(fbank.size % 80 == 0) { "FBank input must have 80 bins per frame" }
        val numFrames = fbank.size / 80
        
        // Input shape: (batch_size=1, time_steps=numFrames, num_bins=80)
        val inputShape = longArrayOf(1, numFrames.toLong(), 80)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(fbank), inputShape)
        
        return tensor.use {
            val output = session.run(mapOf(session.inputNames.iterator().next() to it))
            output.use { results ->
                val rawEmbedding = flatten(results[0].value)
                normalize(rawEmbedding)
            }
        }
    }

    private fun normalize(embedding: FloatArray): FloatArray {
        var sumSq = 0f
        for (v in embedding) sumSq += v * v
        val norm = sqrt(sumSq.toDouble()).toFloat()
        val eps = 1e-12f
        val factor = if (norm > eps) 1f / norm else 0f
        return FloatArray(embedding.size) { embedding[it] * factor }
    }

    private fun flatten(value: Any): FloatArray {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> {
                // Handle nested arrays if the model returns [1, 512] or similar
                if (value.isEmpty()) return floatArrayOf()
                val first = value[0]
                if (first is FloatArray) return first
                if (first is Array<*>) return flatten(first)
                throw IllegalArgumentException("Unexpected output type in ORT result")
            }
            else -> throw IllegalArgumentException("Unexpected output type: ${value::class.java}")
        }
    }

    override fun close() {
        _session?.close()
        _session = null
        // env is often a singleton, but if we opened it, we should close it if ORT requires it.
        // In some versions, env.close() is needed.
    }
}
