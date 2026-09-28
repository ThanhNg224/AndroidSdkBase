package io.github.thanhng224.sdkbase.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkErrorsTest {

    @Test
    fun `every catalog code is unique`() {
        val codes = SdkErrors.all()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `codes are grouped by family range`() {
        // 1xxx common, 2xxx system, 3xxx business, 4xxx lifecycle.
        assertTrue(SdkErrors.invalidConfig("x") is SdkError.Common)
        assertTrue(SdkErrors.networkUnavailable() is SdkError.System)
        assertTrue(SdkErrors.notStarted() is SdkError.Lifecycle)

        assertEquals(1, SdkErrors.invalidConfig("x").code / 1000)
        assertEquals(2, SdkErrors.networkUnavailable().code / 1000)
        assertEquals(4, SdkErrors.notStarted().code / 1000)
    }

    @Test
    fun `core owns no business codes`() {
        assertTrue(SdkErrors.all().none { it / 1000 == 3 })
    }

    @Test
    fun `sessionClosed is 4002 lifecycle`() {
        val error = SdkErrors.sessionClosed()
        assertTrue(error is SdkError.Lifecycle)
        assertEquals(SdkErrors.SESSION_CLOSED, error.code)
        assertEquals(4002, error.code)
    }

    @Test
    fun `all contains 4002`() {
        assertTrue(SdkErrors.all().contains(SdkErrors.SESSION_CLOSED))
    }
}
