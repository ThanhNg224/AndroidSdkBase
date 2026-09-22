package io.github.thanhng224.sdkbase.otpsdk

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.errorOrNull
import io.github.thanhng224.sdkbase.core.gateway.OtpChallenge
import io.github.thanhng224.sdkbase.core.gateway.OtpGateway
import io.github.thanhng224.sdkbase.core.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpSdkConfigTest {

    private val gateway = object : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
            SdkResult.Success(OtpChallenge("ch", 6, 60, 30))
        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> =
            SdkResult.Success(Unit)
    }

    @Test
    fun `a valid config builds`() {
        val result = OtpSdkConfig.Builder("0900000000", gateway).build()
        assertNotNull(result.getOrNull())
        assertEquals(3, result.getOrNull()?.maxAttempts)
    }

    @Test
    fun `a blank destination is rejected at the boundary`() {
        val result = OtpSdkConfig.Builder("   ", gateway).build()
        assertEquals(SdkErrors.INVALID_CONFIG, result.errorOrNull()?.code)
        assertTrue(result.errorOrNull()?.reason?.contains("destination") == true)
    }

    @Test
    fun `maxAttempts must be positive`() {
        val result = OtpSdkConfig.Builder("0900000000", gateway).maxAttempts(0).build()
        assertEquals(SdkErrors.INVALID_CONFIG, result.errorOrNull()?.code)
        assertTrue(result.errorOrNull()?.reason?.contains("maxAttempts") == true)
    }

    @Test
    fun `an absurd maxAttempts is rejected rather than silently clamped`() {
        val result = OtpSdkConfig.Builder("0900000000", gateway).maxAttempts(1000).build()
        assertEquals(SdkErrors.INVALID_CONFIG, result.errorOrNull()?.code)
    }

    @Test
    fun `telemetry is optional`() {
        val result = OtpSdkConfig.Builder("0900000000", gateway).telemetry(null).build()
        assertNotNull(result.getOrNull())
        assertEquals(null, result.getOrNull()?.telemetry)
    }
}
