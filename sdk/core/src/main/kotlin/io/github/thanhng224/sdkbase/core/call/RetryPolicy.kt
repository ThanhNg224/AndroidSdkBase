package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import kotlinx.coroutines.delay

/**
 * Retries a [SdkResult]-returning operation with exponential backoff. [run] stops on the first
 * success, on a failure [retryOn] rejects, or after [maxAttempts], returning the last failure.
 *
 * A Java host cannot use Kotlin default arguments, so every trailing-default overload is
 * generated with [JvmOverloads].
 */
public class RetryPolicy @JvmOverloads constructor(
    public val maxAttempts: Int = 3,
    public val initialDelayMillis: Long = 500,
    public val maxDelayMillis: Long = 5_000,
    public val factor: Double = 2.0,
    public val retryOn: (SdkError) -> Boolean = RetryPolicy.TransientErrors,
) {

    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        require(factor >= 1.0) { "factor must be >= 1.0, was $factor" }
    }

    public suspend fun <T> run(block: suspend (attempt: Int) -> SdkResult<T>): SdkResult<T> {
        var attempt = 1
        var delayMillis = initialDelayMillis
        while (true) {
            val result = block(attempt)
            if (result is SdkResult.Success) return result
            val error = (result as SdkResult.Failure).error
            if (attempt >= maxAttempts || !retryOn(error)) return result
            delay(delayMillis)
            delayMillis = (delayMillis * factor).toLong().coerceAtMost(maxDelayMillis)
            attempt++
        }
    }

    public companion object {
        // Declared before `None`: `None`'s constructor call resolves the `retryOn` default to
        // this field, which must already be initialized by then.
        public val TransientErrors: (SdkError) -> Boolean = { error ->
            error.code == SdkErrors.NETWORK_UNAVAILABLE || error.code == SdkErrors.TIMEOUT
        }

        /** A single attempt, no retry. */
        public val None: RetryPolicy = RetryPolicy(maxAttempts = 1)
    }
}
