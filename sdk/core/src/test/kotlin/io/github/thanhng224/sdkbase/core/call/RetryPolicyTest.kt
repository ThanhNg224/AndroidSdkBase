package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryPolicyTest {

    @Test
    fun `retries a transient failure then succeeds`() = runTest {
        var attempts = 0
        val policy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 500)
        val result = policy.run { attempt ->
            attempts = attempt
            if (attempt < 3) {
                SdkResult.Failure(SdkErrors.networkUnavailable())
            } else {
                SdkResult.Success("ok")
            }
        }
        assertEquals(SdkResult.Success("ok"), result)
        assertEquals(3, attempts)
    }

    @Test
    fun `stops immediately on a non-retryable failure`() = runTest {
        var attempts = 0
        val policy = RetryPolicy(maxAttempts = 5, initialDelayMillis = 500)
        val result = policy.run { attempt ->
            attempts = attempt
            SdkResult.Failure(SdkErrors.invalidConfig("bad"))
        }
        assertEquals(1, attempts)
        assertEquals(SdkErrors.INVALID_CONFIG, (result as SdkResult.Failure).error.code)
    }

    @Test
    fun `gives up after maxAttempts and returns the last failure`() = runTest {
        var attempts = 0
        val policy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 500)
        val result = policy.run { attempt ->
            attempts = attempt
            SdkResult.Failure(SdkErrors.timeout("op-$attempt"))
        }
        assertEquals(3, attempts)
        val error = (result as SdkResult.Failure).error
        assertEquals(SdkErrors.TIMEOUT, error.code)
        assertTrue(error.reason.contains("op-3"))
    }

    @Test
    fun `backoff delays double and are capped at maxDelayMillis`() = runTest {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 500,
            maxDelayMillis = 1_200,
            factor = 2.0,
        )
        val observedTimes = mutableListOf<Long>()
        policy.run { attempt ->
            observedTimes += testScheduler.currentTime
            SdkResult.Failure(SdkErrors.networkUnavailable())
        }
        // attempt 1 at t=0, then delay 500 -> attempt 2 at t=500, delay 1000 -> attempt 3 at
        // t=1500, delay capped at 1200 -> attempt 4 at t=2700, delay capped at 1200 -> attempt 5
        // at t=3900.
        assertEquals(listOf(0L, 500L, 1_500L, 2_700L, 3_900L), observedTimes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxAttempts below 1`() {
        RetryPolicy(maxAttempts = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a factor below 1`() {
        RetryPolicy(factor = 0.5)
    }
}
