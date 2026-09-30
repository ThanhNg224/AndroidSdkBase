package io.github.thanhng224.sdkbase.otp.internal.engine

import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge
import io.github.thanhng224.sdkbase.otp.gateway.OtpGateway
import io.github.thanhng224.sdkbase.otp.session.OtpState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpTimerTest {

    private class StubGateway(private val challenge: OtpChallenge) : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
            SdkResult.Success(challenge)
        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> =
            SdkResult.Success(Unit)
    }

    @Test
    fun `the resend cooldown counts down in real time`() = runTest {
        val scope =
            SessionScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), SdkLogger.NoOp.tagged("Test"))
        val engine = OtpEngine(
            gateway = StubGateway(OtpChallenge("ch-1", 6, expiresInSeconds = 120, resendAfterSeconds = 3)),
            scope = scope,
        )
        engine.start("0900000000")
        assertEquals(3, engine.state.value.secondsUntilResend)

        advanceTimeBy(3_100)

        assertEquals(0, engine.state.value.secondsUntilResend)
        assertTrue(engine.state.value.canResend)
        scope.close()
    }

    @Test
    fun `reaching expiry fails the session without any host interaction`() = runTest {
        val scope =
            SessionScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), SdkLogger.NoOp.tagged("Test"))
        val engine = OtpEngine(
            gateway = StubGateway(OtpChallenge("ch-1", 6, expiresInSeconds = 2, resendAfterSeconds = 1)),
            scope = scope,
        )
        engine.start("0900000000")

        advanceTimeBy(2_100)

        assertEquals(OtpState.Phase.Failed, engine.state.value.phase)
        assertEquals(OtpErrors.OTP_EXPIRED, engine.state.value.error?.code)
        scope.close()
    }

    // Proves the BEHAVIOUR (no ticking after close), not the mechanism: the ticker shares the
    // session's scope, so scope cancellation is what actually stops it. See SessionScope.close().
    @Test
    fun `close stops the ticker`() = runTest {
        val scope =
            SessionScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), SdkLogger.NoOp.tagged("Test"))
        val engine = OtpEngine(
            gateway = StubGateway(OtpChallenge("ch-1", 6, expiresInSeconds = 120, resendAfterSeconds = 60)),
            scope = scope,
        )
        engine.start("0900000000")
        advanceTimeBy(1_100)
        val afterOneTick = engine.state.value.secondsUntilResend

        scope.close()
        advanceTimeBy(5_000)

        assertEquals(afterOneTick, engine.state.value.secondsUntilResend)
    }
}
