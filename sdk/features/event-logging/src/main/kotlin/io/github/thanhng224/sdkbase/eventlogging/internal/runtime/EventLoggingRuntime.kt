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
import io.github.thanhng224.sdkbase.eventlogging.internal.redaction.EventAttributeRedactor
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.DurableEventQueue
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.QueueStatus
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingDiagnostics
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class EventLoggingRuntime(
    private val config: EventLoggingConfig,
    private val gateway: EventLoggingGateway,
    private val environment: SdkEnvironment,
    private val sessionScope: SessionScope,
    private val sessionId: String,
    private val sessionStartedAtMillis: Long,
    initialQueue: QueueStatus,
    private val schedulingEnabled: Boolean,
) {
    private val queue = DurableEventQueue(config, environment.dispatchers) { environment.clock.nowMillis() }
    private val commands = Channel<Command>(capacity = minOf(config.maxEvents, CHANNEL_CAPACITY))
    private val drainSignals = Channel<Unit>(capacity = Channel.CONFLATED)
    private val drainMutex = Mutex()
    private val enqueuedCount = AtomicLong(0L)
    private val deliveredCount = AtomicLong(0L)
    private val rejectedCount = AtomicLong(0L)
    private val failures = AtomicLong(0L)
    private val mutableDiagnostics = MutableStateFlow(
        EventLoggingDiagnostics(
            initialQueue.events.size,
            initialQueue.byteCount,
            0L,
            0L,
            0L,
            0L,
            null,
            false,
        ),
    )
    val diagnostics: StateFlow<EventLoggingDiagnostics> = mutableDiagnostics

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
                    recordFailure(error.code)
                    if (command.reply == null) {
                        rejectedCount.incrementAndGet()
                        updateRejected()
                    } else {
                        command.reply.complete(SdkResult.Failure(error))
                    }
                } catch (e: Exception) {
                    val error = EventLoggingErrors.storageFailure(e)
                    recordFailure(error.code)
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
                            tryDrainInBackground()
                        } catch (e: CancellationException) {
                            currentCoroutineContext().ensureActive()
                            val error = EventLoggingErrors.deliveryFailure(e)
                            recordFailure(error.code)
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
        val record = when (val result = sanitize(event)) {
            is SdkResult.Success -> result.value
            is SdkResult.Failure -> return result
        }
        val reply = CompletableDeferred<SdkResult<Unit>>()
        if (commands.trySend(Command(record, reply)).isFailure) {
            rejectedCount.incrementAndGet()
            updateRejected()
            return SdkResult.Failure(EventLoggingErrors.queueFull())
        }
        return reply.await()
    }

    suspend fun flush(): SdkResult<Unit> {
        return drainMutex.withLock { drainOnce(scheduleRetry = true) }
    }

    suspend fun bootstrap(): SdkResult<Unit> = if (schedulingEnabled) schedule() else SdkResult.Success(Unit)

    fun close() {
        commands.close()
    }

    private suspend fun persist(record: EventLoggingRecord): SdkResult<Unit> {
        return when (val result = queue.append(record)) {
            is SdkResult.Failure -> {
                rejectedCount.incrementAndGet()
                updateQueueFailure(result.error.code)
                result
            }

            is SdkResult.Success -> {
                enqueuedCount.incrementAndGet()
                updateQueue(result.value.eventCount, result.value.byteCount)
                if (result.value.becameNonEmpty && schedulingEnabled) {
                    try {
                        schedule() // The periodic safety net remains installed from startup.
                    } catch (e: CancellationException) {
                        if (sessionScope.isClosed) throw e
                        recordFailure(EventLoggingErrors.SCHEDULER_FAILURE)
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
        if (result is SdkResult.Failure) recordFailure(result.error.code)
        return result
    }

    private suspend fun tryDrainInBackground(): SdkResult<Unit> {
        if (!drainMutex.tryLock()) return SdkResult.Failure(EventLoggingErrors.deliveryInProgress())
        return try {
            drainOnce(scheduleRetry = false)
        } finally {
            drainMutex.unlock()
        }
    }

    private suspend fun drainOnce(scheduleRetry: Boolean): SdkResult<Unit> {
        val lease = try {
            queue.acquireDrainLease()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = EventLoggingErrors.storageFailure(e)
            recordFailure(error.code)
            return SdkResult.Failure(error)
        } ?: run {
            val status = queue.load()
            if (status is SdkResult.Success) updateQueue(status.value.events.size, status.value.byteCount)
            return SdkResult.Failure(EventLoggingErrors.deliveryInProgress())
        }

        try {
            while (true) {
                val loaded = queue.load()
                if (loaded is SdkResult.Failure) {
                    recordFailure(loaded.error.code)
                    return loaded
                }
                val status = (loaded as SdkResult.Success).value
                updateQueue(status.events.size, status.byteCount, delivering = status.events.isNotEmpty())
                val head = status.events.firstOrNull() ?: return SdkResult.Success(Unit)
                val outcome = safeCall(
                    operation = "event delivery",
                    timeoutMillis = config.gatewayTimeoutMillis,
                ) { gateway.deliver(head) }
                if (outcome is SdkResult.Failure) {
                    recordFailure(outcome.error.code, delivering = false)
                    if (scheduleRetry && schedulingEnabled && outcome.error.isRetryable) schedule()
                    return outcome
                }
                when (val removed = queue.remove(head.id)) {
                    is SdkResult.Failure -> {
                        recordFailure(removed.error.code, delivering = false)
                        return removed
                    }

                    is SdkResult.Success -> {
                        deliveredCount.incrementAndGet()
                        updateQueue(removed.value.events.size, removed.value.byteCount, delivering = false)
                    }
                }
            }
        } finally {
            queue.releaseDrainLease(lease)
        }
    }

    private fun sanitize(event: EventLoggingEvent): SdkResult<EventLoggingRecord> {
        return try {
            if (event.action.isBlank()) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("action is blank"))
            }
            val level = event.level.lowercase()
            if (level !in ALLOWED_LEVELS) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("level is unsupported"))
            }
            val action = redact(event.action.take(config.maxActionLength)).take(config.maxActionLength)
            if (action.isBlank()) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("action is blank after sanitization"))
            }
            val screen = event.screenId?.let { redact(it.take(MAX_SCREEN_LENGTH)).take(MAX_SCREEN_LENGTH) }
            val attrs = LinkedHashMap<String, String>()
            fun addAttributes(source: Map<String, String>) {
                for ((key, value) in source) {
                    if (key !in config.allowedAttributeKeys || key.length > MAX_ATTRIBUTE_KEY_LENGTH) continue
                    if (attrs.size >= config.maxAttributes && key !in attrs) continue
                    val hostRedacted = config.redactor.redact(value.take(config.maxValueLength))
                        .take(config.maxValueLength)
                    attrs[key] = EventAttributeRedactor.redact(
                        key,
                        hostRedacted,
                        EventAttributeRedactor.default,
                    ).take(config.maxValueLength)
                }
            }
            addAttributes(config.commonAttributes)
            addAttributes(event.attributes)
            val id = environment.idGenerator.newId()
            if (!IDENTIFIER_PATTERN.matches(id) || !IDENTIFIER_PATTERN.matches(sessionId)) {
                return SdkResult.Failure(EventLoggingErrors.invalidEvent("identifier is invalid"))
            }
            val now = environment.clock.nowMillis().coerceAtLeast(0L)
            val elapsed = (now - sessionStartedAtMillis).coerceAtLeast(0L)
            SdkResult.Success(EventLoggingRecord(id, sessionId, now, elapsed, action, level, screen, attrs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SdkResult.Failure(EventLoggingErrors.invalidEvent("event could not be sanitized"))
        }
    }

    private fun redact(value: String): String =
        EventAttributeRedactor.default.redact(config.redactor.redact(value))

    private fun admitTelemetry(name: String, attributes: Map<String, String>) {
        try {
            val event = EventLoggingEvent.Builder(name).attributes(attributes).build()
            val record = when (val result = sanitize(event)) {
                is SdkResult.Success -> result.value

                is SdkResult.Failure -> {
                    rejectedCount.incrementAndGet()
                    updateRejected()
                    return
                }
            }
            if (commands.trySend(Command(record, null)).isFailure) {
                rejectedCount.incrementAndGet()
                updateRejected()
            }
        } catch (_: Exception) {
            rejectedCount.incrementAndGet()
            updateRejected()
        }
    }

    private fun updateQueue(count: Int, bytes: Int, delivering: Boolean = false) {
        mutableDiagnostics.update { current ->
            EventLoggingDiagnostics(
                count, bytes, enqueuedCount.get(), deliveredCount.get(), rejectedCount.get(), failures.get(),
                current.lastFailureCode, delivering,
            )
        }
    }

    private fun updateQueueFailure(code: Int) {
        recordFailure(code)
    }

    private fun updateRejected() {
        mutableDiagnostics.update { current ->
            EventLoggingDiagnostics(
                current.pendingEvents, current.pendingBytes, enqueuedCount.get(),
                deliveredCount.get(), rejectedCount.get(),
                failures.get(), current.lastFailureCode, current.isDelivering,
            )
        }
    }

    private fun recordFailure(code: Int, delivering: Boolean = false) {
        failures.incrementAndGet()
        mutableDiagnostics.update { current ->
            EventLoggingDiagnostics(
                current.pendingEvents, current.pendingBytes, enqueuedCount.get(),
                deliveredCount.get(), rejectedCount.get(),
                failures.get(), code, delivering,
            )
        }
    }

    private class Command(val record: EventLoggingRecord, val reply: CompletableDeferred<SdkResult<Unit>>?)

    private companion object {
        private const val MAX_SCREEN_LENGTH: Int = 256
        private const val MAX_ATTRIBUTE_KEY_LENGTH: Int = 64
        private val ALLOWED_LEVELS = setOf("debug", "info", "warn", "error")
        private const val CHANNEL_CAPACITY: Int = 32
        private val IDENTIFIER_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
