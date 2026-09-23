package io.github.thanhng224.sdkbase.core.result

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import org.junit.Assert.assertEquals
import org.junit.Test

class SdkResultTest {

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
