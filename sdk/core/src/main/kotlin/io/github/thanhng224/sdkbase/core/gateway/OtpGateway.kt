package io.github.thanhng224.sdkbase.core.gateway

import io.github.thanhng224.sdkbase.core.SdkResult

/**
 * The host owns the network. The SDK declares what it needs and the host implements it with
 * whatever client it already has — Retrofit, Ktor, OkHttp, a mock in tests.
 *
 * This is the single most important design rule of this base: a published SDK that bundles its own
 * HTTP stack forces a transitive dependency, a version conflict and a size cost on every consumer.
 */
public interface OtpGateway {

    /** Asks the backend to send a code to [destination]. */
    public suspend fun requestOtp(destination: String): SdkResult<OtpChallenge>

    /** Submits [code] for the challenge identified by [challengeId]. */
    public suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit>
}

/** What the backend tells the SDK about a freshly created challenge. */
public data class OtpChallenge(
    public val challengeId: String,
    public val codeLength: Int,
    public val expiresInSeconds: Int,
    public val resendAfterSeconds: Int,
)
