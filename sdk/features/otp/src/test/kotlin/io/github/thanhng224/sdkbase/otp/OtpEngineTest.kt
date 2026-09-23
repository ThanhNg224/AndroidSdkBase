package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.DispatcherProvider
import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.internal.OtpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpEngineTest {

    private class FakeGateway(
        private val challenge: OtpChallenge = OtpChallenge("ch-1", 6, 60, 30),
        private val verifyResult: () -> SdkResult<Unit> = { SdkResult.Success(Unit) },
    ) : OtpGateway {
        var requestCount: Int = 0
        var lastSubmittedCode: String? = null

        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> {
            requestCount++
            return SdkResult.Success(challenge)
        }

        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> {
            lastSubmittedCode = code
            return verifyResult()
        }
    }

    private fun dispatchers(dispatcher: CoroutineDispatcher) = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
    }

    @Test
    fun `start requests a challenge and moves to awaiting code`() = runTest {
        val gateway = FakeGateway()
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)))

        engine.start("0900000000")

        assertEquals(1, gateway.requestCount)
        assertEquals(OtpState.Phase.AwaitingCode, engine.state.value.phase)
        assertEquals(6, engine.state.value.codeLength)
        assertEquals(3, engine.state.value.attemptsRemaining)
        engine.close()
    }

    @Test
    fun `a correct code reaches Verified`() = runTest {
        val gateway = FakeGateway()
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)))
        engine.start("0900000000")

        "123456".forEach { engine.dispatch(OtpCommand.AppendDigit(it)) }
        engine.dispatch(OtpCommand.Submit)

        assertEquals("123456", gateway.lastSubmittedCode)
        assertEquals(OtpState.Phase.Verified, engine.state.value.phase)
        engine.close()
    }

    @Test
    fun `a wrong code returns to awaiting with one fewer attempt`() = runTest {
        val gateway = FakeGateway(verifyResult = { SdkResult.Failure(OtpErrors.otpInvalid()) })
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)))
        engine.start("0900000000")

        "000000".forEach { engine.dispatch(OtpCommand.AppendDigit(it)) }
        engine.dispatch(OtpCommand.Submit)

        assertEquals(OtpState.Phase.AwaitingCode, engine.state.value.phase)
        assertEquals(2, engine.state.value.attemptsRemaining)
        assertEquals(OtpErrors.OTP_INVALID, engine.state.value.error?.code)
        engine.close()
    }

    @Test
    fun `exhausting attempts ends the session`() = runTest {
        val gateway = FakeGateway(verifyResult = { SdkResult.Failure(OtpErrors.otpInvalid()) })
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)), maxAttempts = 2)
        engine.start("0900000000")

        repeat(2) {
            "000000".forEach { digit -> engine.dispatch(OtpCommand.AppendDigit(digit)) }
            engine.dispatch(OtpCommand.Submit)
        }

        assertEquals(OtpState.Phase.Failed, engine.state.value.phase)
        assertEquals(OtpErrors.OTP_ATTEMPTS_EXCEEDED, engine.state.value.error?.code)
        engine.close()
    }

    @Test
    fun `resend is refused while the cooldown is still running`() = runTest {
        val gateway = FakeGateway()
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)))
        engine.start("0900000000")

        engine.dispatch(OtpCommand.Resend)

        // Still exactly one request: the cooldown blocked the second one.
        assertEquals(1, gateway.requestCount)
        assertEquals(OtpErrors.OTP_RESEND_TOO_SOON, engine.state.value.error?.code)
        engine.close()
    }

    @Test
    fun `a gateway failure on start is surfaced as a fatal error`() = runTest {
        val gateway = object : OtpGateway {
            override suspend fun requestOtp(destination: String) =
                SdkResult.Failure(SdkErrors.networkUnavailable())
            override suspend fun verifyOtp(challengeId: String, code: String) =
                SdkResult.Success(Unit)
        }
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)))

        engine.start("0900000000")

        assertEquals(OtpState.Phase.Failed, engine.state.value.phase)
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, engine.state.value.error?.code)
        engine.close()
    }
}
