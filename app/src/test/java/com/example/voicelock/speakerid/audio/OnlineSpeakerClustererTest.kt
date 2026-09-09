package com.example.voicelock.speakerid.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineSpeakerClustererTest {
    @Test
    fun `nearby normalized vectors keep one speaker label`() {
        val clusterer = OnlineSpeakerClusterer(threshold = 0.8f)

        assertEquals(1, clusterer.assign(floatArrayOf(1f, 0f)))
        assertEquals(1, clusterer.assign(floatArrayOf(0.98f, 0.2f)))
        assertEquals(1, clusterer.clusterCount)
    }

    @Test
    fun `distant vectors create deterministic speaker labels`() {
        val clusterer = OnlineSpeakerClusterer(threshold = 0.8f)

        assertEquals(1, clusterer.assign(floatArrayOf(1f, 0f, 0f)))
        assertEquals(2, clusterer.assign(floatArrayOf(0f, 1f, 0f)))
        assertEquals(3, clusterer.assign(floatArrayOf(0f, 0f, 1f)))
    }

    @Test
    fun `invalid zero vector is rejected`() {
        assertNull(OnlineSpeakerClusterer(threshold = 0.7f).assign(floatArrayOf(0f, 0f)))
    }

    @Test
    fun `cluster count is bounded and overflow joins closest existing cluster`() {
        val clusterer = OnlineSpeakerClusterer(threshold = 0.99f, maxSpeakers = 2)
        assertEquals(1, clusterer.assign(floatArrayOf(1f, 0f)))
        assertEquals(2, clusterer.assign(floatArrayOf(0f, 1f)))

        assertEquals(1, clusterer.assign(floatArrayOf(0.8f, 0.2f)))
        assertEquals(2, clusterer.clusterCount)
    }
}
