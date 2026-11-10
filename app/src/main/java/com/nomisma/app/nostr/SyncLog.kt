package com.nomisma.app.nostr

import android.util.Log

/**
 * Logcat trace of the sync session (`adb logcat -s NomismaSync`). Only event ids, kinds,
 * counts and timings are logged: never contents, amounts or full keys.
 */
internal object SyncLog {
    private const val TAG = "NomismaSync"

    fun d(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    fun w(message: String, failure: Throwable? = null) {
        runCatching { Log.w(TAG, if (failure == null) message else "$message: ${failure.javaClass.simpleName} ${failure.message}") }
    }

    fun shortId(id: String): String = id.take(8)
}
