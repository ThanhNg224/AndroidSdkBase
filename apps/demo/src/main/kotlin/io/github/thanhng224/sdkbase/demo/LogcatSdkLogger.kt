package io.github.thanhng224.sdkbase.demo

import android.util.Log
import io.github.thanhng224.sdkbase.core.SdkLogger

/**
 * Logcat sink for [SdkLogger]. Debug logging is OFF unless the host opts in, because a published
 * SDK logging on a release build of someone else's app is a data-leak vector, not a feature.
 */
internal class LogcatSdkLogger(
    private val tagPrefix: String = "SdkBase",
    private val debugEnabled: Boolean = false,
) : SdkLogger {

    override fun debug(tag: String, message: String) {
        if (debugEnabled) Log.d(tagOf(tag), message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tagOf(tag), message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tagOf(tag), message, throwable) else Log.e(tagOf(tag), message)
    }

    // Logcat truncates tags over 23 chars on older API levels.
    private fun tagOf(tag: String): String = "$tagPrefix/$tag".take(23)
}
