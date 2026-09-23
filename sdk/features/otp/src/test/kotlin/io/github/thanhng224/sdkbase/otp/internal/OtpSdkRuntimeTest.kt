package io.github.thanhng224.sdkbase.otp.internal

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.otp.OtpChallenge
import io.github.thanhng224.sdkbase.otp.OtpCommand
import io.github.thanhng224.sdkbase.otp.OtpGateway
import io.github.thanhng224.sdkbase.otp.OtpSdkConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun dispatchers(dispatcher: CoroutineDispatcher) = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
    }

    @Test
    fun `submit clears a stale partially entered code before submitting the caller's code`() = runTest {
        val gateway = FakeGateway()
        val engine = OtpEngine(gateway, dispatchers(StandardTestDispatcher(testScheduler)))
        val config = OtpSdkConfig.Builder("0900000000", gateway).build().getOrNull()!!
        engine.start("0900000000")

        // Three stray digits already sitting in the engine, e.g. left by the bundled UI, before an
        // SMS-autofilled code is submitted directly.
        engine.dispatch(OtpCommand.AppendDigit('9'))
        engine.dispatch(OtpCommand.AppendDigit('9'))
        engine.dispatch(OtpCommand.AppendDigit('9'))

        val runtime = OtpSdkRuntime(engine, config)
        runtime.submit("123456")

        assertEquals("123456", gateway.lastSubmittedCode)
        runtime.close()
    }
}
