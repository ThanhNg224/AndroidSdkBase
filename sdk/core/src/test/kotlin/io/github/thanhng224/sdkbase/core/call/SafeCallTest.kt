package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeCallTest {

    @Test
    fun `a successful block passes through untouched`() = runTest {
        val result = safeCall("op") { SdkResult.Success(42) }
        assertEquals(SdkResult.Success(42), result)
    }

    @Test
    fun `an IOException is mapped to networkUnavailable`() = runTest {
        val result = safeCall<Unit>("op") { throw IOException("down") }
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, (result as SdkResult.Failure).error.code)
    }

    @Test
    fun `any other exception is mapped to gatewayFailure`() = runTest {
        val result = safeCall<Unit>("op") { throw IllegalStateException("boom") }
        assertEquals(SdkErrors.GATEWAY_FAILURE, (result as SdkResult.Failure).error.code)
    }

    @Test
    fun `a timeout from an enclosing scope propagates instead of becoming a Failure`() = runTest {
        // The caller must never see a Failure for a cancellation that came from above it: it could
        // act on that result (e.g. move to a Failed state) before the cancellation lands.
        var returned: SdkResult<Unit>? = null
        var caught: Throwable? = null
        try {
            withTimeout(100) {
                returned = safeCall<Unit>("op") { awaitCancellation() }
            }
        } catch (e: TimeoutCancellationException) {
            caught = e
        }
        assertEquals(null, returned)
        assertTrue(caught is TimeoutCancellationException)
    }

    @Test
    fun `a block that outlives its timeout is mapped to timeout`() = runTest {
        val result = safeCall<Unit>("op", timeoutMillis = 100) {
            delay(1_000)
            SdkResult.Success(Unit)
        }
        assertEquals(SdkErrors.TIMEOUT, (result as SdkResult.Failure).error.code)
    }

    @Test
    fun `cancelling the caller propagates instead of being swallowed`() = runTest {
        var caughtCancellation = false
        val job = launch {
            try {
                safeCall<Unit>("op") { awaitCancellation() }
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }
        yield()
        job.cancel()
        job.join()
        assertTrue(caughtCancellation)
    }
}
