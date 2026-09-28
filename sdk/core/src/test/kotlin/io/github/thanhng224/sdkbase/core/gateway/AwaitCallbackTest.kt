package io.github.thanhng224.sdkbase.core.gateway

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [awaitCallback]/[awaitCompletion] are the suspend side of every Java-callable gateway: only the
 * first terminal call may resume the coroutine, it may arrive from any thread, and a callback that
 * arrives after the awaiting coroutine was cancelled must be silently ignored rather than crash.
 */
class AwaitCallbackTest {

    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "callback-thread")
    }

    @After
    fun tearDown() {
        callbackExecutor.shutdownNow()
    }

    @Test
    fun successResumesWithValue() = runBlocking {
        val result = awaitCallback<String> { callback -> callback.onSuccess("ok") }

        assertEquals(SdkResult.Success("ok"), result)
    }

    @Test
    fun failureResumesWithError() = runBlocking {
        val error = SdkErrors.networkUnavailable()

        val result = awaitCallback<String> { callback -> callback.onFailure(error) }

        assertEquals(SdkResult.Failure(error), result)
    }

    @Test
    fun secondCallIsIgnored() = runBlocking {
        val result = awaitCallback<String> { callback ->
            callback.onSuccess("first")
            callback.onFailure(SdkErrors.networkUnavailable())
        }

        assertEquals(SdkResult.Success("first"), result)
    }

    @Test
    fun callbackFromAnotherThreadResumes() = runBlocking {
        val result = awaitCallback<String> { callback ->
            callbackExecutor.execute { callback.onSuccess("from-thread") }
        }

        assertEquals(SdkResult.Success("from-thread"), result)
    }

    @Test
    fun callbackAfterCancellationIsIgnored() = runBlocking {
        val started = CountDownLatch(1)
        var storedCallback: GatewayCallback<String>? = null

        // A dedicated thread: `started.await` below blocks the calling thread synchronously, so the
        // launched coroutine must not share it or the two would deadlock against each other.
        val job = launch(Dispatchers.Default) {
            awaitCallback<String> { callback ->
                storedCallback = callback
                started.countDown()
            }
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        job.cancelAndJoin()

        // Must not throw even though the awaiting coroutine is already cancelled.
        storedCallback?.onSuccess("late")
        Unit
    }

    @Test
    fun completionSuccessIsUnit() = runBlocking {
        val result = awaitCompletion { callback -> callback.onSuccess() }

        assertEquals(SdkResult.Success(Unit), result)
    }
}
