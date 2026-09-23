package io.github.thanhng224.sdkbase.core.logging

import io.github.thanhng224.sdkbase.core.time.Clock

/**
 * The SDK's only logging entry point. Never a global/static instance: a host config carries its
 * own (see `OtpSdkConfig.logger`), and [withSession] returns a new, independent instance scoped to
 * one session id instead of mutating shared state.
 *
 * A record is redacted exactly once, before any sink sees it. A sink that throws is contained by
 * [log]: the throw never escapes and never stops the remaining sinks from receiving the record.
 */
public class SdkLogger private constructor(
    public val minLevel: LogLevel,
    private val sinks: List<LogSink>,
    private val redactor: Redactor,
    private val clock: Clock,
    private val sessionId: String?,
) {

    /** False when there is no sink to receive [level], or [level] is below [minLevel]. */
    public fun isLoggable(level: LogLevel): Boolean = sinks.isNotEmpty() && level >= minLevel

    /** A [TaggedLogger] bound to [tag]; the only way callers write a record. */
    public fun tagged(tag: String): TaggedLogger = TaggedLogger(this, tag)

    /** A new logger, otherwise identical, whose every record is prefixed with [sessionId]. */
    public fun withSession(sessionId: String): SdkLogger =
        SdkLogger(minLevel, sinks, redactor, clock, sessionId)

    /**
     * Redacts, prefixes and dispatches one record to every sink. `internal`: only [TaggedLogger],
     * in this same module, calls it. [message] is evaluated at most once, and only when
     * [isLoggable] is true.
     */
    internal fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String) {
        if (!isLoggable(level)) return
        val redacted = redactor.redact(message())
        val text = sessionId?.let { "session=$it $redacted" } ?: redacted
        val record = LogRecord(level, tag, text, throwable, clock.nowMillis(), sessionId)
        for (sink in sinks) {
            try {
                sink.write(record)
            } catch (e: Exception) {
                // A sink is host-supplied; letting it throw here would let a bad log sink break
                // whatever business logic just tried to log something.
            }
        }
    }

    /** Only [TaggedLogger.trace] uses this, to measure a duration with the logger's own clock. */
    internal fun currentTimeMillis(): Long = clock.nowMillis()

    public class Builder {
        private var minLevel: LogLevel = LogLevel.INFO
        private val sinks = mutableListOf<LogSink>()
        private var redactor: Redactor = DefaultRedactor()
        private var clock: Clock = Clock.System

        public fun minLevel(level: LogLevel): Builder = apply { this.minLevel = level }

        public fun sink(sink: LogSink): Builder = apply { sinks += sink }

        public fun redactor(redactor: Redactor): Builder = apply { this.redactor = redactor }

        public fun clock(clock: Clock): Builder = apply { this.clock = clock }

        public fun build(): SdkLogger = SdkLogger(minLevel, sinks.toList(), redactor, clock, sessionId = null)
    }

    public companion object {
        /** Logs nothing: no sinks, so [isLoggable] is always false and [log] never allocates. */
        public val NoOp: SdkLogger = SdkLogger(
            minLevel = LogLevel.ERROR,
            sinks = emptyList(),
            redactor = Redactor.None,
            clock = Clock.System,
            sessionId = null,
        )
    }
}
