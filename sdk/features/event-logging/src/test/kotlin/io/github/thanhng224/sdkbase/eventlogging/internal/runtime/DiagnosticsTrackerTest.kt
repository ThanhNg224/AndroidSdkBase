package io.github.thanhng224.sdkbase.eventlogging.internal.runtime

import io.github.thanhng224.sdkbase.eventlogging.internal.storage.QueueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTrackerTest {

    @Test
    fun `diagnostics track queue counts failures rejections and delivery flags`() {
        val tracker = DiagnosticsTracker(QueueStatus(emptyList(), 0))

        tracker.enqueued()
        tracker.queueUpdated(count = 1, bytes = 48, delivering = true)
        tracker.delivered()
        tracker.queueUpdated(count = 0, bytes = 0)
        tracker.rejected()
        tracker.failure(code = 2101)

        val diagnostics = tracker.diagnostics.value
        assertEquals(0, diagnostics.pendingEvents)
        assertEquals(0, diagnostics.pendingBytes)
        assertEquals(1L, diagnostics.enqueuedEvents)
        assertEquals(1L, diagnostics.deliveredEvents)
        assertEquals(1L, diagnostics.rejectedEvents)
        assertEquals(1L, diagnostics.deliveryFailures)
        assertEquals(2101, diagnostics.lastFailureCode)
        assertFalse(diagnostics.isDelivering)
    }

    @Test
    fun `queue updates preserve failure code and replace delivery flag`() {
        val tracker = DiagnosticsTracker(QueueStatus(emptyList(), 0))

        tracker.failure(code = 2102)
        tracker.queueUpdated(count = 2, bytes = 96, delivering = true)
        assertTrue(tracker.diagnostics.value.isDelivering)
        tracker.queueUpdated(count = 2, bytes = 96, delivering = false)

        val diagnostics = tracker.diagnostics.value
        assertEquals(2102, diagnostics.lastFailureCode)
        assertEquals(1L, diagnostics.deliveryFailures)
        assertEquals(2, diagnostics.pendingEvents)
        assertFalse(diagnostics.isDelivering)
    }
}
