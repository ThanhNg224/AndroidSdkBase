package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.StateListener
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
import java.util.concurrent.atomic.AtomicReference
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
        val callback = RecordingCallback<OtpSession>()
        val gateway = FakeGateway()
        val config = configOf(gateway)

        // Mirrors OtpSdk.start(config, callback)'s own wiring exactly (`onUndelivered = { it.close() }`),
        // but with a local reference to the constructed session: OtpSession exposes nothing that
        // would let a black-box caller observe its own closing, so proving "not leaked" needs a
        // handle a purely black-box call to the public overload could never give us.
        val sessionRef = AtomicReference<OtpSession>()
        val startedSignal = CountDownLatch(1)
        val proceed = CountDownLatch(1)

        val cancellable = launchCallback(
            dispatchers = dispatchers,
            callback = callback,
            onUndelivered = { it.close() },
        ) {
            val result = OtpSdk.start(config) as SdkResult.Success
            sessionRef.set(result.value)
            startedSignal.countDown()
            // A blocking wait, not a suspension - see LaunchCallbackTest for why this is what
            // reliably lets cancel() win the claim race against a block that has already finished.
            proceed.await(2, TimeUnit.SECONDS)
            result
        }

        assertTrue(startedSignal.await(2, TimeUnit.SECONDS))
        cancellable.cancel()
        proceed.countDown()

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())

        // The session was constructed but never delivered: onUndelivered must have closed it,
        // cancelling its own scope — so a further Java-callable call on it never fires either.
        val session = sessionRef.get()
        val submitCallback = RecordingCallback<Unit>()
        session.submit("123456", submitCallback)
        assertFalse(submitCallback.latch.await(300, TimeUnit.MILLISECONDS))
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
