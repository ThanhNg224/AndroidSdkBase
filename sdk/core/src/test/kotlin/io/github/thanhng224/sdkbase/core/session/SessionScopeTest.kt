package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.testing.RecordingLogSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SessionScope] is the only owner of a session's coroutines, so its threading and cancel-vs-close
 * semantics are proven here with two distinct real single-thread dispatchers, the same pattern
 * [io.github.thanhng224.sdkbase.core.call.LaunchCallbackTest] uses for [call].
 */
class SessionScopeTest {

    private val defaultExecutor: ExecutorService = namedExecutor("default-thread")
    private val mainExecutor: ExecutorService = namedExecutor("main-thread")
    private val defaultDispatcher: CoroutineDispatcher = defaultExecutor.asCoroutineDispatcher()
    private val mainDispatcher: CoroutineDispatcher = mainExecutor.asCoroutineDispatcher()

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainDispatcher
        override val default: CoroutineDispatcher = defaultDispatcher
        override val io: CoroutineDispatcher = defaultDispatcher
    }

    private fun namedExecutor(name: String): ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, name) }

    @After
    fun tearDown() {
        defaultExecutor.shutdownNow()
        mainExecutor.shutdownNow()
    }

    private fun scope(sink: RecordingLogSink = RecordingLogSink()): SessionScope {
        val logger = SdkLogger.Builder().sink(sink).build().tagged("Test")
        return SessionScope(dispatchers, logger)
    }

    private class RecordingCallback<T> : ResultCallback<T> {
        val successes = mutableListOf<T>()
        val failures = mutableListOf<SdkError>()
        val threads = mutableListOf<String>()
        val latch = CountDownLatch(1)

        override fun onSuccess(value: T) {
            successes += value
            threads += Thread.currentThread().name
            latch.countDown()
        }

        override fun onFailure(error: SdkError) {
            failures += error
            threads += Thread.currentThread().name
            latch.countDown()
        }
    }

    @Test
    fun ifOpenRunsBlockWhileOpen(): Unit = runBlocking {
        val session = scope()

        val result = session.ifOpen { SdkResult.Success("ok") }

        assertEquals(SdkResult.Success("ok"), result)
    }

    @Test
    fun ifOpenAfterCloseFailsWithSessionClosed(): Unit = runBlocking {
        val session = scope()
        val invocations = AtomicInteger(0)
        session.close()

        val result = session.ifOpen {
            invocations.incrementAndGet()
            SdkResult.Success("ok")
        }

        assertEquals(0, invocations.get())
        val failure = result as SdkResult.Failure
        assertEquals(SdkErrors.SESSION_CLOSED, failure.error.code)
    }

    @Test
    fun callAfterCloseDeliversSessionClosedOnMain() {
        val session = scope()
        session.close()
        val callback = RecordingCallback<String>()

        session.call(callback) { SdkResult.Success("ok") }

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        assertEquals(SdkErrors.SESSION_CLOSED, callback.failures.single().code)
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.threads.single().startsWith("main-thread"))
    }

    @Test
    fun closeCancelsPendingCall() {
        val session = scope()
        val callback = RecordingCallback<String>()
        val started = CountDownLatch(1)

        session.call(callback) {
            started.countDown()
            awaitCancellation()
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        session.close()

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())
    }

    @Test
    fun launchAfterCloseIsNoOp() {
        val session = scope()
        val invocations = AtomicInteger(0)
        session.close()

        session.launch { invocations.incrementAndGet() }

        Thread.sleep(150)
        assertEquals(0, invocations.get())
    }

    @Test
    fun closeIsIdempotentAndReportsFirstCaller() {
        val session = scope()

        assertTrue(session.close())
        assertFalse(session.close())
        assertFalse(session.close())
    }

    @Test
    fun uncaughtFailureIsLoggedNotThrown() {
        val sink = RecordingLogSink()
        val session = scope(sink)

        session.launch { error("boom") }

        val deadline = System.currentTimeMillis() + 2_000
        while (sink.records.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)

        assertTrue(sink.messages().any { it.contains("uncaught failure in session") })
    }

    @Test
    fun closeDuringIfOpenReturnsSessionClosedAndCancelsBlock() {
        val session = scope()
        val started = CountDownLatch(1)
        val blockCancelled = AtomicBoolean(false)
        val ranAfterGate = AtomicBoolean(false)
        val resultRef = AtomicReference<SdkResult<String>>()
        val done = CountDownLatch(1)

        // ifOpen is suspend, so it needs its own caller coroutine, independent of the session's
        // own coroutineScope - a plain background thread running runBlocking gives one.
        Thread {
            runBlocking {
                val result = session.ifOpen {
                    started.countDown()
                    try {
                        awaitCancellation()
                    } finally {
                        blockCancelled.set(true)
                    }
                    @Suppress("UNREACHABLE_CODE")
                    ranAfterGate.set(true)
                    @Suppress("UNREACHABLE_CODE")
                    SdkResult.Success("ok")
                }
                resultRef.set(result)
                done.countDown()
            }
        }.start()

        assertTrue(started.await(2, TimeUnit.SECONDS))
        session.close()

        assertTrue(done.await(2, TimeUnit.SECONDS))
        val failure = resultRef.get() as SdkResult.Failure
        assertEquals(SdkErrors.SESSION_CLOSED, failure.error.code)
        assertTrue(blockCancelled.get())
        assertFalse(ranAfterGate.get())
    }

    @Test
    fun callerCancellationPropagatesAndCancelsBlock() {
        val session = scope()
        val started = CountDownLatch(1)
        val blockCancelled = CountDownLatch(1)
        val callerSawCancellation = AtomicBoolean(false)
        val callerJob = Job()
        val callerScope = CoroutineScope(callerJob + defaultDispatcher)

        callerScope.launch {
            try {
                session.ifOpen {
                    started.countDown()
                    try {
                        awaitCancellation()
                    } finally {
                        blockCancelled.countDown()
                    }
                    @Suppress("UNREACHABLE_CODE")
                    SdkResult.Success("ok")
                }
            } catch (e: CancellationException) {
                callerSawCancellation.set(true)
            }
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        callerJob.cancel()

        assertTrue(blockCancelled.await(2, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertTrue(callerSawCancellation.get())

        // The session itself was never closed - a later ifOpen still runs.
        val result = runBlocking { session.ifOpen { SdkResult.Success("still-open") } }
        assertEquals(SdkResult.Success("still-open"), result)
    }

    @Test
    fun blockThrowingIsReturnedAsUnknown() {
        val sink = RecordingLogSink()
        val session = scope(sink)
        val boom = IllegalStateException("boom")

        val result = runBlocking { session.ifOpen<String> { throw boom } }

        val failure = result as SdkResult.Failure
        assertEquals(SdkErrors.UNKNOWN, failure.error.code)
        // Not `assertEquals(boom, failure.error.cause)`: crossing the `async`/`await()` boundary
        // makes kotlinx-coroutines' debug-mode stack-trace recovery clone the exception (same type
        // and message, a new instance) rather than hand back the identical object - the same reason
        // LaunchCallbackTest never awaits across a coroutine boundary for its own equivalent check.
        val cause = failure.error.cause
        assertTrue(cause is IllegalStateException)
        assertEquals("boom", cause?.message)
        assertTrue(sink.messages().any { it.contains("session operation failed") })
    }

    @Test
    fun closeCancelsCoroutineScopeWork() {
        val session = scope()
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)

        session.coroutineScope.launch {
            started.countDown()
            try {
                awaitCancellation()
            } finally {
                cancelled.countDown()
            }
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        session.close()

        assertTrue(cancelled.await(2, TimeUnit.SECONDS))
    }
}
