package io.github.thanhng224.sdkbase.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the tag-truncation and message-chunking logic through the internal pure functions
 * `logcatTag`/`logcatChunks`, never through [LogcatSink] itself: that would call
 * `android.util.Log`, which this module never tests directly.
 */
class LogcatSinkTest {

    @Test
    fun `tag combines prefix and tag when short enough`() {
        assertEquals("SdkBase/Engine", logcatTag("SdkBase", "Engine"))
    }

    @Test
    fun `tag is truncated to 23 characters`() {
        val tag = logcatTag("SdkBase", "AVeryLongTagThatDoesNotFit")
        assertEquals(23, tag.length)
        assertEquals("SdkBase/AVeryLongTagTha", tag)
    }

    @Test
    fun `a short message is a single chunk`() {
        val message = "short message"
        assertEquals(listOf(message), logcatChunks(message))
    }

    @Test
    fun `a message over the chunk size is split without losing or duplicating characters`() {
        val message = "x".repeat(9_001)
        val chunks = logcatChunks(message, maxChunkSize = 4_000)

        assertEquals(3, chunks.size)
        assertEquals(listOf(4_000, 4_000, 1_001), chunks.map { it.length })
        assertEquals(message, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 4_000 })
    }

    @Test
    fun `an empty message yields exactly one empty chunk`() {
        assertEquals(listOf(""), logcatChunks(""))
    }
}
