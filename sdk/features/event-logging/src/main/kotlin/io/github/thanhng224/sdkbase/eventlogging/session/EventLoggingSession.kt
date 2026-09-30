package io.github.thanhng224.sdkbase.eventlogging.session

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SdkSession
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * Active owner of one bounded event queue and its session jobs. [state] is a metadata-only
 * snapshot of queue health; a host can fake this interface in its own tests.
 */
public interface EventLoggingSession : SdkSession<EventLoggingDiagnostics>, AutoCloseable {

    /** Alias for [state] emphasizing that it contains metadata only. */
    public val diagnostics: StateFlow<EventLoggingDiagnostics>
        get() = state

    /** Bounded, non-blocking telemetry bridge into the durable event queue. */
    public val telemetrySink: TelemetrySink

    /** Persists the event before returning success; delivery is at least once. */
    public suspend fun track(event: EventLoggingEvent): SdkResult<Unit>

    /** Drains persisted records in order until empty or the first gateway failure. */
    public suspend fun flush(): SdkResult<Unit>

    /** Java-callable form of [track]. */
    public fun track(event: EventLoggingEvent, callback: ResultCallback<Unit>): Cancellable

    /** Java-callable form of [flush]. */
    public fun flush(callback: ResultCallback<Unit>): Cancellable
}
