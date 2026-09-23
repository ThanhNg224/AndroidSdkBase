package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.error.FailureMapper
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs host-supplied [block] so nothing it throws or hangs on reaches the caller: never throws,
 * except a [CancellationException] that is not this call's own timeout, which is rethrown so
 * cancellation from further up the call stack still propagates.
 *
 * When [timeoutMillis] is set, a timeout is reported as [SdkErrors.timeout]; any other
 * [Exception] is handed to [mapper]. An [Error] is never caught.
 */
public suspend fun <T> safeCall(
    operation: String,
    timeoutMillis: Long? = null,
    mapper: FailureMapper = FailureMapper.Default,
    block: suspend () -> SdkResult<T>,
): SdkResult<T> = try {
    // withTimeoutOrNull returns null only for its own timeout; a timeout or cancellation from an
    // enclosing scope still propagates instead of being reported to the caller as a Failure.
    if (timeoutMillis != null) {
        withTimeoutOrNull(timeoutMillis) { block() } ?: SdkResult.Failure(SdkErrors.timeout(operation))
    } else {
        block()
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SdkResult.Failure(mapper.map(operation, e))
}
