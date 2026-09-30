package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.core.call.safeCall
import io.github.thanhng224.sdkbase.core.error.FailureMapper
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.DurableEventQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DeliveryDrainer(
    private val config: EventLoggingConfig,
    private val gateway: EventLoggingGateway,
    private val queue: DurableEventQueue,
    private val diagnostics: DiagnosticsTracker,
    private val schedulingEnabled: Boolean,
    private val schedule: suspend () -> SdkResult<Unit>,
) {
    private val drainMutex = Mutex()

    suspend fun flush(): SdkResult<Unit> = drainMutex.withLock { drainOnce(scheduleRetry = true) }

    suspend fun tryDrainInBackground(): SdkResult<Unit> {
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
            diagnostics.failure(error.code)
            return SdkResult.Failure(error)
        } ?: run {
            val status = queue.load()
            if (status is SdkResult.Success) diagnostics.queueUpdated(status.value.events.size, status.value.byteCount)
            return SdkResult.Failure(EventLoggingErrors.deliveryInProgress())
        }

        try {
            while (true) {
                val loaded = queue.load()
                if (loaded is SdkResult.Failure) {
                    diagnostics.failure(loaded.error.code)
                    return loaded
                }
                val status = (loaded as SdkResult.Success).value
                diagnostics.queueUpdated(status.events.size, status.byteCount, delivering = status.events.isNotEmpty())
                val head = status.events.firstOrNull() ?: return SdkResult.Success(Unit)
                val outcome = safeCall(
                    operation = "event delivery",
                    timeoutMillis = config.gatewayTimeoutMillis,
                ) { gateway.deliver(head) }
                if (outcome is SdkResult.Failure) {
                    diagnostics.failure(outcome.error.code, delivering = false)
                    if (scheduleRetry && schedulingEnabled && outcome.error.isRetryable) schedule()
                    return outcome
                }
                when (val removed = queue.remove(head.id)) {
                    is SdkResult.Failure -> {
                        diagnostics.failure(removed.error.code, delivering = false)
                        return removed
                    }

                    is SdkResult.Success -> {
                        diagnostics.delivered()
                        diagnostics.queueUpdated(removed.value.events.size, removed.value.byteCount, delivering = false)
                    }
                }
            }
        } finally {
            queue.releaseDrainLease(lease)
        }
    }
}
