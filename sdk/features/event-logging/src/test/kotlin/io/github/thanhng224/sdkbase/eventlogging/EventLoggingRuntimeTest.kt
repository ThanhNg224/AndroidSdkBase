package io.github.thanhng224.sdkbase.eventlogging

import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.Redactor
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.errorOrNull
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.testing.FakeClock
import io.github.thanhng224.sdkbase.core.testing.SequentialIdGenerator
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.core.time.Clock
import io.github.thanhng224.sdkbase.core.time.IdGenerator
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingRecord
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingCallbackGateway
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.internal.runtime.EventLoggingRuntime
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.DurableEventQueue
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingSession
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventLoggingRuntimeTest {

    @Test
    fun `track stays writable while delivery is suspended`() = runTest {
        val dir = Files.createTempDirectory("event-logging-inflight").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delivered = mutableListOf<String>()
        val gateway = EventLoggingGateway { event ->
            if (event.action == "first") {
                entered.complete(Unit)
                release.await()
            }
            delivered += event.action
            SdkResult.Success(Unit)
        }
        val session = EventLoggingSdk.start(config(dir), gateway, environment(testScheduler)).getOrNull()!!

        assertTrue(session.track(event("first")) is SdkResult.Success)
        runCurrent()
        entered.await()
        assertTrue(session.track(event("second")) is SdkResult.Success)
        assertEquals(emptyList<String>(), delivered)

        release.complete(Unit)
        runCurrent()
        assertTrue(session.flush() is SdkResult.Success)
        assertEquals(listOf("first", "second"), delivered)
        session.close()
        dir.deleteRecursively()
    }

    @Test
    fun `live retry uses the configured capped backoff`() = runTest {
        val dir = Files.createTempDirectory("event-logging-backoff").toFile()
        var calls = 0
        val gateway = EventLoggingGateway {
            calls++
            if (calls < 3) SdkResult.Failure(SdkErrors.networkUnavailable()) else SdkResult.Success(Unit)
        }
        val session = EventLoggingSdk.start(
            config(dir, retryInitialMillis = 10L, retryMaxMillis = 20L),
            gateway,
            environment(testScheduler),
        ).getOrNull()!!

        assertTrue(session.track(event("retry-me")) is SdkResult.Success)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(9L)
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(20L)
        runCurrent()
        assertEquals(3, calls)
        assertEquals(0, session.state.value.pendingEvents)
        session.close()
        dir.deleteRecursively()
    }

    @Test
    fun `cancelling one shot delivery releases the process drain lease`() = runTest {
        val dir = Files.createTempDirectory("event-logging-cancel").toFile()
        val config = config(dir)
        val baseEnvironment = environment(testScheduler)
        val rejecting = EventLoggingGateway {
            SdkResult.Failure(SdkError.Business(3999, "rejected", isRetryable = false))
        }
        val session = EventLoggingSdk.start(config, rejecting, baseEnvironment).getOrNull()!!
        assertTrue(session.track(event("persist-first")) is SdkResult.Success)
        runCurrent()
        session.close()

        val entered = CompletableDeferred<Unit>()
        val blocking = EventLoggingGateway {
            entered.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val delivery = async { EventLoggingSdk.deliverPending(config, blocking, baseEnvironment) }
        runCurrent()
        entered.await()
        delivery.cancelAndJoin()

        val recovered = EventLoggingSdk.deliverPending(config, EventLoggingGateway { SdkResult.Success(Unit) }, baseEnvironment)
        assertTrue(recovered is SdkResult.Success)
        dir.deleteRecursively()
    }

    @Test
    fun `telemetry bridge bounds immediate admission and redacts key named secrets`() = runTest {
        val dir = Files.createTempDirectory("event-logging-telemetry").toFile()
        val received = mutableListOf<EventLoggingRecord>()
        val session = EventLoggingSdk.start(
            config(dir, allowlist = setOf("token", "outcome", "sdk_version")),
            EventLoggingGateway { event ->
                received += event
                SdkResult.Success(Unit)
            },
            environment(testScheduler),
        ).getOrNull()!!

        repeat(40) { index ->
            session.telemetrySink.onEvent(
                "telemetry_$index",
                mapOf(
                    "token" to "raw-secret-$index",
                    "outcome" to "success",
                    "sdk_version" to "1.0.0",
                    "unapproved" to "hidden",
                ),
            )
        }
        assertEquals(8L, session.state.value.rejectedEvents)
        runCurrent()
        assertTrue(received.isNotEmpty())
        assertTrue(received.all { it.attributes.keys == setOf("token", "outcome", "sdk_version") })
        assertTrue(received.all { it.attributes["token"] != "raw-secret-0" })
        assertTrue(received.all { it.attributes["outcome"] == "success" && it.attributes["sdk_version"] == "1.0.0" })
        session.close()
        dir.deleteRecursively()
    }

    @Test
    fun `start confirms scheduler before returning and scheduler errors remain observable`() = runTest {
        val dir = Files.createTempDirectory("event-logging-scheduler").toFile()
        var scheduled = 0
        val scheduler = io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler {
            scheduled++
            SdkResult.Failure(SdkErrors.networkUnavailable())
        }
        val result = EventLoggingSdk.start(
            config(dir, scheduler = scheduler),
            EventLoggingGateway { SdkResult.Success(Unit) },
            environment(testScheduler),
        )
        assertEquals(1, scheduled)
        assertTrue(result is SdkResult.Failure)
        assertEquals(SdkErrors.NETWORK_UNAVAILABLE, result.errorOrNull()?.code)
        dir.deleteRecursively()
    }

    @Test
    fun `Java callback gateway and callback session methods deliver results`() = runTest {
        val dir = Files.createTempDirectory("event-logging-callback").toFile()
        val environment = environment(testScheduler)
        val config = config(dir)
        val gateway = EventLoggingCallbackGateway { _, callback -> callback.onSuccess() }
        var opened: EventLoggingSession? = null
        EventLoggingSdk.start(config, gateway, environment, object : ResultCallback<EventLoggingSession> {
            override fun onSuccess(value: EventLoggingSession) {
                opened = value
            }

            override fun onFailure(error: SdkError) {
                error("start failed: ${error.code}")
            }
        })
        runCurrent()
        val session = opened!!
        var tracked = false
        session.track(event("callback-event"), object : ResultCallback<Unit> {
            override fun onSuccess(value: Unit) {
                tracked = true
            }

            override fun onFailure(error: SdkError) {
                error("track failed: ${error.code}")
            }
        })
        runCurrent()
        assertTrue(tracked)
        session.close()
        runCurrent()
        dir.deleteRecursively()
    }

    @Test
    fun `validated allowlist cannot be changed through caller owned sets`() = runTest {
        val directory = Files.createTempDirectory("event-logging-allowlist").toFile()
        val inputKeys = mutableSetOf("outcome")
        val built = EventLoggingConfig.Builder("allowlist", directory)
            .allowedAttributeKeys(inputKeys)
            .build()
            .getOrNull()!!
        inputKeys += "token"
        assertFalse("token" in built.allowedAttributeKeys)
        try {
            (built.allowedAttributeKeys as MutableSet<String>).add("token")
            error("allowlist was mutable")
        } catch (_: UnsupportedOperationException) {
            // The validated privacy boundary remains immutable.
        }
        directory.deleteRecursively()
    }

    @Test
    fun `sensitive values with spaces are masked before disk and delivery`() = runTest {
        val dir = Files.createTempDirectory("event-logging-sensitive").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var delivered: EventLoggingRecord? = null
        val config = config(dir, allowlist = setOf("password", "outcome"))
        val session = EventLoggingSdk.start(config, EventLoggingGateway { event ->
            delivered = event
            entered.complete(Unit)
            release.await()
            SdkResult.Success(Unit)
        }, environment(testScheduler)).getOrNull()!!

        val event = EventLoggingEvent.Builder("login")
            .attributes(mapOf("password" to " top secret value ", "outcome" to "success"))
            .build()
        assertTrue(session.track(event) is SdkResult.Success)
        runCurrent()
        entered.await()
        val persisted = java.io.File(dir, "event-logging/${config.namespace}/queue.bin").readBytes()
        assertFalse(String(persisted, Charsets.ISO_8859_1).contains("top secret value"))
        assertEquals("***", delivered?.attributes?.get("password"))
        assertEquals("success", delivered?.attributes?.get("outcome"))

        release.complete(Unit)
        runCurrent()
        session.close()
        runCurrent()
        dir.deleteRecursively()
    }

    @Test
    fun `a throwing host redactor is contained and its exception text is not exposed`() = runTest {
        val dir = Files.createTempDirectory("event-logging-redactor").toFile()
        val config = EventLoggingConfig.Builder("redactor", dir)
            .allowedAttributeKeys(setOf("outcome"))
            .redactor(Redactor { throw IllegalStateException("private-redactor-detail") })
            .build()
            .getOrNull()!!
        val session = EventLoggingSdk.start(config, EventLoggingGateway { SdkResult.Success(Unit) }, environment(testScheduler))
            .getOrNull()!!

        val result = session.track(event("safe-name"))

        assertEquals(EventLoggingErrors.INVALID_EVENT, result.errorOrNull()?.code)
        assertFalse(result.errorOrNull()?.reason.orEmpty().contains("private-redactor-detail"))
        session.close()
        runCurrent()
        dir.deleteRecursively()
    }

    @Test
    fun `invalid session identifiers fail before scheduler registration`() = runTest {
        val dir = Files.createTempDirectory("event-logging-session-id").toFile()
        var scheduleCalls = 0
        val scheduler = io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler {
            scheduleCalls++
            SdkResult.Success(Unit)
        }
        val environment = SdkEnvironment.Builder()
            .dispatchers(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
            .idGenerator(IdGenerator { "person@example.com" })
            .build()

        val result = EventLoggingSdk.start(
            config(dir, scheduler = scheduler),
            EventLoggingGateway { SdkResult.Success(Unit) },
            environment,
        )

        assertEquals(EventLoggingErrors.INVALID_EVENT, result.errorOrNull()?.code)
        assertEquals(0, scheduleCalls)
        dir.deleteRecursively()
    }

    @Test
    fun `scheduler cancellation during startup closes and propagates`() = runTest {
        val dir = Files.createTempDirectory("event-logging-start-cancel").toFile()
        val scheduler = io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler {
            throw CancellationException("scheduler canceled")
        }
        var cancelled = false
        try {
            EventLoggingSdk.start(config(dir, scheduler = scheduler), EventLoggingGateway { SdkResult.Success(Unit) }, environment(testScheduler))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        dir.deleteRecursively()
    }

    @Test
    fun `scheduler cancellation after durable append does not report a duplicate risk or kill the actor`() = runTest {
        val dir = Files.createTempDirectory("event-logging-post-append-cancel").toFile()
        var schedules = 0
        val scheduler = io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler {
            schedules++
            if (schedules == 1) SdkResult.Success(Unit) else throw CancellationException("late wake-up cancellation")
        }
        val delivered = mutableListOf<String>()
        val session = EventLoggingSdk.start(
            config(dir, allowlist = setOf("outcome"), scheduler = scheduler),
            EventLoggingGateway { event ->
                delivered += event.action
                SdkResult.Success(Unit)
            },
            environment(testScheduler),
        ).getOrNull()!!

        assertTrue(session.track(event("durable-first")) is SdkResult.Success)
        assertTrue(session.track(event("actor-alive")) is SdkResult.Success)
        runCurrent()
        assertEquals(listOf("durable-first", "actor-alive"), delivered)
        session.close()
        runCurrent()
        dir.deleteRecursively()
    }

    @Test
    fun `cancellation before append commit never reports success and keeps actor usable`() = runTest {
        val dir = Files.createTempDirectory("event-logging-pre-commit-cancel").toFile()
        val cfg = config(dir)
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val stored = DurableEventQueue(cfg, dispatchers) { 100L }
        stored.append(EventLoggingRecord("accepted", "prior-session", 100L, 0L, "accepted", "info", null, emptyMap()))
        var clockCalls = 0
        val environment = SdkEnvironment.Builder().dispatchers(dispatchers)
            .clock(Clock {
                clockCalls++
                if (clockCalls == 2) throw CancellationException("pre-commit clock cancellation")
                100L
            })
            .idGenerator(SequentialIdGenerator("event-")).build()
        val scope = SessionScope(dispatchers, environment.logger.tagged("test"))
        val runtime = EventLoggingRuntime(cfg, EventLoggingGateway { SdkResult.Success(Unit) },
            environment, scope, "test-session", 100L, stored.load().getOrNull()!!, false)
        runtime.start()
        try {
            assertTrue(runtime.track(event("uncommitted")) is SdkResult.Failure)
            assertEquals(listOf("accepted"), stored.load().getOrNull()!!.events.map { it.action })
            assertTrue(runtime.track(event("next")) is SdkResult.Success)
            assertEquals(listOf("accepted", "next"), stored.load().getOrNull()!!.events.map { it.action })
        } finally {
            runtime.close()
            scope.close()
            runCurrent()
            dir.deleteRecursively()
        }
    }

    private fun config(
        directory: java.io.File,
        namespace: String = "test_queue",
        allowlist: Set<String> = emptySet(),
        scheduler: io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler =
            io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler.None,
        retryInitialMillis: Long = 5L,
        retryMaxMillis: Long = 20L,
    ): EventLoggingConfig = EventLoggingConfig.Builder(namespace, directory)
        .allowedAttributeKeys(allowlist)
        .scheduler(scheduler)
        .retryInitialDelayMillis(retryInitialMillis)
        .retryMaxDelayMillis(retryMaxMillis)
        .build()
        .getOrNull()!!

    private fun environment(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): SdkEnvironment = SdkEnvironment.Builder()
        .dispatchers(TestDispatcherProvider(StandardTestDispatcher(scheduler)))
        .clock(FakeClock(100L))
        .idGenerator(SequentialIdGenerator("test-"))
        .build()

    private fun event(action: String): EventLoggingEvent = EventLoggingEvent.Builder(action).build()
}
