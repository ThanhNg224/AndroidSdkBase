package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.logging.LogLevel
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingLogSinkTest {

    @Test
    fun `filters by level`() {
        val sink = RecordingLogSink()
        // A real SdkLogger, not a hand-built LogRecord, so the redaction path is exercised too.
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).build()
        val tagged = logger.tagged("T")

        tagged.i { "hello" }
        tagged.e { "boom" }

        assertEquals(listOf("hello"), sink.messages(LogLevel.INFO))
        assertEquals(listOf("boom"), sink.messages(LogLevel.ERROR))
        assertEquals(listOf("hello", "boom"), sink.messages())
        assertEquals(2, sink.records.size)
    }

    @Test
    fun `clear empties`() {
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).build()
        logger.tagged("T").i { "hello" }

        sink.clear()

        assertTrue(sink.records.isEmpty())
        assertTrue(sink.messages().isEmpty())
    }
}
