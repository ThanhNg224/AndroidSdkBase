package io.github.thanhng224.sdkbase.eventlogging.internal.storage

import io.github.thanhng224.sdkbase.core.logging.Redactor
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.errorOrNull
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.core.testing.FakeClock
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingRecord
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DurableEventQueueTest {
    @get:Rule val temp = TemporaryFolder()
    private fun config(root: File, maxEvents: Int = 100, maxBytes: Int = 1_048_576,
        keys: Set<String> = setOf("outcome", "token"), retention: Long = 100_000,
        redactor: Redactor = Redactor.None) = EventLoggingConfig.Builder("test", root)
        .allowedAttributeKeys(keys).maxEvents(maxEvents).maxBytes(maxBytes)
        .retentionMillis(retention).redactor(redactor).build().getOrNull()!!
    private fun record(id: String, time: Long = 10, attrs: Map<String, String> = emptyMap()) =
        EventLoggingRecord(id, "session", time, 0, "action", "info", "screen", attrs)
    private fun data(root: File) = File(root, "event-logging/test/queue.bin")

    @Test fun restartPreservesFifoAndIds() = runTest {
        val root = temp.newFolder()
        val cfg = config(root)
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val first = DurableEventQueue(cfg, dispatchers) { 10 }
        assertTrue(first.append(record("one")) is SdkResult.Success)
        assertTrue(first.append(record("two")) is SdkResult.Success)
        val restarted = DurableEventQueue(cfg, dispatchers) { 10 }
        assertEquals(listOf("one", "two"), restarted.load().getOrNull()!!.events.map { it.id })
    }

    @Test fun ackByIdKeepsConcurrentlyAppendedTail() = runTest {
        val root = temp.newFolder()
        val cfg = config(root)
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val foreground = DurableEventQueue(cfg, dispatchers) { 10 }
        val other = DurableEventQueue(cfg, dispatchers) { 10 }
        foreground.append(record("head"))
        val lease = foreground.acquireDrainLease()!!
        try {
            other.append(record("tail"))
            assertEquals(listOf("tail"), foreground.remove("head").getOrNull()!!.events.map { it.id })
            assertEquals(listOf("tail"), other.load().getOrNull()!!.events.map { it.id })
        } finally { foreground.releaseDrainLease(lease) }
    }

    @Test fun concurrentWritersNeverOverwriteAcceptedRecords() = runTest {
        val root = temp.newFolder()
        val cfg = config(root)
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val queues = List(4) { DurableEventQueue(cfg, dispatchers) { 10 } }
        val outcomes = coroutineScope {
            List(40) { id -> async { queues[id % 4].append(record("id-$id")) } }.awaitAll()
        }
        assertTrue(outcomes.all { it is SdkResult.Success })
        val ids = queues.first().load().getOrNull()!!.events.map { it.id }
        assertEquals(40, ids.size); assertEquals(40, ids.toSet().size)
    }

    @Test fun fullQueueRejectsNewEventWithoutDroppingAcceptedHead() = runTest {
        val root = temp.newFolder()
        val queue = DurableEventQueue(config(root, maxEvents = 1),
            TestDispatcherProvider(StandardTestDispatcher(testScheduler))) { 10 }
        queue.append(record("head"))
        val failure = queue.append(record("tail")) as SdkResult.Failure
        assertEquals(EventLoggingErrors.QUEUE_FULL, failure.error.code)
        assertEquals(listOf("head"), queue.load().getOrNull()!!.events.map { it.id })
    }

    @Test fun byteLimitRejectsWithoutPartialCommit() = runTest {
        val root = temp.newFolder()
        val queue = DurableEventQueue(config(root, maxBytes = 1024),
            TestDispatcherProvider(StandardTestDispatcher(testScheduler))) { 10 }
        val failure = queue.append(record("large", attrs = mapOf("outcome" to "x".repeat(1200)))) as SdkResult.Failure
        assertEquals(EventLoggingErrors.QUEUE_FULL, failure.error.code)
        assertTrue(queue.load().getOrNull()!!.events.isEmpty())
        assertFalse(data(root).exists())
    }

    @Test fun retentionExpiresOnlyOldRecords() = runTest {
        val root = temp.newFolder()
        val clock = FakeClock(10)
        val queue = DurableEventQueue(config(root, retention = 100),
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)), clock::nowMillis)
        queue.append(record("expired", 10))
        clock.advanceBy(1000)
        queue.append(record("fresh", clock.nowMillis()))
        assertEquals(listOf("fresh"), queue.load().getOrNull()!!.events.map { it.id })
    }

    @Test fun corruptionFailsClosedAndPreservesEvidence() = runTest {
        val root = temp.newFolder()
        val queue = DurableEventQueue(config(root), TestDispatcherProvider(StandardTestDispatcher(testScheduler))) { 10 }
        val file = data(root).apply { parentFile!!.mkdirs(); writeText("broken queue") }
        val result = queue.load()
        assertEquals(EventLoggingErrors.STORAGE_FAILURE, result.errorOrNull()?.code)
        assertEquals(false, result.errorOrNull()?.isRetryable)
        assertEquals("broken queue", file.readText())
    }

    @Test fun truncatedQueueDoesNotBecomeAnEmptyAcceptedQueue() = runTest {
        val root = temp.newFolder()
        val queue = DurableEventQueue(config(root), TestDispatcherProvider(StandardTestDispatcher(testScheduler))) { 10 }
        assertTrue(queue.append(record("accepted")) is SdkResult.Success)
        val bytes = data(root).readBytes()
        for (length in listOf(0, 3, bytes.size - 1)) {
            data(root).writeBytes(bytes.copyOf(length))
            val result = queue.load()
            assertEquals(EventLoggingErrors.STORAGE_FAILURE, result.errorOrNull()?.code)
            assertEquals(false, result.errorOrNull()?.isRetryable)
            assertArrayEquals(bytes.copyOf(length), data(root).readBytes())
        }
    }

    @Test fun tighteningAllowlistRemovesAttributesFromMemoryAndDisk() = runTest {
        val root = temp.newFolder()
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val original = DurableEventQueue(config(root), dispatchers) { 10 }
        original.append(record("event", attrs = mapOf("outcome" to "ok", "token" to "**12")))
        val restricted = DurableEventQueue(config(root, keys = setOf("outcome")), dispatchers) { 10 }
        assertEquals(mapOf("outcome" to "ok"), restricted.load().getOrNull()!!.events.single().attributes)
        assertFalse(data(root).readBytes().toString(Charsets.ISO_8859_1).contains("token"))
    }

    @Test fun onlyOneDrainerCanOwnQueueAndReleaseAllowsRestart() = runTest {
        val root = temp.newFolder()
        val cfg = config(root)
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val first = DurableEventQueue(cfg, dispatchers) { 10 }
        val second = DurableEventQueue(cfg, dispatchers) { 10 }
        val lease = first.acquireDrainLease()!!
        assertNull(second.acquireDrainLease())
        first.releaseDrainLease(lease)
        val next = second.acquireDrainLease()!!
        second.releaseDrainLease(next)
    }

    @Test fun acceptedPayloadDoesNotRunCustomScrubberAgainAcrossReadsAndRestart() = runTest {
        val root = temp.newFolder()
        val cfg = config(root, redactor = Redactor { "host-$it" })
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val queue = DurableEventQueue(cfg, dispatchers) { 10 }
        queue.append(EventLoggingRecord("one", "session", 10, 0, "host-action", "info", "host-screen",
            mapOf("outcome" to "host-ok")))
        val bytes = data(root).readBytes()
        repeat(5) {
            val value = DurableEventQueue(cfg, dispatchers) { 10 }.load().getOrNull()!!.events.single()
            assertEquals("host-action", value.action); assertEquals("host-screen", value.screenId)
            assertEquals("host-ok", value.attributes["outcome"])
        }
        assertArrayEquals(bytes, data(root).readBytes())
    }

    @Test fun deliveredAttributesCannotMutateStoredPayload() {
        val input = linkedMapOf("outcome" to "ok")
        val event = record("one", attrs = input)
        input["outcome"] = "changed"
        assertEquals("ok", event.attributes["outcome"])
        try { (event.attributes as MutableMap)["outcome"] = "bad"; fail("mutable attributes") }
        catch (_: UnsupportedOperationException) { }
    }
}
