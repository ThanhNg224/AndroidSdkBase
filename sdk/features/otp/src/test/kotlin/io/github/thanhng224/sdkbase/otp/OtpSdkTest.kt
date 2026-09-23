package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkLogger
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.errorOrNull
import io.github.thanhng224.sdkbase.core.getOrNull
import io.github.thanhng224.sdkbase.core.gateway.TelemetrySink
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
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

    private fun configOf(
        gateway: OtpGateway,
        logger: SdkLogger = SdkLogger.NoOp,
        telemetry: TelemetrySink? = null,
    ): OtpSdkConfig =
        OtpSdkConfig.Builder("0900000000", gateway)
            .logger(logger)
            .telemetry(telemetry)
            .build()
            .getOrNull()!!

    private fun throwingGateway(onRequest: () -> Nothing): OtpGateway = object : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = onRequest()
        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> = SdkResult.Success(Unit)
    }

    @Test
    fun `an IOException from the gateway becomes NETWORK_UNAVAILABLE, never a throw`() = runTest {
        val result = OtpSdk.start(configOf(throwingGateway { throw IOException("socket closed") }))
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, result.errorOrNull()?.code)
    }

    @Test
    fun `any other exception from the gateway becomes GATEWAY_FAILURE`() = runTest {
        val result = OtpSdk.start(configOf(throwingGateway { throw IllegalStateException("boom") }))
        assertEquals(SdkErrors.GATEWAY_FAILURE, result.errorOrNull()?.code)
    }

    @Test
    fun `throwing logger cannot replace gateway failure or prevent fatal state`() = runTest {
        val logger = object : SdkLogger {
            override fun debug(tag: String, message: String): Unit = error("debug sink failed")
            override fun info(tag: String, message: String): Unit = error("info sink failed")
            override fun error(tag: String, message: String, throwable: Throwable?): Unit = error("error sink failed")
        }
        val result = OtpSdk.start(
            configOf(
                FakeGateway(requestResult = { SdkResult.Failure(SdkErrors.networkUnavailable()) }),
                logger = logger,
            ),
        )

        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, result.errorOrNull()?.code)
    }

    @Test
    fun `throwing telemetry cannot fail start or verified submit`() = runTest {
        val events = mutableListOf<String>()
        val telemetry = TelemetrySink { name, _ ->
            events += name
            error("telemetry sink failed")
        }
        val session = OtpSdk.start(configOf(FakeGateway(), telemetry = telemetry)).getOrNull()!!

        val result = session.submit("123456")

        assertTrue(result is SdkResult.Success)
        assertEquals(OtpState.Phase.Verified, session.state.value.phase)
        assertEquals(listOf("otp_started", "otp_verified"), events)
        session.close()
    }

    @Test
    fun `a gateway that never answers times out`() = runTest {
        val hanging = object : OtpGateway {
            override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = awaitCancellation()
            override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> = awaitCancellation()
        }
        val config = OtpSdkConfig.Builder("0900000000", hanging).gatewayTimeoutSeconds(1).build().getOrNull()!!
        assertEquals(SdkErrors.TIMEOUT, OtpSdk.start(config).errorOrNull()?.code)
    }

    @Test
    fun `resend reports a failed resend as a failure`() = runTest {
        var calls = 0
        val gateway = FakeGateway(
            challenge = OtpChallenge("ch-1", 6, 60, 0),
            requestResult = {
                if (calls++ == 0) SdkResult.Success(OtpChallenge("ch-1", 6, 60, 0))
                else SdkResult.Failure(SdkErrors.networkUnavailable())
            },
        )
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, session.resend().errorOrNull()?.code)
        session.close()
    }

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
        val gateway = FakeGateway(verifyResult = { SdkResult.Failure(OtpErrors.otpInvalid()) })
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!

        val result = session.submit("000000")

        assertEquals(OtpErrors.OTP_INVALID, result.errorOrNull()?.code)
        session.close()
    }

    @Test
    fun `resend refuses while the cooldown has not elapsed`() = runTest {
        // The fake challenge's resendAfterSeconds is 30, so the cooldown has not elapsed yet.
        val gateway = FakeGateway()
        val session = OtpSdk.start(configOf(gateway)).getOrNull()!!

        val result = session.resend()

        assertTrue(result is SdkResult.Failure)
        assertEquals(OtpErrors.OTP_RESEND_TOO_SOON, result.errorOrNull()?.code)
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
