package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.concurrent.thread

class GatewayContractTest {

    private fun assertFailsWith(expected: String, block: () -> Unit) {
        try {
            block()
            fail("expected AssertionError: $expected")
        } catch (e: AssertionError) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(expected))
        }
    }

    // --- assertReturns

    @Test
    fun `assertReturns hands back the result of a well-behaved call`() {
        val result = GatewayContract.assertReturns {
            delay(10)
            SdkResult.Success("ok")
        }

        assertEquals("ok", result.assertSuccess())
    }

    @Test
    fun `assertReturns passes a Failure result through`() {
        val result = GatewayContract.assertReturns<Unit> { SdkResult.Failure(SdkErrors.networkUnavailable()) }

        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, result.assertFailure().code)
    }

    @Test
    fun `assertReturns fails when the call throws`() {
        assertFailsWith("gateway threw") {
            GatewayContract.assertReturns<Unit> { throw IllegalStateException("boom") }
        }
    }

    @Test
    fun `assertReturns fails when the call hangs`() {
        assertFailsWith("gateway did not return within 200 ms") {
            GatewayContract.assertReturns<Unit>(timeoutMillis = 200) {
                delay(60_000)
                SdkResult.Success(Unit)
            }
        }
    }

    // --- assertCancellable

    @Test
    fun `assertCancellable passes a call that cooperates with cancellation`() {
        GatewayContract.assertCancellable {
            delay(60_000)
            SdkResult.Success(Unit)
        }
    }

    @Test
    fun `assertCancellable fails a call that ignores cancellation`() {
        assertFailsWith("gateway ignored cancellation") {
            GatewayContract.assertCancellable(timeoutMillis = 300) {
                Thread.sleep(3_000)
                SdkResult.Success(Unit)
            }
        }
    }

    @Test
    fun `assertCancellable fails a call that throws when cancelled`() {
        assertFailsWith("gateway threw") {
            GatewayContract.assertCancellable {
                try {
                    delay(60_000)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw IllegalStateException("cleanup failed")
                }
                SdkResult.Success(Unit)
            }
        }
    }

    // --- assertCallsBackOnce

    @Test
    fun `assertCallsBackOnce returns the delivered result from another thread`() {
        val result = GatewayContract.assertCallsBackOnce<String> { callback ->
            thread { callback.onSuccess("ok") }
        }

        assertEquals("ok", result.assertSuccess())
    }

    @Test
    fun `assertCallsBackOnce returns a delivered failure`() {
        val result = GatewayContract.assertCallsBackOnce<String> { it.onFailure(SdkErrors.timeout("x")) }

        assertEquals(SdkErrors.TIMEOUT, result.assertFailure().code)
    }

    @Test
    fun `assertCallsBackOnce fails on a double callback`() {
        assertFailsWith("gateway called back 2 times") {
            GatewayContract.assertCallsBackOnce<String>(settleMillis = 100) { callback ->
                callback.onSuccess("a")
                callback.onSuccess("b")
            }
        }
    }

    @Test
    fun `assertCallsBackOnce fails on a late second callback`() {
        assertFailsWith("gateway called back 2 times") {
            GatewayContract.assertCallsBackOnce<String>(settleMillis = 300) { callback ->
                callback.onSuccess("a")
                thread {
                    Thread.sleep(50)
                    callback.onFailure(SdkErrors.unknown())
                }
            }
        }
    }

    @Test
    fun `assertCallsBackOnce fails when it never calls back`() {
        assertFailsWith("gateway never called back") {
            GatewayContract.assertCallsBackOnce<String>(timeoutMillis = 100) { }
        }
    }

    @Test
    fun `assertCallsBackOnce fails when register throws`() {
        assertFailsWith("register threw") {
            GatewayContract.assertCallsBackOnce<String> { throw IllegalStateException("boom") }
        }
    }

    // --- assertCompletesOnce

    @Test
    fun `assertCompletesOnce returns Success for a completion`() {
        val result = GatewayContract.assertCompletesOnce { callback: CompletionCallback ->
            thread { callback.onSuccess() }
        }

        result.assertSuccess()
    }

    @Test
    fun `assertCompletesOnce returns a delivered failure`() {
        val result = GatewayContract.assertCompletesOnce { it.onFailure(SdkErrors.unknown()) }

        assertEquals(SdkErrors.UNKNOWN, result.assertFailure().code)
    }

    @Test
    fun `assertCompletesOnce fails on a double completion`() {
        assertFailsWith("gateway called back 2 times") {
            GatewayContract.assertCompletesOnce(settleMillis = 100) { callback ->
                callback.onSuccess()
                callback.onFailure(SdkErrors.unknown())
            }
        }
    }

    @Test
    fun `assertCompletesOnce fails when it never completes`() {
        assertFailsWith("gateway never called back") {
            GatewayContract.assertCompletesOnce(timeoutMillis = 100) { }
        }
    }

    @Test
    fun `assertCompletesOnce fails when register throws`() {
        assertFailsWith("register threw") {
            GatewayContract.assertCompletesOnce { throw IllegalStateException("boom") }
        }
    }
}
