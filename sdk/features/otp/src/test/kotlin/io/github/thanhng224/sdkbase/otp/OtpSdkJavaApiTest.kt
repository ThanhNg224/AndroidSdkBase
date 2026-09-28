package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.LogLevel
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.StateListener
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.core.testing.RecordingLogSink
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.otp.config.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge
import io.github.thanhng224.sdkbase.otp.gateway.OtpGateway
import io.github.thanhng224.sdkbase.otp.session.OtpSession
import io.github.thanhng224.sdkbase.otp.session.OtpState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OtpSdk.start]'s callback overload and [OtpSession]'s callback twins are `launchCallback`/
 * `observe` wired onto OTP's real engine, so these exercise that wiring end to end on a real
 * single-thread executor rather than re-proving `launchCallback`'s own semantics (see
 * `LaunchCallbackTest` in `:sdk:core`).
 */
class OtpSdkJavaApiTest {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "otp-test-thread") }
    private val dispatchers = TestDispatcherProvider(executor.asCoroutineDispatcher())

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    private class FakeGateway(
        private val challenge: OtpChallenge = OtpChallenge("ch-1", 6, 60, 0),
        private val requestResult: () -> SdkResult<OtpChallenge> = { SdkResult.Success(challenge) },
        private val verifyResult: () -> SdkResult<Unit> = { SdkResult.Success(Unit) },
    ) : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> = requestResult()
        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> = verifyResult()
    }

    private fun configOf(gateway: OtpGateway): OtpSdkConfig =
        (
            OtpSdkConfig.Builder("0900000000", gateway)
                .environment(SdkEnvironment.Builder().dispatchers(dispatchers).build())
                .build() as SdkResult.Success
            ).value

    private class RecordingCallback<T> : ResultCallback<T> {
        val successes = mutableListOf<T>()
        val failures = mutableListOf<SdkError>()
        val latch = CountDownLatch(1)

        override fun onSuccess(value: T) {
            successes += value
            latch.countDown()
        }

        override fun onFailure(error: SdkError) {
            failures += error
            latch.countDown()
        }
    }

    private fun startSession(gateway: OtpGateway): OtpSession {
        val callback = RecordingCallback<OtpSession>()
        OtpSdk.start(configOf(gateway), callback)
        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        return callback.successes.single()
    }

    @Test
    fun `startDeliversSession`() {
        val callback = RecordingCallback<OtpSession>()

        OtpSdk.start(configOf(FakeGateway()), callback)

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        assertTrue(callback.failures.isEmpty())
        callback.successes.single().close()
    }

    @Test
    fun `startWithFailingGatewayDeliversFailure`() {
        val callback = RecordingCallback<OtpSession>()
        val gateway = FakeGateway(requestResult = { SdkResult.Failure(SdkErrors.networkUnavailable()) })

        OtpSdk.start(configOf(gateway), callback)

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        assertTrue(callback.successes.isEmpty())
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, callback.failures.single().code)
    }

    @Test
    fun `cancelBeforeDeliveryClosesTheSession`() {
        // Fully black-box: calls only the public OtpSdk.start(config, callback) overload. Proving
        // the undelivered session got closed (not leaked) needs a signal OtpSession itself does not
        // expose, so OtpSdkRuntime.close() logs a diagnostic DEBUG record - a legitimate log line,
        // not a test-only hook - which a RecordingLogSink here can observe from the outside.
        //
        // The hang point is the environment's telemetry sink, not the gateway: OtpEngine wraps every
        // gateway call in safeCall's withTimeoutOrNull, whose own completion bookkeeping resumes
        // through the coroutine's (by-then-cancelled) Job and throws CancellationException even
        // though the gateway call itself already produced a value - so cancelling while genuinely
        // inside that call loses the session outright (nothing is ever built to close), rather than
        // reproducing the "produced but undelivered" race this test targets. Telemetry is emitted
        // after the engine has already finished (no gateway call, no withTimeoutOrNull in flight), so
        // hanging there - still a blocking, non-suspending wait, per LaunchCallbackTest - lets the
        // session actually get constructed before cancel() is observed, exactly like the race
        // LaunchCallbackTest's `undeliveredSuccessIsReleased` proves for launchCallback in isolation.
        val sink = RecordingLogSink()
        val logger = SdkLogger.Builder().minLevel(LogLevel.VERBOSE).sink(sink).build()
        val started = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val telemetry = TelemetrySink { _, _ ->
            started.countDown()
            proceed.await(2, TimeUnit.SECONDS)
        }
        val config = (
            OtpSdkConfig.Builder("0900000000", FakeGateway())
                .environment(
                    SdkEnvironment.Builder()
                        .dispatchers(dispatchers)
                        .logger(logger)
                        .telemetry(telemetry)
                        .build(),
                )
                .build() as SdkResult.Success
            ).value
        val callback = RecordingCallback<OtpSession>()

        val cancellable = OtpSdk.start(config, callback)

        assertTrue(started.await(2, TimeUnit.SECONDS))
        cancellable.cancel()
        proceed.countDown()

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())

        val deadline = System.currentTimeMillis() + 2_000
        while (sink.messages().none { it.contains("session closed") } && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(
            "expected a \"session closed\" log record - the session OtpSdk.start built was never " +
                "delivered, so onUndelivered must have closed it instead of leaking it",
            sink.messages().any { it.contains("session closed") },
        )
    }

    @Test
    fun `submitCallbackReportsVerified`() {
        val session = startSession(FakeGateway())
        val callback = RecordingCallback<Unit>()

        session.submit("123456", callback)

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(Unit), callback.successes)
        assertTrue(callback.failures.isEmpty())
        assertEquals(OtpState.Phase.Verified, session.state.value.phase)
        session.close()
    }

    @Test
    fun `observeStateSeesVerified`() {
        val session = startSession(FakeGateway())
        val phases = mutableListOf<OtpState.Phase>()
        val verifiedLatch = CountDownLatch(1)
        val cancellable = session.observeState(
            StateListener {
                phases += it.phase
                if (it.phase == OtpState.Phase.Verified) verifiedLatch.countDown()
            },
        )

        val submitCallback = RecordingCallback<Unit>()
        session.submit("123456", submitCallback)

        assertTrue(verifiedLatch.await(2, TimeUnit.SECONDS))
        assertTrue(phases.contains(OtpState.Phase.Verified))
        cancellable.cancel()
        session.close()
    }

    @Test
    fun `closeCancelsPendingCallbacks`() {
        val started = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val gateway = object : OtpGateway {
            override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
                SdkResult.Success(OtpChallenge("ch-1", 6, 60, 0))

            override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> {
                started.countDown()
                proceed.await(2, TimeUnit.SECONDS)
                return SdkResult.Success(Unit)
            }
        }
        val session = startSession(gateway)
        val callback = RecordingCallback<Unit>()
        session.submit("123456", callback)

        assertTrue(started.await(2, TimeUnit.SECONDS))
        session.close()
        proceed.countDown()

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
    }
}
