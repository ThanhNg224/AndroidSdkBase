package io.github.thanhng224.sdkbase.core.logging

import io.github.thanhng224.sdkbase.core.testing.FakeClock
import io.github.thanhng224.sdkbase.core.testing.RecordingLogSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SdkLogger] itself: level gating, redaction ordering, session prefixing and sink containment.
 * [TaggedLogger] gets its own test class for the per-level entry points and `trace`.
 */
class SdkLoggerTest {

    private class ThrowingSink : LogSink {
        override fun write(record: LogRecord): Nothing = error("sink exploded")
    }

    @Test
    fun `not loggable when there is no sink, whatever the level`() {
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).build()
        assertFalse(logger.isLoggable(LogLevel.ERROR))
    }

    @Test
    fun `not loggable below minLevel`() {
        val logger = SdkLogger.Builder().minLevel(LogLevel.WARN).sink(RecordingLogSink()).build()
        assertFalse(logger.isLoggable(LogLevel.INFO))
        assertTrue(logger.isLoggable(LogLevel.WARN))
        assertTrue(logger.isLoggable(LogLevel.ERROR))
    }

    @Test
    fun `the message lambda is never invoked below minLevel`() {
        var invoked = false
        val logger = SdkLogger.Builder().minLevel(LogLevel.ERROR).sink(RecordingLogSink()).build()
        logger.tagged("T").d {
            invoked = true
            "message"
        }
        assertFalse(invoked)
    }

    @Test
    fun `NoOp never invokes the message lambda`() {
        var invoked = false
        SdkLogger.NoOp.tagged("T").e {
            invoked = true
            "message"
        }
        assertFalse(invoked)
    }

    @Test
    fun `redaction runs once before any sink sees the record`() {
        val sink = RecordingLogSink()
        val redactor = Redactor { message -> message.replace("secret", "***") }
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).redactor(redactor).build()

        logger.tagged("T").i { "value=secret" }

        assertEquals(1, sink.records.size)
        assertTrue(sink.records[0].message.contains("***"))
        assertFalse(sink.records[0].message.contains("secret"))
    }

    @Test
    fun `withSession prefixes every subsequent record and leaves the original logger untouched`() {
        val sink = RecordingLogSink()
        val base = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).redactor(Redactor.None).build()
        val scoped = base.withSession("abc-123")

        scoped.tagged("T").i { "hello" }
        base.tagged("T").i { "world" }

        assertEquals(2, sink.records.size)
        assertTrue(sink.records[0].message.startsWith("session=abc-123 "))
        assertEquals("abc-123", sink.records[0].sessionId)
        assertFalse(sink.records[1].message.startsWith("session="))
        assertEquals(null, sink.records[1].sessionId)
    }

    @Test
    fun `a throwing sink is contained and does not stop the remaining sinks`() {
        val second = RecordingLogSink()
        val logger = SdkLogger.Builder()
            .minLevel(LogLevel.VERBOSE)
            .sink(ThrowingSink())
            .sink(second)
            .redactor(Redactor.None)
            .build()

        // Must not throw.
        logger.tagged("T").e(IllegalStateException("boom")) { "boom" }

        assertEquals(1, second.records.size)
        assertEquals("boom", second.records[0].message)
        assertTrue(second.records[0].throwable.toString().contains("boom"))
    }

    @Test
    fun `record carries level, tag, throwable and the clock's timestamp`() {
        val sink = RecordingLogSink()
        val throwable = IllegalStateException("nope")
        val logger = SdkLogger.Builder()
            .minLevel(LogLevel.VERBOSE)
            .sink(sink)
            .redactor(Redactor.None)
            .clock(FakeClock(42_000L))
            .build()

        logger.tagged("Tag1").w(throwable) { "careful" }

        val record = sink.records.single()
        assertEquals(LogLevel.WARN, record.level)
        assertEquals("Tag1", record.tag)
        assertEquals("careful", record.message)
        assertNotSame(throwable, record.throwable)
        assertEquals(throwable.toString(), record.throwable.toString())
        assertEquals(42_000L, record.timestampMillis)
    }

    @Test
    fun `throwable message cause suppressed and stack text are redacted in detached snapshot`() {
        val sink = RecordingLogSink()
        val cause = IllegalArgumentException("cause CANARY")
        val original = IllegalStateException("message CANARY", cause).apply {
            stackTrace = arrayOf(StackTraceElement("pkg.CANARY", "methodCANARY", "fileCANARY.kt", 27))
            addSuppressed(IllegalArgumentException("suppressed CANARY"))
        }
        val logger = SdkLogger.Builder()
            .minLevel(LogLevel.VERBOSE)
            .sink(sink)
            .redactor(Redactor { it.replace("CANARY", "[redacted]") })
            .build()

        logger.tagged("T").e(original) { "request CANARY" }

        val record = sink.records.single()
        val snapshot = record.throwable!!
        assertNotSame(original, snapshot)
        assertNotSame(cause, snapshot.cause)
        assertNotSame(original.suppressed.single(), snapshot.suppressed.single())
        assertTrue(record.message.contains("[redacted]"))
        assertFalse(record.message.contains("CANARY"))
        val rendered = java.io.StringWriter().also { snapshot.printStackTrace(java.io.PrintWriter(it)) }.toString()
        assertTrue(rendered.contains("message [redacted]"))
        assertTrue(rendered.contains("cause [redacted]"))
        assertTrue(rendered.contains("suppressed [redacted]"))
        assertTrue(rendered.contains("pkg.[redacted].method[redacted](file[redacted].kt:27)"))
        assertFalse(rendered.contains("CANARY"))
        assertEquals("message CANARY", original.message)
        assertEquals("cause CANARY", cause.message)
        assertEquals("pkg.CANARY", original.stackTrace.single().className)
        assertEquals("suppressed CANARY", original.suppressed.single().message)
    }

    @Test
    fun `throwable graph cycles are represented by a marker`() {
        class CyclingException : Exception("cycle CANARY") {
            var next: Throwable? = null
            override val cause: Throwable?
                get() = next
        }

        val first = CyclingException()
        val second = CyclingException()
        first.next = second
        second.next = first
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().sink(sink)
            .redactor(Redactor { it.replace("CANARY", "[redacted]") }).build()

        logger.tagged("T").e(first) { "failed" }

        val snapshot = sink.records.single().throwable!!
        val cycleMarker = snapshot.cause!!.cause!!
        assertEquals(0, cycleMarker.stackTrace.size)
        val rendered = java.io.StringWriter().also { snapshot.printStackTrace(java.io.PrintWriter(it)) }
            .toString()
        assertTrue(rendered.contains("<cycle>"))
        assertTrue(rendered.contains("cycle [redacted]"))
        assertFalse(rendered.contains("CANARY"))
    }

    @Test
    fun `very deep cause chain is truncated instead of overflowing the stack`() {
        var chain: Throwable = IllegalStateException("leaf")
        repeat(5_000) { chain = IllegalStateException("level $it", chain) }
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().sink(sink).build()

        logger.tagged("T").e(chain) { "deep" }

        val rendered = java.io.StringWriter().also {
            sink.records.single().throwable!!.printStackTrace(java.io.PrintWriter(it))
        }.toString()
        assertTrue(rendered.contains("<truncated>"))
    }

    @Test
    fun `same exception in two suppressed branches is not reported as a cycle`() {
        val shared = IllegalArgumentException("shared")
        val original = IllegalStateException("root").apply {
            addSuppressed(RuntimeException("a", shared))
            addSuppressed(RuntimeException("b", shared))
        }
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().sink(sink).build()

        logger.tagged("T").e(original) { "diamond" }

        val snapshot = sink.records.single().throwable!!
        assertTrue(snapshot.suppressed.all { it.cause.toString().contains("shared") })
        assertFalse(snapshot.suppressed.any { it.cause.toString().contains("<cycle>") })
    }

    @Test
    fun `throwable snapshot getter failure keeps only the redacted message`() {
        val sink = RecordingLogSink()
        val hostile = object : Exception() {
            override val message: String?
                get() = throw IllegalStateException("hostile getter")
        }
        val logger = SdkLogger.Builder().sink(sink)
            .redactor(Redactor { it.replace("CANARY", "[redacted]") }).build()

        logger.tagged("T").e(hostile) { "message CANARY" }

        val record = sink.records.single()
        assertEquals("message [redacted]", record.message)
        assertNull(record.throwable)
    }

    @Test
    fun `redactor failure while processing message drops record without escaping`() {
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().sink(sink)
            .redactor(Redactor { throw IllegalArgumentException("redactor failed") }).build()

        logger.tagged("T").e { "raw CANARY" }

        assertTrue(sink.records.isEmpty())
    }

    @Test
    fun `redactor failure while processing throwable drops record without escaping`() {
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().sink(sink)
            .redactor(
                Redactor { value ->
                    if (value.contains("THROWABLE_CANARY")) error("redactor failed")
                    value
                },
            ).build()

        logger.tagged("T").e(IllegalStateException("THROWABLE_CANARY")) { "safe message" }

        assertTrue(sink.records.isEmpty())
    }

    @Test
    fun `Redactor None intentionally preserves throwable text while detaching source`() {
        val sink = RecordingLogSink()
        val original = IllegalStateException("intentional CANARY")
        val logger = SdkLogger.Builder().sink(sink).redactor(Redactor.None).build()

        logger.tagged("T").e(original) { "intentional CANARY" }

        val record = sink.records.single()
        assertEquals("intentional CANARY", record.message)
        assertNotSame(original, record.throwable)
        assertTrue(record.throwable.toString().contains("intentional CANARY"))
    }
}
