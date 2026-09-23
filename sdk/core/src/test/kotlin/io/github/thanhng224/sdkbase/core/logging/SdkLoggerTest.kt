package io.github.thanhng224.sdkbase.core.logging

import io.github.thanhng224.sdkbase.core.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SdkLogger] itself: level gating, redaction ordering, session prefixing and sink containment.
 * [TaggedLogger] gets its own test class for the per-level entry points and `trace`.
 */
class SdkLoggerTest {

    private class RecordingSink : LogSink {
        val records = mutableListOf<LogRecord>()
        override fun write(record: LogRecord) {
            records += record
        }
    }

    private class ThrowingSink : LogSink {
        override fun write(record: LogRecord): Nothing = error("sink exploded")
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun nowMillis(): Long = millis
    }

    @Test
    fun `not loggable when there is no sink, whatever the level`() {
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).build()
        assertFalse(logger.isLoggable(LogLevel.ERROR))
    }

    @Test
    fun `not loggable below minLevel`() {
        val logger = SdkLogger.Builder().minLevel(LogLevel.WARN).sink(RecordingSink()).build()
        assertFalse(logger.isLoggable(LogLevel.INFO))
        assertTrue(logger.isLoggable(LogLevel.WARN))
        assertTrue(logger.isLoggable(LogLevel.ERROR))
    }

    @Test
    fun `the message lambda is never invoked below minLevel`() {
        var invoked = false
        val logger = SdkLogger.Builder().minLevel(LogLevel.ERROR).sink(RecordingSink()).build()
        logger.tagged("T").d { invoked = true; "message" }
        assertFalse(invoked)
    }

    @Test
    fun `NoOp never invokes the message lambda`() {
        var invoked = false
        SdkLogger.NoOp.tagged("T").e { invoked = true; "message" }
        assertFalse(invoked)
    }

    @Test
    fun `redaction runs once before any sink sees the record`() {
        val sink = RecordingSink()
        val redactor = Redactor { message -> message.replace("secret", "***") }
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).redactor(redactor).build()

        logger.tagged("T").i { "value=secret" }

        assertEquals(1, sink.records.size)
        assertTrue(sink.records[0].message.contains("***"))
        assertFalse(sink.records[0].message.contains("secret"))
    }

    @Test
    fun `withSession prefixes every subsequent record and leaves the original logger untouched`() {
        val sink = RecordingSink()
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
        val second = RecordingSink()
        val logger = SdkLogger.Builder()
            .minLevel(LogLevel.VERBOSE)
            .sink(ThrowingSink())
            .sink(second)
            .redactor(Redactor.None)
            .build()

        // Must not throw.
        logger.tagged("T").e { "boom" }

        assertEquals(1, second.records.size)
        assertEquals("boom", second.records[0].message)
    }

    @Test
    fun `record carries level, tag, throwable and the clock's timestamp`() {
        val sink = RecordingSink()
        val throwable = IllegalStateException("nope")
        val logger = SdkLogger.Builder()
            .minLevel(LogLevel.VERBOSE)
            .sink(sink)
            .redactor(Redactor.None)
            .clock(FixedClock(42_000L))
            .build()

        logger.tagged("Tag1").w(throwable) { "careful" }

        val record = sink.records.single()
        assertEquals(LogLevel.WARN, record.level)
        assertEquals("Tag1", record.tag)
        assertEquals("careful", record.message)
        assertEquals(throwable, record.throwable)
        assertEquals(42_000L, record.timestampMillis)
    }
}
