package io.github.thanhng224.sdkbase.core.logging

import io.github.thanhng224.sdkbase.core.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaggedLoggerTest {

    private class RecordingSink : LogSink {
        val records = mutableListOf<LogRecord>()
        override fun write(record: LogRecord) {
            records += record
        }
    }

    /** Advances by a fixed step every call, so start/end reads in `trace` are deterministic. */
    private class StepClock(private val stepMillis: Long) : Clock {
        private var current = 0L
        override fun nowMillis(): Long {
            val value = current
            current += stepMillis
            return value
        }
    }

    private fun loggerWith(sink: LogSink, clock: Clock = Clock.System): SdkLogger =
        SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).redactor(Redactor.None).clock(clock).build()

    @Test
    fun `v d i w e each log at their own level with the bound tag`() {
        val sink = RecordingSink()
        val tagged = loggerWith(sink).tagged("MyTag")
        val throwable = RuntimeException("x")

        tagged.v { "verbose" }
        tagged.d { "debug" }
        tagged.i { "info" }
        tagged.w(throwable) { "warn" }
        tagged.e(throwable) { "error" }

        assertEquals(
            listOf(LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            sink.records.map { it.level },
        )
        assertTrue(sink.records.all { it.tag == "MyTag" })
        assertEquals(throwable, sink.records[3].throwable)
        assertEquals(throwable, sink.records[4].throwable)
    }

    @Test
    fun `trace logs the duration at VERBOSE on success using the injected clock`() {
        val sink = RecordingSink()
        val tagged = loggerWith(sink, StepClock(stepMillis = 7)).tagged("Op")

        val result = tagged.trace("doWork") { "value" }

        assertEquals("value", result)
        val record = sink.records.single()
        assertEquals(LogLevel.VERBOSE, record.level)
        assertEquals("op=doWork duration_ms=7", record.message)
        assertEquals(null, record.throwable)
    }

    @Test
    fun `trace logs ERROR with the throwable and rethrows`() {
        val sink = RecordingSink()
        val tagged = loggerWith(sink, StepClock(stepMillis = 3)).tagged("Op")
        val boom = IllegalStateException("boom")

        try {
            tagged.trace<Unit>("doWork") { throw boom }
            fail("expected the original throwable to propagate")
        } catch (caught: IllegalStateException) {
            assertEquals(boom, caught)
        }

        val record = sink.records.single()
        assertEquals(LogLevel.ERROR, record.level)
        assertEquals("op=doWork duration_ms=3", record.message)
        assertEquals(boom, record.throwable)
    }

    @Test
    fun `trace runs the block even when the logger cannot log anything`() {
        var ran = false
        val result = SdkLogger.NoOp.tagged("Op").trace("doWork") {
            ran = true
            "done"
        }
        assertTrue(ran)
        assertEquals("done", result)
    }
}
