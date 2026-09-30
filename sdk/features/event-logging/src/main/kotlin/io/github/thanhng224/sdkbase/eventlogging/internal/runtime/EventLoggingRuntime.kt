package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.core.call.safeCall
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.FailureMapper
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingRecord
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.DurableEventQueue
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.QueueStatus
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class EventLoggingRuntime(
    private val config: EventLoggingConfig,
    private val gateway: EventLoggingGateway,
    private val environment: SdkEnvironment,
    private val sessionScope: SessionScope,
    sessionId: String,
    sessionStartedAtMillis: Long,
    initialQueue: QueueStatus,
    private val schedulingEnabled: Boolean,
) {
    private val queue = DurableEventQueue(config, environment.dispatchers) { environment.clock.nowMillis() }
    private val commands = Channel<Command>(capacity = minOf(config.maxEvents, CHANNEL_CAPACITY))
    private val drainSignals = Channel<Unit>(capacity = Channel.CONFLATED)
    private val sanitizer = EventRecordSanitizer(config, environment, sessionId, sessionStartedAtMillis)
    private val diagnosticsTracker = DiagnosticsTracker(initialQueue)
    private val drainer = DeliveryDrainer(config, gateway, queue, diagnosticsTracker, schedulingEnabled, ::schedule)
    val diagnostics: StateFlow<EventLoggingDiagnostics> = diagnosticsTracker.diagnostics

    val telemetrySink: TelemetrySink = TelemetrySink { name, attributes ->
        admitTelemetry(name, attributes)
    }

    fun start() {
        sessionScope.launch {
            for (command in commands) {
                try {
                    val result = persist(command.record)
                    command.reply?.complete(result)
                } catch (e: CancellationException) {
                    if (sessionScope.isClosed) {
                        command.reply?.complete(SdkResult.Failure(SdkErrors.sessionClosed()))
                        throw e
                    }
                    val error = SdkErrors.cancelledByUser()
                    diagnosticsTracker.failure(error.code)
                    if (command.reply == null) {
                        diagnosticsTracker.rejected()
                    } else {
                        command.reply.complete(SdkResult.Failure(error))
                    }
                } catch (e: Exception) {
                    val error = EventLoggingErrors.storageFailure(e)
                    diagnosticsTracker.failure(error.code)
                    command.reply?.complete(SdkResult.Failure(error))
                }
            }
        }
        if (schedulingEnabled) {
            sessionScope.launch {
                drainSignals.trySend(Unit)
                for (ignored in drainSignals) {
                    var retryDelay = config.retryInitialDelayMillis
                    while (!sessionScope.isClosed) {
                        val result = try {
                            drainer.tryDrainInBackground()
                        } catch (e: CancellationException) {
                            currentCoroutineContext().ensureActive()
                            val error = EventLoggingErrors.deliveryFailure(e)
                            diagnosticsTracker.failure(error.code)
                            SdkResult.Failure(error)
                        }
                        if (result is SdkResult.Success) {
                            retryDelay = config.retryInitialDelayMillis
                            break
                        }
                        val error = (result as SdkResult.Failure).error
                        if (!error.isRetryable) break
                        delay(retryDelay)
                        retryDelay = if (retryDelay >= config.retryMaxDelayMillis / 2L) {
                            config.retryMaxDelayMillis
                        } else {
                            (retryDelay * 2L).coerceAtMost(config.retryMaxDelayMillis)
                        }
                    }
                }
            }
        }
    }

    suspend fun track(event: EventLoggingEvent): SdkResult<Unit> {
        val record = when (val result = sanitizer.sanitize(event)) {
            is SdkResult.Success -> result.value
            is SdkResult.Failure -> return result
        }
        val reply = CompletableDeferred<SdkResult<Unit>>()
        if (commands.trySend(Command(record, reply)).isFailure) {
            diagnosticsTracker.rejected()
            return SdkResult.Failure(EventLoggingErrors.queueFull())
        }
        return reply.await()
    }

    suspend fun flush(): SdkResult<Unit> = drainer.flush()

    suspend fun bootstrap(): SdkResult<Unit> = if (schedulingEnabled) schedule() else SdkResult.Success(Unit)

    fun close() {
        commands.close()
    }

    private suspend fun persist(record: EventLoggingRecord): SdkResult<Unit> {
        return when (val result = queue.append(record)) {
            is SdkResult.Failure -> {
                diagnosticsTracker.rejectedQueueFailure(result.error.code)
                result
            }

            is SdkResult.Success -> {
                diagnosticsTracker.enqueued()
                diagnosticsTracker.queueUpdated(result.value.eventCount, result.value.byteCount)
                if (result.value.becameNonEmpty && schedulingEnabled) {
                    try {
                        schedule() // The periodic safety net remains installed from startup.
                    } catch (e: CancellationException) {
                        if (sessionScope.isClosed) throw e
                        diagnosticsTracker.failure(EventLoggingErrors.SCHEDULER_FAILURE)
                    }
                }
                if (schedulingEnabled) drainSignals.trySend(Unit)
                SdkResult.Success(Unit)
            }
        }
    }

    private suspend fun schedule(): SdkResult<Unit> {
        val result = safeCall(
            operation = "event delivery scheduling",
            timeoutMillis = config.gatewayTimeoutMillis,
            mapper = FailureMapper { _, error -> EventLoggingErrors.schedulerFailure(error) },
        ) { config.scheduler.schedule(config.namespace) }
        if (result is SdkResult.Failure) diagnosticsTracker.failure(result.error.code)
        return result
    }

    private fun admitTelemetry(name: String, attributes: Map<String, String>) {
        try {
            val event = EventLoggingEvent.Builder(name).attributes(attributes).build()
            val record = when (val result = sanitizer.sanitize(event)) {
                is SdkResult.Success -> result.value

                is SdkResult.Failure -> {
                    diagnosticsTracker.rejected()
                    return
                }
            }
            if (commands.trySend(Command(record, null)).isFailure) diagnosticsTracker.rejected()
        } catch (_: Exception) {
            diagnosticsTracker.rejected()
        }
    }

    private class Command(val record: EventLoggingRecord, val reply: CompletableDeferred<SdkResult<Unit>>?)

    private companion object {
        private const val CHANNEL_CAPACITY: Int = 32
    }
}
