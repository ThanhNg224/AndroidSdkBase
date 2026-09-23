package io.github.thanhng224.sdkbase.core.logging

import android.util.Log

/**
 * Writes [LogRecord]s to Logcat: the one place `android.util.Log` is allowed in `sdk/`. Tags are
 * truncated to Logcat's 23-character limit on older API levels, and messages are chunked so a long
 * one is not silently cut by Logcat's own per-line limit.
 */
public class LogcatSink(private val tagPrefix: String = "SdkBase") : LogSink {

    override fun write(record: LogRecord) {
        val tag = logcatTag(tagPrefix, record.tag)
        val priority = priorityOf(record.level)
        val body = record.throwable?.let { "${record.message}\n${Log.getStackTraceString(it)}" } ?: record.message
        for (chunk in logcatChunks(body)) {
            Log.println(priority, tag, chunk)
        }
    }

    private fun priorityOf(level: LogLevel): Int = when (level) {
        LogLevel.VERBOSE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }
}

/** Logcat truncates tags over this length on older API levels. */
private const val LOGCAT_MAX_TAG_LENGTH = 23

/** Logcat truncates a single log line around this length. */
private const val LOGCAT_MAX_CHUNK_LENGTH = 4_000

/** Pure so it can be tested without touching `android.util.Log`. */
internal fun logcatTag(prefix: String, tag: String, maxLength: Int = LOGCAT_MAX_TAG_LENGTH): String =
    "$prefix/$tag".take(maxLength)

/** Pure so it can be tested without touching `android.util.Log`. Never returns an empty list. */
internal fun logcatChunks(message: String, maxChunkSize: Int = LOGCAT_MAX_CHUNK_LENGTH): List<String> {
    if (message.length <= maxChunkSize) return listOf(message)
    return message.chunked(maxChunkSize)
}
