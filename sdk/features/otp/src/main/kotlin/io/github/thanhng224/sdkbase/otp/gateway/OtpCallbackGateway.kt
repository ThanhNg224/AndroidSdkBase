package io.github.thanhng224.sdkbase.otp.gateway

import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback
import io.github.thanhng224.sdkbase.core.gateway.awaitCallback
import io.github.thanhng224.sdkbase.core.gateway.awaitCompletion
import io.github.thanhng224.sdkbase.core.result.SdkResult

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
 * Adapts a callback gateway into the suspend contract [OtpGateway] consumes, via core's
 * [awaitCallback]/[awaitCompletion]: the callback may arrive on any thread, and only the first
 * terminal call resumes the coroutine, so a host calling back twice cannot crash the SDK.
 */
public fun OtpCallbackGateway.asGateway(): OtpGateway {
    val delegate = this
    return object : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
            awaitCallback { callback -> delegate.requestOtp(destination, callback) }

        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> =
            awaitCompletion { callback -> delegate.verifyOtp(challengeId, code, callback) }
    }
}
