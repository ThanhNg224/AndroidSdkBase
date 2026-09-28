package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [launchCallback] is the one primitive every Java-callable outbound entry point is built from, so
 * its threading, cancel-vs-deliver and containment semantics are proven here directly, with two
 * distinct real single-thread dispatchers so "delivered on main, not the worker thread" is a
 * thread-identity assertion, not just a value check. A blocking [CountDownLatch] inside [block] -
 * not a coroutine suspension - is used to hold a real dispatcher thread mid-flight where a race with
 * [Cancellable.cancel] needs to be forced deterministically.
 */
class LaunchCallbackTest {

    private val uncaught = mutableListOf<Throwable>()
    private val defaultExecutor: ExecutorService = namedExecutor("default-thread")
    private val mainExecutor: ExecutorService = namedExecutor("main-thread") { uncaught += it }
    private val defaultDispatcher: CoroutineDispatcher = defaultExecutor.asCoroutineDispatcher()
    private val mainDispatcher: CoroutineDispatcher = mainExecutor.asCoroutineDispatcher()

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainDispatcher
        override val default: CoroutineDispatcher = defaultDispatcher
        override val io: CoroutineDispatcher = defaultDispatcher
    }

    private fun namedExecutor(name: String, onUncaught: (Throwable) -> Unit = {}): ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, name).apply { setUncaughtExceptionHandler { _, e -> onUncaught(e) } }
        }

    @After
    fun tearDown() {
        defaultExecutor.shutdownNow()
        mainExecutor.shutdownNow()
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
    fun `deliversSuccessOnMain - block runs on default, callback lands on main`() {
        val blockThread = AtomicReference<String>()
        val callback = RecordingCallback<String>()

        launchCallback(dispatchers, callback) {
            blockThread.set(Thread.currentThread().name)
            SdkResult.Success("ok")
        }

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        // kotlinx.coroutines appends " @coroutine#N" to the thread name in debug mode (on whenever
        // JVM assertions are enabled, as Gradle's unit test JVM does) - a prefix check keeps the
        // thread-identity assertion meaningful without depending on that debug-mode detail.
        assertTrue(blockThread.get().startsWith("default-thread"))
        assertEquals("ok", callback.successes.single())
        assertTrue(callback.threads.single().startsWith("main-thread"))
    }

    @Test
    fun `deliversFailureOnMain`() {
        val callback = RecordingCallback<String>()
        val error = SdkErrors.networkUnavailable()

        launchCallback(dispatchers, callback) { SdkResult.Failure(error) }

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        assertEquals(error, callback.failures.single())
        assertTrue(callback.threads.single().startsWith("main-thread"))
    }

    @Test
    fun `deliversExactlyOnce`() {
        val count = AtomicInteger(0)
        val latch = CountDownLatch(1)
        val callback = object : ResultCallback<String> {
            override fun onSuccess(value: String) {
                count.incrementAndGet()
                latch.countDown()
            }

            override fun onFailure(error: SdkError) {
                count.incrementAndGet()
                latch.countDown()
            }
        }

        launchCallback(dispatchers, callback) { SdkResult.Success("ok") }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertEquals(1, count.get())
    }

    @Test
    fun `blockThrowingIsDeliveredAsUnknown`() {
        val callback = RecordingCallback<String>()
        val boom = IllegalStateException("boom")

        launchCallback(dispatchers, callback) { throw boom }

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        val error = callback.failures.single()
        assertEquals(SdkErrors.UNKNOWN, error.code)
        assertEquals(boom, error.cause)
    }

    @Test
    fun `cancelBeforeDeliveryNeverCallsBack`() {
        val callback = RecordingCallback<String>()
        val started = CountDownLatch(1)

        val cancellable = launchCallback(dispatchers, callback) {
            started.countDown()
            awaitCancellation()
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        cancellable.cancel()

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())
    }

    @Test
    fun `parentCancellationNeverCallsBack`() {
        val callback = RecordingCallback<String>()
        val parent = Job()
        val started = CountDownLatch(1)

        launchCallback(dispatchers, callback, parent = parent) {
            started.countDown()
            awaitCancellation()
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        parent.cancel()

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())
    }

    @Test
    fun `undeliveredSuccessIsReleased - cancel wins the race, onUndelivered gets the value once`() {
        val callback = RecordingCallback<String>()
        val ready = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val undelivered = mutableListOf<String>()

        val cancellable = launchCallback(
            dispatchers = dispatchers,
            callback = callback,
            onUndelivered = { undelivered += it },
        ) {
            ready.countDown()
            // A blocking wait, not a suspension: simulates the value having already been computed
            // and about to be handed off, so job.cancel() below cannot interrupt it cooperatively -
            // the claim race is decided purely by the shared AtomicBoolean, as documented.
            proceed.await(2, TimeUnit.SECONDS)
            SdkResult.Success("late")
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        cancellable.cancel()
        proceed.countDown()

        val deadline = System.currentTimeMillis() + 2_000
        while (undelivered.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)

        assertEquals(listOf("late"), undelivered)
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())
    }

    @Test
    fun `onUndelivered is not called when the value was actually delivered`() {
        val callback = RecordingCallback<String>()
        val undelivered = mutableListOf<String>()

        launchCallback(dispatchers, callback, onUndelivered = { undelivered += it }) {
            SdkResult.Success("ok")
        }

        assertTrue(callback.latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertTrue(undelivered.isEmpty())
    }

    @Test
    fun `throwingCallbackIsContained`() {
        val invoked = CountDownLatch(1)
        val callback = object : ResultCallback<String> {
            override fun onSuccess(value: String) {
                invoked.countDown()
                error("callback boom")
            }

            override fun onFailure(error: SdkError) = invoked.countDown()
        }

        launchCallback(dispatchers, callback) { SdkResult.Success("ok") }

        assertTrue(invoked.await(2, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertTrue("callback's throw must never reach the dispatcher thread's handler", uncaught.isEmpty())
    }

    @Test
    fun `cancelIsIdempotent`() {
        val callback = RecordingCallback<String>()
        val started = CountDownLatch(1)

        val cancellable = launchCallback(dispatchers, callback) {
            started.countDown()
            awaitCancellation()
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        cancellable.cancel()
        cancellable.cancel()

        assertFalse(callback.latch.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `cancelOnMainAfterBlockFinishedNeverCallsBack`() {
        val callback = RecordingCallback<String>()
        val undelivered = mutableListOf<String>()
        val mainBusy = CountDownLatch(1)
        val blockReady = CountDownLatch(1)
        val releaseBlock = CountDownLatch(1)

        // Occupies the only main-executor thread, so neither the cancel task queued below nor a
        // delivery hop that queues onto main later can run until it is released.
        mainExecutor.execute { mainBusy.await(5, TimeUnit.SECONDS) }

        val cancellable = launchCallback(dispatchers, callback, onUndelivered = { undelivered += it }) {
            blockReady.countDown()
            // A blocking wait, not a suspension: block has not returned yet, so nothing has queued
            // a delivery hop onto main yet either - that only happens once this returns below.
            releaseBlock.await(5, TimeUnit.SECONDS)
            SdkResult.Success("ok")
        }

        assertTrue(blockReady.await(2, TimeUnit.SECONDS))

        // Queued onto the SAME single-thread main executor while it is still busy: this is
        // therefore guaranteed to sit ahead, in the queue, of any delivery hop - which cannot even
        // be queued until `block` (still held open above) returns - reproducing "the host calls
        // cancel() on main before the queued delivery runs".
        val cancelRan = CountDownLatch(1)
        mainExecutor.execute {
            cancellable.cancel()
            cancelRan.countDown()
        }

        releaseBlock.countDown()
        mainBusy.countDown()
        assertTrue(cancelRan.await(2, TimeUnit.SECONDS))

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())
        assertEquals(listOf("ok"), undelivered)
    }

    @Test
    fun `parentCancelledAfterBlockFinishedReleasesValue`() {
        val callback = RecordingCallback<String>()
        val undelivered = mutableListOf<String>()
        val parent = Job()
        val mainBusy = CountDownLatch(1)
        val blockReady = CountDownLatch(1)
        val releaseBlock = CountDownLatch(1)

        mainExecutor.execute { mainBusy.await(5, TimeUnit.SECONDS) }

        launchCallback(dispatchers, callback, parent = parent, onUndelivered = { undelivered += it }) {
            blockReady.countDown()
            releaseBlock.await(5, TimeUnit.SECONDS)
            SdkResult.Success("ok")
        }

        assertTrue(blockReady.await(2, TimeUnit.SECONDS))

        val cancelRan = CountDownLatch(1)
        mainExecutor.execute {
            parent.cancel()
            cancelRan.countDown()
        }

        releaseBlock.countDown()
        mainBusy.countDown()
        assertTrue(cancelRan.await(2, TimeUnit.SECONDS))

        assertFalse(callback.latch.await(300, TimeUnit.MILLISECONDS))
        assertTrue(callback.successes.isEmpty())
        assertTrue(callback.failures.isEmpty())
        assertEquals(listOf("ok"), undelivered)
    }
}
