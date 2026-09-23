package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.SdkError
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The Java-implementable form of [OtpGateway]: no suspend, no Continuation. A callback-based host
 * implements this directly; [asGateway] adapts it into the suspend contract the engine consumes.
 */
public interface OtpCallbackGateway {

    /** Asks the backend to send a code to [destination]. */
    public fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>)

    /** Submits [code] for the challenge identified by [challengeId]. */
    public fun verifyOtp(challengeId: String, code: String, callback: CompletionCallback)
}

/**
 * Adapts a callback gateway into the suspend contract [OtpGateway] consumes. The callback may
 * arrive on any thread, and only the first terminal call resumes the coroutine, so a host calling
 * back twice cannot crash the SDK.
 */
public fun OtpCallbackGateway.asGateway(): OtpGateway {
    val delegate = this
    return object : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
            bridge { callback -> delegate.requestOtp(destination, callback) }

        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> =
            bridgeCompletion { resume ->
                delegate.verifyOtp(
                    challengeId,
                    code,
                    object : CompletionCallback {
                        override fun onSuccess() = resume(SdkResult.Success(Unit))
                        override fun onFailure(error: SdkError) = resume(SdkResult.Failure(error))
                    },
                )
            }
    }
}

/**
 * Suspends until [register] delivers exactly one terminal call, guarded by
 * [AtomicBoolean.compareAndSet] against a double-resume. A cancellation before the callback simply
 * never resumes, which is correct.
 */
private suspend fun <T> bridge(register: (GatewayCallback<T>) -> Unit): SdkResult<T> =
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

/** Suspends until the host's first terminal callback; later callbacks are ignored. */
private suspend fun <T> bridgeCompletion(register: (resume: (SdkResult<T>) -> Unit) -> Unit): SdkResult<T> =
    suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        register { result ->
            if (resumed.compareAndSet(false, true)) continuation.resumeWith(Result.success(result))
        }
    }
