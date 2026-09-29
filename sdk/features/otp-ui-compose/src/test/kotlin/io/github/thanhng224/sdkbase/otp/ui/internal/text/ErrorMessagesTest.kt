package io.github.thanhng224.sdkbase.otp.ui.internal.text

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    private fun res(code: Int) = errorMessageRes(SdkError.Business(code, "diagnostic"))

    @Test
    fun everyOtpErrorMapsToADistinctNonGenericString() {
        val resources = OtpErrors.all().map(::res)
        assertEquals(resources.size, resources.toSet().size)
        assertTrue(resources.none { it == R.string.sdk_otp_ui_compose_error_generic })
    }

    @Test
    fun networkAndTimeoutMapToTheirStrings() {
        assertEquals(R.string.sdk_otp_ui_compose_error_network, errorMessageRes(SdkErrors.networkUnavailable()))
        assertEquals(R.string.sdk_otp_ui_compose_error_timeout, errorMessageRes(SdkErrors.timeout("x")))
    }

    @Test
    fun everythingElseIsGeneric() {
        listOf(
            SdkErrors.gatewayFailure("x"),
            SdkErrors.unknown(),
            SdkErrors.sessionClosed(),
            SdkError.Business(3999, "unrelated"),
        ).forEach { assertEquals(R.string.sdk_otp_ui_compose_error_generic, errorMessageRes(it)) }
        assertNotEquals(R.string.sdk_otp_ui_compose_error_generic, res(OtpErrors.OTP_INVALID))
    }
}
