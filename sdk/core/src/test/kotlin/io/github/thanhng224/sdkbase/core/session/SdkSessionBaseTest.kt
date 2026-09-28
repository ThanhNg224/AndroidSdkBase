package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SdkSessionBaseTest {

    private val mainExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { Thread(it, "main-thread") }
    private val defaultExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { Thread(it, "default-thread") }
    private val mainDispatcher: CoroutineDispatcher = mainExecutor.asCoroutineDispatcher()
    private val defaultDispatcher: CoroutineDispatcher = defaultExecutor.asCoroutineDispatcher()
    private val dispatchers = TestDispatcherProvider(mainDispatcher, defaultDispatcher)

    @After
    fun tearDown() {
        mainExecutor.shutdownNow()
        defaultExecutor.shutdownNow()
    }

    private class FakeSession(
        scope: SessionScope,
        store: StateStore<Int>,
    ) : SdkSessionBase<Int>(scope, store) {
        val onCloseCount = AtomicInteger(0)

        override fun onClose() {
            onCloseCount.incrementAndGet()
        }
    }

    private fun newScope() = SessionScope(dispatchers, SdkLogger.NoOp.tagged("Test"))

    @Test
    fun onCloseRunsExactlyOnce() {
        val session = FakeSession(newScope(), StateStore(0))

        session.close()
        session.close()
        session.close()

        assertEquals(1, session.onCloseCount.get())
    }

    @Test
    fun observeStateStopsAfterClose() {
        val store = StateStore(0)
        val session = FakeSession(newScope(), store)
        val values = CopyOnWriteArrayList<Int>()
        val delivered = CountDownLatch(1)

        session.observeState { state ->
            values += state
            if (state == 1) delivered.countDown()
        }

        runBlocking { store.withLock { update { 1 } } }
        assertEquals(true, delivered.await(2, TimeUnit.SECONDS))

        session.close()
        Thread.sleep(100)

        runBlocking { store.withLock { update { 2 } } }
        Thread.sleep(100)

        assertFalse(values.contains(2))
    }
}
