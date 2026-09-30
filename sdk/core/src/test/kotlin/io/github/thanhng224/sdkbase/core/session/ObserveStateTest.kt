package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** [StateFlow.observe] delivers on a real main-like dispatcher thread, proven the same way [launchCallback] is. */
class ObserveStateTest {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "main-thread") }
    private val mainDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainDispatcher
        override val default: CoroutineDispatcher = mainDispatcher
        override val io: CoroutineDispatcher = mainDispatcher
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `emitsCurrentThenChanges - on main`() {
        val state = MutableStateFlow(1)
        val received = mutableListOf<Int>()
        val threads = mutableListOf<String>()
        val firstLatch = CountDownLatch(1)
        val latch = CountDownLatch(2)

        val cancellable = state.observe(
            dispatchers,
            StateListener {
                received += it
                threads += Thread.currentThread().name
                firstLatch.countDown()
                latch.countDown()
            },
        )
        // Waits for the current-value delivery before mutating, otherwise state.value = 2 can race
        // ahead of collect() actually subscribing and the "1" emission is lost, not just delayed.
        assertTrue(firstLatch.await(2, TimeUnit.SECONDS))
        state.value = 2

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), received)
        assertTrue(threads.all { it.startsWith("main-thread") })
        cancellable.cancel()
    }

    @Test
    fun `throwingListenerKeepsReceiving`() {
        val state = MutableStateFlow(1)
        val received = mutableListOf<Int>()
        val firstLatch = CountDownLatch(1)
        val latch = CountDownLatch(2)

        val cancellable = state.observe(
            dispatchers,
            StateListener {
                received += it
                firstLatch.countDown()
                latch.countDown()
                error("listener boom")
            },
        )
        assertTrue(firstLatch.await(2, TimeUnit.SECONDS))
        state.value = 2

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), received)
        cancellable.cancel()
    }

    @Test
    fun `cancelStops`() {
        val state = MutableStateFlow(1)
        val received = mutableListOf<Int>()
        val firstLatch = CountDownLatch(1)

        val cancellable = state.observe(
            dispatchers,
            StateListener {
                received += it
                firstLatch.countDown()
            },
        )
        assertTrue(firstLatch.await(2, TimeUnit.SECONDS))
        cancellable.cancel()

        Thread.sleep(50)
        state.value = 2
        Thread.sleep(150)

        assertEquals(listOf(1), received)
    }

    @Test
    fun `parentCancellationStops`() {
        val state = MutableStateFlow(1)
        val received = mutableListOf<Int>()
        val firstLatch = CountDownLatch(1)
        val parent = Job()

        state.observe(
            dispatchers,
            StateListener {
                received += it
                firstLatch.countDown()
            },
            parent = parent,
        )
        assertTrue(firstLatch.await(2, TimeUnit.SECONDS))
        parent.cancel()

        Thread.sleep(50)
        state.value = 2
        Thread.sleep(150)

        assertEquals(listOf(1), received)
    }
}
