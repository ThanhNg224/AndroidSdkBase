package io.github.thanhng224.sdkbase.otpsdk

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.errorOrNull
import io.github.thanhng224.sdkbase.core.getOrNull
import io.github.thanhng224.sdkbase.core.gateway.OtpChallenge
import io.github.thanhng224.sdkbase.core.gateway.OtpGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OtpSdk.start], [OtpSession.submit] and [OtpSession.resend] are business logic — validation,
 * result mapping, cooldown refusal — not UI, so they get unit tests per the repo's testing rule.
 * These drive real suspend calls (engine effects run to completion before returning; the engine's
 * internal ticker uses the real `AndroidDispatchers`, which is harmless here since no test waits on
 * a tick and every session is closed at the end of its test).
 */
class OtpSdkTest {

    private class FakeGateway(
        private val challenge: OtpChallenge = OtpChallenge("ch-1", 6, 60, 30),
        private val requestResult: () -> SdkResult<OtpChallenge> = { SdkResult.Success(challenge) },
        private val verifyResult: () -> SdkResult<Unit> = { SdkResult.Success(Unit) },
    ) : OtpGateway {
        var lastSubmittedCode: String? = null

        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = requestResult()

        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> {
            lastSubmittedCode = code
            return verifyResult()
        }
    }

    private fun configOf(gateway: OtpGateway): OtpSdkConfig =
        OtpSdkConfig.Builder("0900000000", gateway).build().getOrNull()!!

    @Test
    fun `start surfaces the gateway's own error code, not unknown`() = runTest {
        val gateway = FakeGateway(
            requestResult = { SdkResult.Failure(SdkErrors.gatewayFailure("boom")) },
        )

        val result = OtpSdk.start(configOf(gateway))

        assertTrue(result is SdkResult.Failure)
        assertEquals(SdkErrors.GATEWAY_FAILURE, result.errorOrNull()?.code)
    }

    @Test
    fun `start succeeds when the gateway issues a challenge`() = runTest {
        val gateway = FakeGateway()

        val result = OtpSdk.start(configOf(gateway))

        assertTrue(result is SdkResult.Success)
        result.getOrNull()?.close()
    }

    @Test
    fun `submit maps a verified engine state to success`() = runTest {
        val gateway = FakeGateway()
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!

        val result = session.submit("123456")

        assertTrue(result is SdkResult.Success)
        assertEquals("123456", gateway.lastSubmittedCode)
        session.close()
    }

    @Test
    fun `submit maps a rejected code to failure with the engine's own error`() = runTest {
        val gateway = FakeGateway(verifyResult = { SdkResult.Failure(SdkErrors.otpInvalid()) })
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!

        val result = session.submit("000000")

        assertEquals(SdkErrors.OTP_INVALID, result.errorOrNull()?.code)
        session.close()
    }

    @Test
    fun `resend refuses while the cooldown has not elapsed`() = runTest {
        // The fake challenge's resendAfterSeconds is 30, so the cooldown has not elapsed yet.
        val gateway = FakeGateway()
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!

        val result = session.resend()

        assertTrue(result is SdkResult.Failure)
        assertEquals(SdkErrors.OTP_RESEND_TOO_SOON, result.errorOrNull()?.code)
        session.close()
    }

    @Test
    fun `resend succeeds once the cooldown has elapsed`() = runTest {
        val gateway = FakeGateway(
            challenge = OtpChallenge("ch-1", 6, 60, resendAfterSeconds = 0),
        )
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!

        val result = session.resend()

        assertTrue(result is SdkResult.Success)
        session.close()
    }
}
