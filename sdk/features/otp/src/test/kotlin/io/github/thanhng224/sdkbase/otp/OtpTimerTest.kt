package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.otp.internal.OtpEngine
import kotlinx.coroutines.CoroutineDispatcher
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

    private fun dispatchers(dispatcher: CoroutineDispatcher) = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
    }

    @Test
    fun `the resend cooldown counts down in real time`() = runTest {
        val engine = OtpEngine(
            gateway = StubGateway(OtpChallenge("ch-1", 6, expiresInSeconds = 120, resendAfterSeconds = 3)),
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
        )
        engine.start("0900000000")
        assertEquals(3, engine.state.value.secondsUntilResend)

        advanceTimeBy(3_100)

        assertEquals(0, engine.state.value.secondsUntilResend)
        assertTrue(engine.state.value.canResend)
        engine.close()
    }

    @Test
    fun `reaching expiry fails the session without any host interaction`() = runTest {
        val engine = OtpEngine(
            gateway = StubGateway(OtpChallenge("ch-1", 6, expiresInSeconds = 2, resendAfterSeconds = 1)),
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
        )
        engine.start("0900000000")

        advanceTimeBy(2_100)

        assertEquals(OtpState.Phase.Failed, engine.state.value.phase)
        assertEquals(OtpErrors.OTP_EXPIRED, engine.state.value.error?.code)
        engine.close()
    }

    @Test
    // Proves the BEHAVIOUR (no ticking after close), not the mechanism: the ticker shares the
    // engine's scope, so scope cancellation is what actually stops it. See OtpEngine.close().
    fun `close stops the ticker`() = runTest {
        val engine = OtpEngine(
            gateway = StubGateway(OtpChallenge("ch-1", 6, expiresInSeconds = 120, resendAfterSeconds = 60)),
            dispatchers = dispatchers(StandardTestDispatcher(testScheduler)),
        )
        engine.start("0900000000")
        advanceTimeBy(1_100)
        val afterOneTick = engine.state.value.secondsUntilResend

        engine.close()
        advanceTimeBy(5_000)

        assertEquals(afterOneTick, engine.state.value.secondsUntilResend)
    }
}
