package io.github.thanhng224.sdkbase.core.gateway

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpCallbackGatewayTest {

    private val challenge = OtpChallenge("ch-1", 6, 60, 30)

    @Test
    fun `a synchronous success is delivered`() = runTest {
        val gateway = object : OtpCallbackGateway {
            override fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>) {
                callback.onSuccess(challenge)
            }
            override fun verifyOtp(challengeId: String, code: String, callback: GatewayCallback<Unit>) {
                callback.onSuccess(Unit)
            }
        }.asGateway()

        val result = gateway.requestOtp("0900000000")
        assertEquals(challenge, (result as SdkResult.Success).value)
    }

    @Test
    fun `a failure is delivered as the host's own error`() = runTest {
        val gateway = object : OtpCallbackGateway {
            override fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>) {
                callback.onFailure(SdkErrors.networkUnavailable())
            }
            override fun verifyOtp(challengeId: String, code: String, callback: GatewayCallback<Unit>) {
                callback.onSuccess(Unit)
            }
        }.asGateway()

        val result = gateway.requestOtp("0900000000")
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, (result as SdkResult.Failure).error.code)
    }

    @Test
    fun `a callback delivered from another thread still resumes`() {
        // Deliberately `runBlocking`, not `runTest`: `runTest`'s virtual clock auto-advances a
        // pending `withTimeout` the instant its scheduler looks idle, and a real background
        // `Thread`'s callback is invisible to that scheduler — it races the virtual clock and lost
        // 100% of the time in manual verification (`TimeoutCancellationException` at real elapsed
        // time under 3ms, every run). `runBlocking` gives this test a real dispatcher and a real
        // clock, so the 5-second timeout only fires if the background thread genuinely never calls
        // back — which is the property this test exists to check.
        val gateway = object : OtpCallbackGateway {
            override fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>) {
                Thread { callback.onSuccess(challenge) }.start()
            }
            override fun verifyOtp(challengeId: String, code: String, callback: GatewayCallback<Unit>) {
                callback.onSuccess(Unit)
            }
        }.asGateway()

        val result = runBlocking { withTimeout(5_000) { gateway.requestOtp("0900000000") } }
        assertTrue(result is SdkResult.Success)
    }

    @Test
    fun `a host that calls back twice does not crash the SDK`() = runTest {
        // A misbehaving host is the SDK's problem to survive, not to propagate.
        val gateway = object : OtpCallbackGateway {
            override fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>) {
                callback.onSuccess(challenge)
                callback.onSuccess(challenge)
                callback.onFailure(SdkErrors.unknown())
            }
            override fun verifyOtp(challengeId: String, code: String, callback: GatewayCallback<Unit>) {
                callback.onSuccess(Unit)
            }
        }.asGateway()

        val result = withTimeout(5_000) { gateway.requestOtp("0900000000") }
        assertEquals(challenge, (result as SdkResult.Success).value)
    }
}
