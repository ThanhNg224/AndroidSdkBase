package io.github.thanhng224.sdkbase.otp.internal.engine

import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge

/** The first rule this host-supplied challenge breaks, or `null` if it is usable. */
internal fun OtpChallenge.violation(): String? = when {
    challengeId.isBlank() -> "challengeId is blank"
    codeLength !in 4..10 -> "codeLength must be in 4..10, was $codeLength"
    expiresInSeconds <= 0 -> "expiresInSeconds must be > 0, was $expiresInSeconds"
    resendAfterSeconds < 0 -> "resendAfterSeconds must be >= 0, was $resendAfterSeconds"
    else -> null
}
