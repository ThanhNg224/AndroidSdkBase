package io.github.thanhng224.sdkbase.otp.gateway

import io.github.thanhng224.sdkbase.core.result.SdkResult

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

/**
 * What the backend tells the SDK about a freshly created challenge. A plain class, not a
 * `data class` (ABI: `copy`/`componentN` would freeze the property list for every consumer).
 *
 * The SDK rejects a challenge outside these ranges with a `GATEWAY_FAILURE` error: [challengeId]
 * not blank, [codeLength] in 4..10, [expiresInSeconds] greater than 0, [resendAfterSeconds] at
 * least 0.
 */
public class OtpChallenge(
    public val challengeId: String,
    public val codeLength: Int,
    public val expiresInSeconds: Int,
    public val resendAfterSeconds: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OtpChallenge) return false
        return challengeId == other.challengeId &&
            codeLength == other.codeLength &&
            expiresInSeconds == other.expiresInSeconds &&
            resendAfterSeconds == other.resendAfterSeconds
    }

    override fun hashCode(): Int {
        var result = challengeId.hashCode()
        result = 31 * result + codeLength
        result = 31 * result + expiresInSeconds
        result = 31 * result + resendAfterSeconds
        return result
    }

    override fun toString(): String =
        "OtpChallenge(challengeId=$challengeId, codeLength=$codeLength, " +
            "expiresInSeconds=$expiresInSeconds, resendAfterSeconds=$resendAfterSeconds)"
}
