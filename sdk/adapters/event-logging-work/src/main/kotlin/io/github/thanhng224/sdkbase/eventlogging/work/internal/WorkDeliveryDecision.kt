package io.github.thanhng224.sdkbase.eventlogging.work.internal

import io.github.thanhng224.sdkbase.core.result.SdkResult

internal enum class WorkDeliveryDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal fun deliveryDecision(result: SdkResult<*>): WorkDeliveryDecision = when (result) {
    is SdkResult.Success -> WorkDeliveryDecision.SUCCESS

    is SdkResult.Failure -> if (result.error.isRetryable) {
        WorkDeliveryDecision.RETRY
    } else {
        WorkDeliveryDecision.FAILURE
    }
}
