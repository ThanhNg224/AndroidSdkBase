package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.SdkError
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The Java-implementable form of [OtpGateway]: no suspend, no Continuation.
 *
 * A Java host with a genuinely asynchronous client — a callback-based HTTP library, the normal
 * case for enterprise Android hosts — implements this directly instead of hand-driving Kotlin's
 * undocumented `Continuation.resumeWith` protocol. [asGateway] adapts it into the suspend contract
 * the engine consumes.
 */
public interface OtpCallbackGateway {

    /** Asks the backend to send a code to [destination]. */
    public fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>)

    /** Submits [code] for the challenge identified by [challengeId]. */
    public fun verifyOtp(challengeId: String, code: String, callback: CompletionCallback)
}

/**
 * Adapts a callback gateway into the suspend contract [OtpGateway] consumes.
 *
 * Two things are not optional here: a callback delivered from a thread other than the caller's
 * must still resume the suspended coroutine — the normal case for an async client, and exactly why
 * `CancellableContinuation.resume` is documented as safe to call from any thread — and a host that
 * calls back more than once must not crash the SDK, so only the first terminal call is honoured.
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
 * Suspends until [register] delivers exactly one terminal call, guarding against a second one with
 * [AtomicBoolean.compareAndSet] so a misbehaving host cannot double-resume the continuation. If the
 * coroutine is cancelled before the host calls back, [suspendCancellableCoroutine] simply never
 * resumes — that is correct: cancellation is not swallowed here.
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
