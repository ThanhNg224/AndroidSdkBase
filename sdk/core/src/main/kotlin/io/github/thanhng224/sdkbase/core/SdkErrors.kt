package io.github.thanhng224.sdkbase.core

/**
 * The catalog. Every error the SDK can emit is created here and nowhere else, so
 * `docs/ERROR_CODES.md` can be generated from one file and a host can enumerate the whole surface.
 *
 * Codes are append-only: never renumber a released code.
 */
public object SdkErrors {

    // --- 1xxx common
    public const val UNKNOWN: Int = 1000
    public const val INVALID_CONFIG: Int = 1001
    public const val CANCELLED_BY_USER: Int = 1002

    // --- 2xxx system / transport
    public const val NETWORK_UNAVAILABLE: Int = 2000
    public const val GATEWAY_FAILURE: Int = 2001
    public const val TIMEOUT: Int = 2002

    // --- 3xxx business
    public const val OTP_INVALID: Int = 3000
    public const val OTP_EXPIRED: Int = 3001
    public const val OTP_ATTEMPTS_EXCEEDED: Int = 3002
    public const val OTP_RESEND_TOO_SOON: Int = 3003

    // --- 4xxx lifecycle
    public const val NOT_STARTED: Int = 4000
    public const val ALREADY_RUNNING: Int = 4001

    public fun unknown(cause: Throwable? = null): SdkError =
        SdkError.Common(UNKNOWN, "Unknown failure", cause)

    public fun invalidConfig(reason: String): SdkError =
        SdkError.Common(INVALID_CONFIG, "Invalid configuration: $reason")

    public fun cancelledByUser(): SdkError =
        SdkError.Common(CANCELLED_BY_USER, "Cancelled by user")

    public fun networkUnavailable(cause: Throwable? = null): SdkError =
        SdkError.System(NETWORK_UNAVAILABLE, "Network unavailable", cause)

    public fun gatewayFailure(reason: String, cause: Throwable? = null): SdkError =
        SdkError.System(GATEWAY_FAILURE, "Host gateway failed: $reason", cause)

    public fun timeout(reason: String): SdkError =
        SdkError.System(TIMEOUT, "Timed out: $reason")

    public fun otpInvalid(): SdkError =
        SdkError.Business(OTP_INVALID, "The submitted code is not correct")

    public fun otpExpired(): SdkError =
        SdkError.Business(OTP_EXPIRED, "The challenge has expired")

    public fun otpAttemptsExceeded(): SdkError =
        SdkError.Business(OTP_ATTEMPTS_EXCEEDED, "Too many incorrect attempts")

    public fun otpResendTooSoon(retryAfterSeconds: Int): SdkError =
        SdkError.Business(OTP_RESEND_TOO_SOON, "Resend allowed in $retryAfterSeconds seconds")

    public fun notStarted(): SdkError =
        SdkError.Lifecycle(NOT_STARTED, "The SDK has not been started")

    public fun alreadyRunning(): SdkError =
        SdkError.Lifecycle(ALREADY_RUNNING, "A session is already running")

    /** Every code in the catalog. Used by tests and by the docs generator. */
    public fun all(): List<Int> = listOf(
        UNKNOWN, INVALID_CONFIG, CANCELLED_BY_USER,
        NETWORK_UNAVAILABLE, GATEWAY_FAILURE, TIMEOUT,
        OTP_INVALID, OTP_EXPIRED, OTP_ATTEMPTS_EXCEEDED, OTP_RESEND_TOO_SOON,
        NOT_STARTED, ALREADY_RUNNING,
    )
}
