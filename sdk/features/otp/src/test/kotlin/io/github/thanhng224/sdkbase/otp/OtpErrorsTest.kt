package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.SdkError
import io.github.thanhng224.sdkbase.core.SdkErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpErrorsTest {
    @Test
    fun `codes are unique, in the 3xxx family, and disjoint from core`() {
        val codes = OtpErrors.all()
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it / 1000 == 3 })
        assertTrue(codes.none { it in SdkErrors.all() })
    }

    @Test
    fun `errors are business errors`() {
        assertTrue(OtpErrors.otpInvalid() is SdkError.Business)
    }

    @Test
    fun `resend error carries the retry delay in its reason`() {
        val error = OtpErrors.otpResendTooSoon(retryAfterSeconds = 30)
        assertEquals(OtpErrors.OTP_RESEND_TOO_SOON, error.code)
        assertTrue(error.reason.contains("30"))
    }
}
