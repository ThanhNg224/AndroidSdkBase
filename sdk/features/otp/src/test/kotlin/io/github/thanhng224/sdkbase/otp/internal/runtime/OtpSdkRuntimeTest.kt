package io.github.thanhng224.sdkbase.otp.internal.runtime

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.LogLevel
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.testing.RecordingLogSink
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.otp.config.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge
import io.github.thanhng224.sdkbase.otp.gateway.OtpGateway
import io.github.thanhng224.sdkbase.otp.internal.engine.OtpEngine
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [OtpSdkRuntime] directly, with its own [OtpEngine] on a deterministic test dispatcher,
 * so the stale-digit-clearing fix in [OtpSdkRuntime.submit] can be asserted without racing the
 * runtime's own fire-and-forget `dispatch()` scope (which — deliberately, see [OtpSdkRuntime] — runs
 * on the real `AndroidDispatchers`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtpSdkRuntimeTest {

    private class FakeGateway(
        private val challenge: OtpChallenge = OtpChallenge("ch-1", 6, 60, 30),
    ) : OtpGateway {
        var lastSubmittedCode: String? = null

        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
            SdkResult.Success(challenge)

        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> {
            lastSubmittedCode = code
            return SdkResult.Success(Unit)
        }
    }

    @Test
    fun `submit clears a stale partially entered code before submitting the caller's code`() = runTest {
        val gateway = FakeGateway()
        val scope = SessionScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), SdkLogger.NoOp.tagged("Test"))
        val engine = OtpEngine(gateway, scope)
        val config = OtpSdkConfig.Builder("0900000000", gateway).build().getOrNull()!!
        engine.start("0900000000")

        // Three stray digits already sitting in the engine, e.g. left by the bundled UI, before an
        // SMS-autofilled code is submitted directly.
        engine.dispatch(OtpCommand.AppendDigit('9'))
        engine.dispatch(OtpCommand.AppendDigit('9'))
        engine.dispatch(OtpCommand.AppendDigit('9'))

        val runtime = OtpSdkRuntime(scope, engine, config, SdkLogger.NoOp)
        runtime.submit("123456")

        assertEquals("123456", gateway.lastSubmittedCode)
        runtime.close()
    }

    private suspend fun newRuntime(
        gateway: OtpGateway,
        logger: SdkLogger = SdkLogger.NoOp,
        testScheduler: TestCoroutineScheduler,
    ): OtpSdkRuntime {
        val scope = SessionScope(TestDispatcherProvider(StandardTestDispatcher(testScheduler)), logger.tagged("Test"))
        val engine = OtpEngine(gateway, scope, logger = logger)
        val config = OtpSdkConfig.Builder("0900000000", gateway).build().getOrNull()!!
        engine.start("0900000000")
        return OtpSdkRuntime(scope, engine, config, logger)
    }

    @Test
    fun submitAfterCloseFailsWithSessionClosed() = runTest {
        val runtime = newRuntime(FakeGateway(), testScheduler = testScheduler)
        runtime.close()

        val result = runtime.submit("123456")

        val failure = result as SdkResult.Failure
        assertEquals(SdkErrors.SESSION_CLOSED, failure.error.code)
    }

    @Test
    fun submitAfterCloseNeverCallsGateway() = runTest {
        val gateway = FakeGateway()
        val runtime = newRuntime(gateway, testScheduler = testScheduler)
        runtime.close()

        runtime.submit("123456")

        assertEquals(null, gateway.lastSubmittedCode)
    }

    @Test
    fun closeTwiceLogsSessionClosedOnce() = runTest {
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().minLevel(LogLevel.DEBUG).sink(sink).build()
        val runtime = newRuntime(FakeGateway(), logger = logger, testScheduler = testScheduler)

        runtime.close()
        runtime.close()

        assertEquals(1, sink.messages().count { it.contains("session closed") })
    }

    @Test
    fun closeStopsTicker() = runTest {
        val gateway = FakeGateway(challenge = OtpChallenge("ch-1", 6, 120, 60))
        val runtime = newRuntime(gateway, testScheduler = testScheduler)

        advanceTimeBy(1_100)
        val afterOneTick = runtime.state.value.secondsUntilResend

        runtime.close()
        advanceTimeBy(5_000)

        assertEquals(afterOneTick, runtime.state.value.secondsUntilResend)
    }

    @Test
    fun tickDuringVerifyDoesNotLoseVerifiedPhase() = runTest {
        val proceed = CompletableDeferred<Unit>()
        val gateway = object : OtpGateway {
            override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
                SdkResult.Success(OtpChallenge("ch-1", 6, 120, 60))

            override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> {
                proceed.await()
                return SdkResult.Success(Unit)
            }
        }
        val runtime = newRuntime(gateway, testScheduler = testScheduler)

        // Starts the submit, which suspends inside the gateway's own `verifyOtp` while the engine's
        // `store` lock is held for the whole submit.
        val submitResult = async { runtime.submit("123456") }
        runCurrent()

        // Several ticks elapse while verify is still in flight - they must queue behind the held
        // lock instead of racing the transition it is about to make.
        advanceTimeBy(5_000)
        runCurrent()

        proceed.complete(Unit)
        val result = submitResult.await()

        assertTrue(result is SdkResult.Success)
        assertEquals(OtpState.Phase.Verified, runtime.state.value.phase)
    }
}
