package io.github.thanhng224.sdkbase.core

/**
 * Error codes shared by every feature. Codes are append-only: never renumber a released code.
 *
 * Families: 1xxx common, 2xxx system/transport, 3xxx business (owned by each feature, e.g.
 * `OtpErrors`), 4xxx lifecycle. Hosts branch on [SdkError.code], never on [SdkError.reason].
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

    public fun notStarted(): SdkError =
        SdkError.Lifecycle(NOT_STARTED, "The SDK has not been started")

    public fun alreadyRunning(): SdkError =
        SdkError.Lifecycle(ALREADY_RUNNING, "A session is already running")

    /** Every code in this catalog. */
    public fun all(): List<Int> = listOf(
        UNKNOWN, INVALID_CONFIG, CANCELLED_BY_USER,
        NETWORK_UNAVAILABLE, GATEWAY_FAILURE, TIMEOUT,
        NOT_STARTED, ALREADY_RUNNING,
    )
}
