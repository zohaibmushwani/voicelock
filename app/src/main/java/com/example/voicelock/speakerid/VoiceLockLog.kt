package com.example.voicelock.speakerid

import android.util.Log

/**
 * One stable logcat tag for the complete voice pipeline.
 * Never log PCM samples, embeddings, or stored biometric templates here.
 */
object VoiceLockLog {
    const val TAG = "VoiceLock"

    fun info(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    fun warn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    fun error(message: String, cause: Throwable? = null) {
        runCatching { Log.e(TAG, message, cause) }
    }
}
