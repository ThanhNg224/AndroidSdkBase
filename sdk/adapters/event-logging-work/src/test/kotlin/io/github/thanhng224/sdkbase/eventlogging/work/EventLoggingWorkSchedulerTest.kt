package io.github.thanhng224.sdkbase.eventlogging.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLoggingWorkSchedulerTest {
    @Test
    fun workerInputContainsOnlyTheOpaqueQueueNamespace() {
        val namespace = "queue_81c93f4b"

        val input = EventLoggingWorkScheduler.workerInput(namespace)

        assertEquals(setOf(EventLoggingWorkScheduler.INPUT_NAMESPACE), input.keyValueMap.keys)
        assertEquals(namespace, input.getString(EventLoggingWorkScheduler.INPUT_NAMESPACE))
    }

    @Test
    fun workNameDoesNotExposeNamespaceAndIsStablePerQueue() {
        val namespace = "queue_81c93f4b"

        val first = EventLoggingWorkScheduler.uniqueWorkName(namespace)

        assertEquals(first, EventLoggingWorkScheduler.uniqueWorkName(namespace))
        assertNotEquals(namespace, first)
        assertTrue(first.matches(Regex("sdkbase-event-logging-[0-9a-f]{64}")))
    }

    @Test
    fun differentQueuesReceiveDifferentWorkNames() {
        assertNotEquals(
            EventLoggingWorkScheduler.uniqueWorkName("queue-a"),
            EventLoggingWorkScheduler.uniqueWorkName("queue-b"),
        )
    }

    @Test
    fun periodicRecoveryUsesMinimumIntervalAndHasASeparateHashedName() {
        val namespace = "queue-81c93f4b"
        val oneTimeName = EventLoggingWorkScheduler.uniqueWorkName(namespace)
        val periodicName = EventLoggingWorkScheduler.periodicWorkName(namespace)

        assertEquals(15L * 60L * 1_000L, EventLoggingWorkScheduler.PERIODIC_INTERVAL_MILLIS)
        assertNotEquals(oneTimeName, periodicName)
        assertTrue(periodicName.endsWith(oneTimeName.substringAfterLast('-')))
        assertNotEquals(namespace, periodicName)
    }

    @Test
    fun namespaceMustBeShortAndUrlSafe() {
        assertTrue(EventLoggingWorkScheduler.isSafeNamespace("queue_81c93f4b"))
        assertTrue(!EventLoggingWorkScheduler.isSafeNamespace("customer id"))
        assertTrue(!EventLoggingWorkScheduler.isSafeNamespace("x".repeat(65)))
    }
}
