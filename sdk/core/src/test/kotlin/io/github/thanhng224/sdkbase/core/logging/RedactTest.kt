package io.github.thanhng224.sdkbase.core.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class RedactTest {

    @Test
    fun `redact keeps only the requested tail`() {
        assertEquals("*******89", redact("123456789", keepLast = 2))
        assertEquals("*********", redact("123456789", keepLast = 0))
        // Never leak more than the value itself.
        assertEquals("12", redact("12", keepLast = 5))
    }
}
