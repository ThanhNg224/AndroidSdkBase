package io.github.thanhng224.sdkbase.core

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
    fun `redact keeps only the requested tail`() {
        assertEquals("*******89", redact("123456789", keepLast = 2))
        assertEquals("*********", redact("123456789", keepLast = 0))
        // Never leak more than the value itself.
        assertEquals("12", redact("12", keepLast = 5))
    }

    @Test
    fun `result accessors do not throw`() {
        val ok: SdkResult<Int> = SdkResult.Success(7)
        val bad: SdkResult<Int> = SdkResult.Failure(SdkErrors.unknown())
        assertEquals(7, ok.getOrNull())
        assertEquals(null, ok.errorOrNull())
        assertEquals(null, bad.getOrNull())
        assertEquals(SdkErrors.UNKNOWN, bad.errorOrNull()?.code)
    }
}
