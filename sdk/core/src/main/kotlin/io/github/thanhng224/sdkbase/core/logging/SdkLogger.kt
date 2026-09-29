package io.github.thanhng224.sdkbase.core.logging

import io.github.thanhng224.sdkbase.core.time.Clock

/**
 * The SDK's only logging entry point. Never a global/static instance: a host config carries its
 * own (see `OtpSdkConfig.logger`), and [withSession] returns a new, independent instance scoped to
 * one session id instead of mutating shared state.
 *
 * The message and detached Throwable snapshot are redacted before any sink sees them. A sink that
 * throws is contained by [log]: the throw never escapes and never stops the remaining sinks from
 * receiving the record.
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
     * [isLoggable] is true. A message redactor failure drops the record; a snapshot failure keeps
     * the redacted message and drops only its Throwable.
     */
    internal fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String) {
        if (!isLoggable(level)) return
        val rawMessage = message()
        val redacted = try {
            redactText(rawMessage)
        } catch (_: RedactorFailure) {
            return
        }
        val text = sessionId?.let { "session=$it $redacted" } ?: redacted
        val safeThrowable = try {
            throwable?.let { snapshotThrowable(it, ::redactText) }
        } catch (_: RedactorFailure) {
            return
        } catch (_: Exception) {
            // A hostile or broken Throwable must not prevent the already-redacted message from
            // reaching the sinks, and must never be forwarded in its original form.
            null
        }
        val record = LogRecord(level, tag, text, safeThrowable, clock.nowMillis(), sessionId)
        for (sink in sinks) {
            try {
                sink.write(record)
            } catch (e: Exception) {
                // A sink is host-supplied; letting it throw here would let a bad log sink break
                // whatever business logic just tried to log something.
            }
        }
    }

    private fun redactText(value: String): String = try {
        redactor.redact(value)
    } catch (_: Exception) {
        throw RedactorFailure()
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

/** Distinguishes a redactor failure from an exception raised while reading a host Throwable. */
private class RedactorFailure : RuntimeException()
