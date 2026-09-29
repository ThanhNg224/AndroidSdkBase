package io.github.thanhng224.sdkbase.otp.ui.internal.text

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.ui.error.sdkErrorMessageRes
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
        val generic = sdkErrorMessageRes(SdkErrors.unknown())
        assertTrue(resources.none { it == generic })
    }

    @Test
    fun everythingElseUsesTheSharedSdkText() {
        listOf(
            SdkErrors.networkUnavailable(),
            SdkErrors.timeout("x"),
            SdkErrors.gatewayFailure("x"),
            SdkErrors.unknown(),
            SdkErrors.sessionClosed(),
            SdkError.Business(3999, "unrelated"),
        ).forEach { assertEquals(sdkErrorMessageRes(it), errorMessageRes(it)) }
        assertNotEquals(sdkErrorMessageRes(SdkErrors.unknown()), res(OtpErrors.OTP_INVALID))
    }
}
