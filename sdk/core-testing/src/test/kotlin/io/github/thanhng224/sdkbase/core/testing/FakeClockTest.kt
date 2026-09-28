package io.github.thanhng224.sdkbase.core.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeClockTest {

    @Test
    fun `advanceBy moves now`() {
        val clock = FakeClock(1_000L)

        clock.advanceBy(250L)

        assertEquals(1_250L, clock.nowMillis())
    }

    @Test
    fun `set overrides now`() {
        val clock = FakeClock(1_000L)

        clock.set(5_000L)

        assertEquals(5_000L, clock.nowMillis())
    }

    @Test
    fun `the no-arg constructor starts at zero`() {
        assertEquals(0L, FakeClock().nowMillis())
    }
}
