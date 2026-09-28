package io.github.thanhng224.sdkbase.core.gateway

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until [register] delivers exactly one terminal call to the [GatewayCallback] it is
 * given, guarded by [AtomicBoolean.compareAndSet] against a double-resume: the first call — from
 * any thread — resumes the coroutine, and every later call is silently ignored. A callback that
 * arrives after the awaiting coroutine has already been cancelled is ignored too, rather than
 * crashing — that is the whole point of [suspendCancellableCoroutine] for a callback-based API. A
 * throw out of [register] itself propagates to the caller uncaught, since callers run this inside
 * [io.github.thanhng224.sdkbase.core.call.safeCall].
 */
@SdkInternalApi
public suspend fun <T> awaitCallback(register: (GatewayCallback<T>) -> Unit): SdkResult<T> =
    suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        register(
            object : GatewayCallback<T> {
                override fun onSuccess(value: T) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWith(Result.success(SdkResult.Success(value)))
                    }
                }

                override fun onFailure(error: SdkError) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWith(Result.success(SdkResult.Failure(error)))
                    }
                }
            },
        )
    }

/** [awaitCallback]'s twin for a [CompletionCallback] (no value, just success/failure). */
@SdkInternalApi
public suspend fun awaitCompletion(register: (CompletionCallback) -> Unit): SdkResult<Unit> =
    suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        register(
            object : CompletionCallback {
                override fun onSuccess() {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWith(Result.success(SdkResult.Success(Unit)))
                    }
                }

                override fun onFailure(error: SdkError) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWith(Result.success(SdkResult.Failure(error)))
                    }
                }
            },
        )
    }
