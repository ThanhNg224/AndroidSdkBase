package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.StateListener
import io.github.thanhng224.sdkbase.core.session.observe
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallbackLifetimeTest {

    private class Callback(private val throwing: Boolean = false) : ResultCallback<String> {
        var deliveries = 0
        override fun onSuccess(value: String) {
            deliveries++
            if (throwing) error("host callback failed")
        }
        override fun onFailure(error: SdkError) {
            deliveries++
        }
    }

    @Test
    fun `completed calls detach owners and allow normal parent completion`() = runTest {
        val parent = Job()
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val callback = Callback()
        repeat(100) { launchCallback(dispatchers, callback, parent) { SdkResult.Success("ok") } }
        advanceUntilIdle()
        assertEquals(100, callback.deliveries)
        assertEquals(0, parent.children.count())
        parent.complete()
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `immediate completion failure and throwing callback all detach owners`() {
        val parent = Job()
        val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined)
        val callback = Callback(throwing = true)
        launchCallback(dispatchers, callback, parent) { SdkResult.Success("ok") }
        launchCallback(dispatchers, callback, parent) { error("block failed") }
        assertEquals(2, callback.deliveries)
        assertEquals(0, parent.children.count())
        parent.complete()
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `cancelled calls detach owners without cancelling the parent`() = runTest {
        val parent = Job()
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val callback = Callback()
        val call = launchCallback(dispatchers, callback, parent) { awaitCancellation() }
        runCurrent()
        call.cancel()
        advanceUntilIdle()
        assertEquals(0, callback.deliveries)
        assertEquals(0, parent.children.count())
        assertTrue(parent.isActive)
        parent.complete()
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `cancelled observers detach owners and allow normal parent completion`() = runTest {
        val parent = Job()
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val state = MutableStateFlow(0)
        var deliveries = 0
        val observers = List(100) { state.observe(dispatchers, StateListener { deliveries++ }, parent) }
        runCurrent()
        assertEquals(100, deliveries)
        observers.forEach { it.cancel() }
        advanceUntilIdle()
        state.value = 1
        runCurrent()
        assertEquals(100, deliveries)
        assertEquals(0, parent.children.count())
        parent.complete()
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `parent cancellation releases both calls and observers`() = runTest {
        val parent = Job()
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val callback = Callback()
        launchCallback(dispatchers, callback, parent) { awaitCancellation() }
        MutableStateFlow(0).observe(dispatchers, StateListener { }, parent)
        runCurrent()
        parent.cancel()
        advanceUntilIdle()
        assertEquals(0, callback.deliveries)
        assertEquals(0, parent.children.count())
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `uncaught child failures are reported without cancelling parent siblings`() {
        val failure = assertThrows(AssertionError::class.java) {
            runTest {
                val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
                val parent = Job()
                val sibling = Job(parent)
                launchCallback(dispatchers, Callback(), parent) {
                    throw AssertionError("uncaught block failure")
                }
                MutableStateFlow(0).observe(dispatchers, StateListener {
                    throw AssertionError("uncaught listener failure")
                }, parent)
                advanceUntilIdle()
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                sibling.complete()
                parent.complete()
                assertTrue(parent.isCompleted)
            }
        }
        assertEquals("uncaught block failure", failure.message)
        assertEquals("uncaught listener failure", failure.suppressed.single().message)
    }
}
