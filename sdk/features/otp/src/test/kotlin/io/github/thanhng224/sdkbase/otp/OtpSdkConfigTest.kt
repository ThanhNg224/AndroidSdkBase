package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.errorOrNull
import io.github.thanhng224.sdkbase.core.result.getOrNull
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

    private val callbackGateway = object : OtpCallbackGateway {
        override fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>) {
            callback.onSuccess(OtpChallenge("ch", 6, 60, 30))
        }
        override fun verifyOtp(challengeId: String, code: String, callback: CompletionCallback) {
            callback.onSuccess()
        }
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

    @Test
    fun `a config built from a callback gateway validates the same way`() {
        val result = OtpSdkConfig.Builder("0900000000", callbackGateway).build()
        assertNotNull(result.getOrNull())
        assertEquals(3, result.getOrNull()?.maxAttempts)
    }

    @Test
    fun `a callback gateway config still rejects a blank destination`() {
        val result = OtpSdkConfig.Builder("   ", callbackGateway).build()
        assertEquals(SdkErrors.INVALID_CONFIG, result.errorOrNull()?.code)
    }

    @Test
    fun `gateway timeout must be within 1 to 300 seconds`() {
        val gateway = object : OtpGateway {
            override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = error("unused")
            override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> = error("unused")
        }
        assertEquals(30, OtpSdkConfig.Builder("0900", gateway).build().getOrNull()!!.gatewayTimeoutSeconds)
        assertEquals(SdkErrors.INVALID_CONFIG, OtpSdkConfig.Builder("0900", gateway).gatewayTimeoutSeconds(0).build().errorOrNull()?.code)
        assertEquals(SdkErrors.INVALID_CONFIG, OtpSdkConfig.Builder("0900", gateway).gatewayTimeoutSeconds(301).build().errorOrNull()?.code)
    }
}
