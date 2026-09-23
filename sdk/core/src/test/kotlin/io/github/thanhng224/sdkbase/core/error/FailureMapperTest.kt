package io.github.thanhng224.sdkbase.core.error

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureMapperTest {

    @Test
    fun `Default maps IOException to networkUnavailable`() {
        val cause = IOException("no network")
        val error = FailureMapper.Default.map("requestOtp", cause)
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, error.code)
        assertSame(cause, error.cause)
    }

    @Test
    fun `Default maps any other throwable to gatewayFailure carrying the operation`() {
        val cause = IllegalStateException("boom")
        val error = FailureMapper.Default.map("verifyOtp", cause)
        assertEquals(SdkErrors.GATEWAY_FAILURE, error.code)
        assertSame(cause, error.cause)
        assertTrue(error.reason.contains("verifyOtp"))
    }
}
