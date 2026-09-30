package io.github.thanhng224.sdkbase.eventlogging.session

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SdkSession
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.session.StateListener
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.internal.runtime.EventLoggingRuntime
import kotlinx.coroutines.flow.StateFlow

/** Active owner of one bounded event queue and its session jobs. */
public class EventLoggingSession internal constructor(
    private val scope: SessionScope,
    private val runtime: EventLoggingRuntime,
) : SdkSession<EventLoggingDiagnostics>, AutoCloseable {

    /** Metadata-only queue health. */
    override val state: StateFlow<EventLoggingDiagnostics> = runtime.diagnostics

    /** Alias for [state] emphasizing that it contains metadata only. */
    public val diagnostics: StateFlow<EventLoggingDiagnostics>
        get() = state

    /** Bounded, non-blocking telemetry bridge into the durable event queue. */
    public val telemetrySink: TelemetrySink = runtime.telemetrySink

    /** Persists the event before returning success; delivery is at least once. */
    public suspend fun track(event: EventLoggingEvent): SdkResult<Unit> = scope.ifOpen { runtime.track(event) }

    /** Drains persisted records in order until empty or the first gateway failure. */
    public suspend fun flush(): SdkResult<Unit> = scope.ifOpen { runtime.flush() }

    override fun observeState(listener: StateListener<EventLoggingDiagnostics>): Cancellable =
        scope.observe(state, listener)

    /** Java-callable form of [track]. */
    public fun track(event: EventLoggingEvent, callback: ResultCallback<Unit>): Cancellable =
        scope.call(callback) { runtime.track(event) }

    /** Java-callable form of [flush]. */
    public fun flush(callback: ResultCallback<Unit>): Cancellable = scope.call(callback) { runtime.flush() }

    /** Closes this session and cancels its actor and pending operations. */
    override fun close() {
        runtime.close()
        scope.close()
    }
}
