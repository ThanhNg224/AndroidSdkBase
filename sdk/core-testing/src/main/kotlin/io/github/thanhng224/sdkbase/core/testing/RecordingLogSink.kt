package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.logging.LogLevel
import io.github.thanhng224.sdkbase.core.logging.LogRecord
import io.github.thanhng224.sdkbase.core.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [LogSink] that keeps every [LogRecord] it receives, so a test can assert on what a logger
 * emitted instead of parsing Logcat. Thread-safe.
 */
public class RecordingLogSink : LogSink {

    private val backing = CopyOnWriteArrayList<LogRecord>()

    /** A snapshot of every record received so far, oldest first. */
    public val records: List<LogRecord>
        get() = backing.toList()

    override fun write(record: LogRecord) {
        backing += record
    }

    /** [LogRecord.message] for every record received so far, oldest first. */
    public fun messages(): List<String> = backing.map { it.message }

    /** [LogRecord.message] for records at exactly [level], oldest first. */
    public fun messages(level: LogLevel): List<String> =
        backing.filter { it.level == level }.map { it.message }

    /** Discards every record received so far. */
    public fun clear() {
        backing.clear()
    }
}
