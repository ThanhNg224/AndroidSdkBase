package io.github.thanhng224.sdkbase.core.testing

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks a host's gateway implementation against the contract the SDK relies on, so a host finds a
 * violation in its own unit tests instead of as a hung or crashed session.
 *
 * Framework-agnostic: a violation throws [AssertionError], which JUnit, kotlin.test and TestNG all
 * report as a failure. Everything runs in real time, so give slow fakes a larger `timeoutMillis`.
 *
 * ```
 * @Test fun `my gateway honours the contract`() {
 *     val gateway = MyOtpGateway(fakeApi)
 *     GatewayContract.assertReturns { gateway.requestOtp("+84901234567") }
 *     GatewayContract.assertCancellable { gateway.requestOtp("+84901234567") }
 * }
 * ```
 */
public object GatewayContract {

    public const val DEFAULT_TIMEOUT_MILLIS: Long = 5_000
    public const val DEFAULT_SETTLE_MILLIS: Long = 200

    // Time for the call to reach its first suspension point before it is cancelled.
    private const val CANCEL_GRACE_MILLIS = 50L

    /**
     * Suspend gateway: returns — never throws — within [timeoutMillis], and gives back the
     * [SdkResult] it returned.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T> assertReturns(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        call: suspend () -> SdkResult<T>,
    ): SdkResult<T> = onWorker { scope ->
        val deferred = scope.async { call() }
        val result = try {
            runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.await() } }
        } catch (t: Throwable) {
            throw AssertionError("gateway threw $t", t)
        }
        result ?: throw AssertionError("gateway did not return within $timeoutMillis ms")
    }

    /**
     * Suspend gateway: cancelling it mid-flight ends it within [timeoutMillis]. The SDK's own
     * timeouts rely on this cooperative cancellation.
     */
    @JvmStatic
    @JvmOverloads
    public fun assertCancellable(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        call: suspend () -> SdkResult<*>,
    ) {
        onWorker { scope ->
            val started = CountDownLatch(1)
            val thrown = AtomicReference<Throwable?>(null)
            val deferred: Deferred<*> = scope.async {
                started.countDown()
                try {
                    call()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    thrown.set(t)
                    throw t
                }
            }
            started.await(timeoutMillis, TimeUnit.MILLISECONDS)
            Thread.sleep(CANCEL_GRACE_MILLIS)
            deferred.cancel()
            val ended = runBlocking { withTimeoutOrNull(timeoutMillis) { deferred.join() } }
            if (ended == null) throw AssertionError("gateway ignored cancellation")
            thrown.get()?.let { throw AssertionError("gateway threw $it", it) }
        }
    }

    /**
     * Callback gateway: [register] does not throw; exactly one terminal call arrives within
     * [timeoutMillis] and no further call within [settleMillis] after it. Returns the delivered
     * result.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T> assertCallsBackOnce(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        register: (GatewayCallback<T>) -> Unit,
    ): SdkResult<T> {
        val recorder = TerminalCalls<T>()
        val callback = object : GatewayCallback<T> {
            override fun onSuccess(value: T) = recorder.record(SdkResult.Success(value))
            override fun onFailure(error: SdkError) = recorder.record(SdkResult.Failure(error))
        }
        return recorder.await(timeoutMillis, settleMillis) { register(callback) }
    }

    /** [assertCallsBackOnce] for a [CompletionCallback]. */
    @JvmStatic
    @JvmOverloads
    public fun assertCompletesOnce(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        register: (CompletionCallback) -> Unit,
    ): SdkResult<Unit> {
        val recorder = TerminalCalls<Unit>()
        val callback = object : CompletionCallback {
            override fun onSuccess() = recorder.record(SdkResult.Success(Unit))
            override fun onFailure(error: SdkError) = recorder.record(SdkResult.Failure(error))
        }
        return recorder.await(timeoutMillis, settleMillis) { register(callback) }
    }

    /**
     * Runs [block] with a scope on a private thread pool that is shut down (interrupting threads
     * stuck in a blocking call) afterwards, so a misbehaving gateway cannot leak threads or hang
     * the test.
     */
    private fun <R> onWorker(block: (CoroutineScope) -> R): R {
        val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "gateway-contract").apply { isDaemon = true }
        }
        try {
            return block(CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher()))
        } finally {
            executor.shutdownNow()
        }
    }

    private class TerminalCalls<T> {
        private val count = AtomicInteger(0)
        private val result = AtomicReference<SdkResult<T>?>(null)
        private val first = CountDownLatch(1)

        fun record(delivered: SdkResult<T>) {
            if (count.incrementAndGet() == 1) {
                result.set(delivered)
                first.countDown()
            }
        }

        fun await(timeoutMillis: Long, settleMillis: Long, register: () -> Unit): SdkResult<T> {
            try {
                register()
            } catch (t: Throwable) {
                throw AssertionError("register threw $t", t)
            }
            if (!first.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw AssertionError("gateway never called back")
            }
            Thread.sleep(settleMillis)
            val calls = count.get()
            if (calls != 1) throw AssertionError("gateway called back $calls times")
            return checkNotNull(result.get())
        }
    }
}
