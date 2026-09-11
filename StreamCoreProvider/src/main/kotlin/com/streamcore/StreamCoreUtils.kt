package com.streamcore

import android.util.Log

/** Shared logging tag + helper so failures are visible in logcat instead of silently swallowed. */
internal const val LOG_TAG = "StreamCore"

internal fun logFailure(context: String, error: Throwable) {
    Log.e(LOG_TAG, "$context failed: ${error.message}")
}

/** Shared quality-string -> numeric quality parsing, used by every extractor. */
internal object QualityUtils {
    fun parseQuality(quality: String?): Int {
        val q = quality?.lowercase()?.trim() ?: return 1080
        return when {
            q.contains("2160") || q.contains("4k") -> 2160
            q.contains("1080") -> 1080
            q.contains("720") -> 720
            q.contains("480") -> 480
            q.contains("360") -> 360
            else -> 1080
        }
    }
}
