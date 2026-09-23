package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.DispatcherProvider
import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.internal.OtpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    /** All three dispatchers are the real `Dispatchers.Default`, for tests that need real threads. */
    private val realDispatchers: DispatcherProvider = dispatchers(Dispatchers.Default)

    /** Builds an [OtpEngine] from bare request/verify lambdas, for tests that don't need [FakeGateway]'s counters. */
    private fun engineWith(
        requestOtp: suspend () -> SdkResult<OtpChallenge> = { SdkResult.Success(OtpChallenge("c", 6, 60, 30)) },
        verifyOtp: suspend () -> SdkResult<Unit> = { SdkResult.Success(Unit) },
        dispatchers: DispatcherProvider = dispatchers(StandardTestDispatcher()),
    ): OtpEngine {
        val gateway = object : OtpGateway {
            override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = requestOtp()
            override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> = verifyOtp()
        }
        return OtpEngine(gateway, dispatchers)
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

    @Test
    fun `start is honoured once`() = runTest {
        var requests = 0
        val engine = engineWith(requestOtp = { requests++; SdkResult.Success(OtpChallenge("c", 6, 60, 30)) })
        engine.start("0900")
        engine.start("0900")
        assertEquals(1, requests)
        assertEquals(SdkErrors.ALREADY_RUNNING, engine.state.value.error?.code)
        engine.close()
    }

    @Test
    fun `concurrent submits verify exactly once`() = runBlocking(Dispatchers.Default) {
        repeat(200) {
            val verifies = java.util.concurrent.atomic.AtomicInteger()
            val engine = engineWith(
                requestOtp = { SdkResult.Success(OtpChallenge("c", 1, 60, 30)) },
                verifyOtp = { verifies.incrementAndGet(); kotlinx.coroutines.yield(); SdkResult.Success(Unit) },
                dispatchers = realDispatchers,
            )
            engine.start("0900")
            engine.dispatch(OtpCommand.AppendDigit('1'))
            (1..2).map { launch { engine.dispatch(OtpCommand.Submit) } }.joinAll()
            assertEquals(1, verifies.get())
            engine.close()
        }
    }
}
