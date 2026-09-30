package io.github.thanhng224.sdkbase.eventlogging.error

import io.github.thanhng224.sdkbase.core.error.SdkError

/** Stable error catalog for the optional event logging feature. */
public object EventLoggingErrors {
    public const val INVALID_EVENT: Int = 3101
    public const val STORAGE_FAILURE: Int = 2101
    public const val QUEUE_FULL: Int = 3103
    public const val DELIVERY_IN_PROGRESS: Int = 2102
    public const val DELIVERY_FAILURE: Int = 2103
    public const val SCHEDULER_FAILURE: Int = 2104

    public fun invalidEvent(reason: String): SdkError = SdkError.Business(INVALID_EVENT, "Invalid event: $reason")

    public fun storageFailure(cause: Throwable? = null): SdkError =
        SdkError.System(STORAGE_FAILURE, "Event log storage failed", cause, isRetryable = true)

    public fun queueFull(): SdkError = SdkError.Business(QUEUE_FULL, "Event log queue is full", isRetryable = true)

    public fun deliveryInProgress(): SdkError =
        SdkError.System(DELIVERY_IN_PROGRESS, "Another process is delivering this event queue", isRetryable = true)

    public fun deliveryFailure(cause: Throwable? = null): SdkError =
        SdkError.System(DELIVERY_FAILURE, "Event delivery failed", cause, isRetryable = true)

    public fun schedulerFailure(cause: Throwable? = null): SdkError =
        SdkError.System(SCHEDULER_FAILURE, "Event delivery scheduling failed", cause, isRetryable = true)

    public fun all(): List<Int> = listOf(
        INVALID_EVENT,
        STORAGE_FAILURE,
        QUEUE_FULL,
        DELIVERY_IN_PROGRESS,
        DELIVERY_FAILURE,
        SCHEDULER_FAILURE,
    )
}

internal fun invalidEventQueueState(cause: Throwable): SdkError =
    SdkError.System(EventLoggingErrors.STORAGE_FAILURE, "Event log queue data is invalid", cause, isRetryable = false)
