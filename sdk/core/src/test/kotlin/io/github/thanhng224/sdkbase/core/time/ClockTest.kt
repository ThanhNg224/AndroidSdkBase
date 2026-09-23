package io.github.thanhng224.sdkbase.core.time

import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {

    @Test
    fun `System clock is monotonic-ish`() {
        val first = Clock.System.nowMillis()
        val second = Clock.System.nowMillis()
        assertTrue(second >= first)
    }
}
