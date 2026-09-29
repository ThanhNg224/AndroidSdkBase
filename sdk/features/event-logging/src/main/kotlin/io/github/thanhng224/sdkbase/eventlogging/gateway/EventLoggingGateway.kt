package io.github.thanhng224.sdkbase.eventlogging.gateway

import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.gateway.awaitCompletion
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingRecord

/** Host-owned transport for an already filtered, redacted, and durably stored event. */
public fun interface EventLoggingGateway {
    public suspend fun deliver(event: EventLoggingRecord): SdkResult<Unit>
}

/** Java-implementable callback form of [EventLoggingGateway]. */
public fun interface EventLoggingCallbackGateway {
    public fun deliver(event: EventLoggingRecord, callback: CompletionCallback)
}

/** Adapts the Java callback gateway through core's cancellation-safe callback bridge. */
public fun EventLoggingCallbackGateway.asGateway(): EventLoggingGateway {
    val delegate = this
    return EventLoggingGateway { event ->
        awaitCompletion { callback -> delegate.deliver(event, callback) }
    }
}
