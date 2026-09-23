package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.error.SdkError

/** Business errors of the OTP feature, in the 3xxx family. Codes are append-only. */
public object OtpErrors {
    public const val OTP_INVALID: Int = 3000
    public const val OTP_EXPIRED: Int = 3001
    public const val OTP_ATTEMPTS_EXCEEDED: Int = 3002
    public const val OTP_RESEND_TOO_SOON: Int = 3003

    public fun otpInvalid(): SdkError =
        SdkError.Business(OTP_INVALID, "The submitted code is not correct")

    public fun otpExpired(): SdkError =
        SdkError.Business(OTP_EXPIRED, "The challenge has expired")

    public fun otpAttemptsExceeded(): SdkError =
        SdkError.Business(OTP_ATTEMPTS_EXCEEDED, "Too many incorrect attempts")

    public fun otpResendTooSoon(retryAfterSeconds: Int): SdkError =
        SdkError.Business(OTP_RESEND_TOO_SOON, "Resend allowed in $retryAfterSeconds seconds")

    /** Every code this feature can emit. */
    public fun all(): List<Int> =
        listOf(OTP_INVALID, OTP_EXPIRED, OTP_ATTEMPTS_EXCEEDED, OTP_RESEND_TOO_SOON)
}
