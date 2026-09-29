package io.github.thanhng224.sdkbase.eventlogging.work

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.work.internal.WorkDeliveryDecision
import io.github.thanhng224.sdkbase.eventlogging.work.internal.deliveryDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkDeliveryDecisionTest {
    @Test
    fun successfulDrainCompletesWork() {
        assertEquals(WorkDeliveryDecision.SUCCESS, deliveryDecision(SdkResult.Success(Unit)))
    }

    @Test
    fun retryableFailureRequestsWorkManagerRetry() {
        val failure = SdkResult.Failure(SdkError.System(2001, "temporary", isRetryable = true))

        assertEquals(WorkDeliveryDecision.RETRY, deliveryDecision(failure))
    }

    @Test
    fun permanentFailureFailsWorkWithoutRetryLoop() {
        val failure = SdkResult.Failure(SdkError.Common(1001, "invalid config"))

        assertEquals(WorkDeliveryDecision.FAILURE, deliveryDecision(failure))
    }
}
