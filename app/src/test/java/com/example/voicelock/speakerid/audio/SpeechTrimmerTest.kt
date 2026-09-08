package com.example.voicelock.speakerid.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTrimmerTest {

    @Test
    fun `trim leading and trailing silence`() {
        val sampleRate = 16000
        val frameSamples = 1600 // 100ms
        val source = FloatArray(1600 * 10) { 0.1f }
        // 10 frames total.
        // Let's say frames 2 to 8 are speech (index 2 to 8).
        // Duration = 7 frames * 100ms = 700ms. Still too short for 750ms!
        // Let's use frames 1 to 9. Duration = 9 frames * 100ms = 900ms.
        val speechFrames = BooleanArray(10) { it in 1..9 }

        val result = SpeechTrimmer.trim(source, speechFrames, frameSamples, sampleRate)

        assertTrue("Expected SpeechResult.Speech but got $result", result is SpeechResult.Speech)
        val speech = (result as SpeechResult.Speech).samples
        // start = 1 * 1600 = 1600
        // end = (9 + 1) * 1600 = 16000
        assertEquals(1600 * 9, speech.size)
        assertArrayEquals(source.copyOfRange(1600, 16000), speech, 0.0001f)
    }

    @Test
    fun `reject no speech`() {
        val sampleRate = 16000
        val frameSamples = 100
        val source = FloatArray(1000) { 0.1f }
        val speechFrames = BooleanArray(10) { false }

        val result = SpeechTrimmer.trim(source, speechFrames, frameSamples, sampleRate)

        assertEquals(SpeechResult.NoSpeech, result)
    }

    @Test
    fun `reject too short speech`() {
        val sampleRate = 16000
        val frameSamples = 1600 // 100ms per frame
        val source = FloatArray(1600 * 10)
        // Only 2 frames of speech = 200ms
        val speechFrames = BooleanArray(10) { it in 2..3 }

        val result = SpeechTrimmer.trim(source, speechFrames, frameSamples, sampleRate)

        assertEquals(SpeechResult.TooShort, result)
    }

    @Test
    fun `accept speech longer than 0,75s`() {
        val sampleRate = 16000
        val frameSamples = 1600 // 100ms per frame
        val source = FloatArray(1600 * 10)
        // 8 frames of speech = 800ms (> 750ms)
        val speechFrames = BooleanArray(10) { it in 1..8 }

        val result = SpeechTrimmer.trim(source, speechFrames, frameSamples, sampleRate)

        assertTrue(result is SpeechResult.Speech)
    }
}
