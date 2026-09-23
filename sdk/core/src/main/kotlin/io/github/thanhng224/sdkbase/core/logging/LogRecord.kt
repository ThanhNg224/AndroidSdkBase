package io.github.thanhng224.sdkbase.core.logging

/**
 * One already-redacted log entry, handed to every [LogSink]. A plain class, not a `data class`:
 * fields can be added later without breaking binary compatibility (a `data class` bakes its
 * property list into `equals`/`hashCode`/`copy`/`componentN`, all of which are ABI).
 */
public class LogRecord(
    public val level: LogLevel,
    public val tag: String,
    public val message: String,
    public val throwable: Throwable?,
    public val timestampMillis: Long,
    public val sessionId: String?,
)
