package com.example.voicelock.speakerid

class FBankExtractor {
    companion object {
        init {
            System.loadLibrary("voicelock-native")
        }
    }

    /**
     * Extracts 80-bin FBank features from PCM audio.
     * 
     * Parameters used:
     * - Sample rate: 16kHz
     * - Window length: 25ms
     * - Frame shift (hop): 10ms
     * - Filter bins: 80
     * - Input scale: normalized PCM multiplied by 32768 to match WeSpeaker inference
     * - Window: Hamming
     * - Dither: disabled for deterministic inference
     * - CMN: Per-utterance mean subtraction applied.
     * 
     * @param pcmAudio The raw PCM audio samples (float array).
     * @return Flattened FBank frames (num_frames * 80).
     */
    external fun computeFBank(pcmAudio: FloatArray): FloatArray
}
