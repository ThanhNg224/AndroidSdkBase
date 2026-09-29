package io.github.thanhng224.sdkbase.eventlogging.delivery

import io.github.thanhng224.sdkbase.core.result.SdkResult
/** Host provided wake-up hook for a durable queue; an adapter may implement it with WorkManager. */
public fun interface EventDeliveryScheduler {
    /** Schedules delivery for [namespace]. Calls are coalesced to queue-empty transitions. */
    public suspend fun schedule(namespace: String): SdkResult<Unit>

    public companion object {
        public val None: EventDeliveryScheduler = EventDeliveryScheduler { SdkResult.Success(Unit) }
    }
}
