package com.example.voicelock.speakerid.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes a user-selected or bundled mono 16 kHz audio fixture to in-memory PCM floats. */
class DiarizationAudioDecoder(private val context: Context) {
    suspend fun decodeUri(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        decode { extractor -> extractor.setDataSource(context, uri, null) }
    }

    suspend fun decodeAsset(assetName: String): FloatArray = withContext(Dispatchers.IO) {
        val cached = File(context.cacheDir, "diarization-$assetName")
        if (!cached.exists()) context.assets.open("diarization_samples/$assetName").use { input -> cached.outputStream().use(input::copyTo) }
        decode { extractor -> extractor.setDataSource(cached.absolutePath) }
    }

    private fun decode(configure: (MediaExtractor) -> Unit): FloatArray {
        val extractor = MediaExtractor()
        try {
            configure(extractor)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found")
            val format = extractor.getTrackFormat(track)
            require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == AudioCapture.SAMPLE_RATE_HZ) { "Audio must be 16 kHz" }
            require(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) { "Audio must be mono" }
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(track)

            val codec = MediaCodec.createDecoderByType(mime)
            var started = false
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                started = true
                val output = ArrayList<Float>()
                val bufferInfo = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
                while (!outputEnded) {
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val input = requireNotNull(codec.getInputBuffer(inputIndex))
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            require(outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) == AudioCapture.SAMPLE_RATE_HZ) { "Decoded audio must be 16 kHz" }
                            require(outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) { "Decoded audio must be mono" }
                            pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            if (bufferInfo.size > 0) {
                                val buffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                                buffer.position(bufferInfo.offset)
                                buffer.limit(bufferInfo.offset + bufferInfo.size)
                                output.addPcm(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), pcmEncoding)
                            }
                            outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
                return output.toFloatArray()
            } finally {
                if (started) codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun MutableList<Float>.addPcm(buffer: ByteBuffer, encoding: Int) {
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> while (buffer.remaining() >= Float.SIZE_BYTES) add(buffer.float.coerceIn(-1f, 1f))
            AudioFormat.ENCODING_PCM_16BIT -> while (buffer.remaining() >= Short.SIZE_BYTES) add(buffer.short / Short.MAX_VALUE.toFloat())
            else -> error("Unsupported decoded PCM encoding")
        }
    }

    private companion object {
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
