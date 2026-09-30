package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.session.StateListener
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingDiagnostics
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingSession
import kotlinx.coroutines.flow.StateFlow

/**
 * Routes every operation of [EventLoggingRuntime] through the one [SessionScope]. It implements
 * [EventLoggingSession] directly rather than extending `SdkSessionBase`: the diagnostics snapshot is
 * maintained by the runtime's own actor (atomic counters plus a `MutableStateFlow`), not by a
 * `StateStore` that callers mutate under a lock.
 */
internal class DefaultEventLoggingSession(
    private val scope: SessionScope,
    private val runtime: EventLoggingRuntime,
) : EventLoggingSession {

    override val state: StateFlow<EventLoggingDiagnostics> = runtime.diagnostics

    override val telemetrySink: TelemetrySink = runtime.telemetrySink

    override suspend fun track(event: EventLoggingEvent): SdkResult<Unit> = scope.ifOpen { runtime.track(event) }

    override suspend fun flush(): SdkResult<Unit> = scope.ifOpen { runtime.flush() }

    override fun observeState(listener: StateListener<EventLoggingDiagnostics>): Cancellable =
        scope.observe(state, listener)

    override fun track(event: EventLoggingEvent, callback: ResultCallback<Unit>): Cancellable =
        scope.call(callback) { runtime.track(event) }

    override fun flush(callback: ResultCallback<Unit>): Cancellable = scope.call(callback) { runtime.flush() }

    /** Closes this session and cancels its actor and pending operations. */
    override fun close() {
        runtime.close()
        scope.close()
    }
}
