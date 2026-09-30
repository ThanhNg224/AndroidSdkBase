package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.eventlogging.internal.storage.QueueStatus
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingDiagnostics
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal class DiagnosticsTracker(initialQueue: QueueStatus) {
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

    fun enqueued() {
        enqueuedCount.incrementAndGet()
    }

    fun delivered() {
        deliveredCount.incrementAndGet()
    }

    fun rejected() {
        rejectedCount.incrementAndGet()
        updateRejected()
    }

    fun queueUpdated(count: Int, bytes: Int, delivering: Boolean = false) {
        mutableDiagnostics.update { current ->
            EventLoggingDiagnostics(
                count,
                bytes,
                enqueuedCount.get(),
                deliveredCount.get(),
                rejectedCount.get(),
                failures.get(),
                current.lastFailureCode,
                delivering,
            )
        }
    }

    fun rejectedQueueFailure(code: Int) {
        rejectedCount.incrementAndGet()
        failure(code)
    }

    fun failure(code: Int, delivering: Boolean = false) {
        failures.incrementAndGet()
        mutableDiagnostics.update { current ->
            EventLoggingDiagnostics(
                current.pendingEvents,
                current.pendingBytes,
                enqueuedCount.get(),
                deliveredCount.get(),
                rejectedCount.get(),
                failures.get(),
                code,
                delivering,
            )
        }
    }

    private fun updateRejected() {
        mutableDiagnostics.update { current ->
            EventLoggingDiagnostics(
                current.pendingEvents,
                current.pendingBytes,
                enqueuedCount.get(),
                deliveredCount.get(),
                rejectedCount.get(),
                failures.get(),
                current.lastFailureCode,
                current.isDelivering,
            )
        }
    }
}
