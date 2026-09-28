package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.TaggedLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * The only owner of a session's coroutines. A feature session is built on exactly one
 * [SessionScope] instead of hand-rolling its own [CoroutineScope]/teardown: long-lived work (timers,
 * collectors) runs on [coroutineScope], a fire-and-forget UI-driven call goes through [launch], a
 * suspend operation goes through [ifOpen], and its Java-callable twin goes through [call]. [close]
 * is the single teardown every one of those honors.
 *
 * After [close] returns `true` for the first time: [isClosed] is `true`, [launch] is a no-op,
 * [ifOpen] returns `Failure(SdkErrors.sessionClosed())` without running its block, [call] delivers
 * `Failure(SdkErrors.sessionClosed())` on [DispatcherProvider.main] without running its block, and
 * [coroutineScope] — along with any work already running on it, including one in flight through
 * [call] or [ifOpen] — is cancelled. A pending [call] never has its callback fire once [close] has
 * won the race (same guarantee [launchCallback] gives a cancelled [Cancellable]).
 */
@SdkInternalApi
public class SessionScope(
    public val dispatchers: DispatcherProvider,
    logger: TaggedLogger,
) {

    private val closed = AtomicBoolean(false)

    /** `true` once [close] has run for the first time. */
    public val isClosed: Boolean
        get() = closed.get()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable) { "uncaught failure in session" }
    }

    /**
     * The scope for a session's long-lived work (timers, collectors): a [SupervisorJob] on
     * [DispatcherProvider.default], with an uncaught-exception handler that logs instead of
     * crashing the process — a throw from one piece of session work never takes down another.
     */
    public val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default + exceptionHandler)

    /** Fire-and-forget: runs [block] on [coroutineScope]. A no-op once [isClosed]. */
    public fun launch(block: suspend CoroutineScope.() -> Unit) {
        if (isClosed) return
        coroutineScope.launch(block = block)
    }

    /** [block] while the session is open; `Failure(SdkErrors.sessionClosed())` once [isClosed]. */
    public suspend fun <T> ifOpen(block: suspend () -> SdkResult<T>): SdkResult<T> =
        if (isClosed) SdkResult.Failure(SdkErrors.sessionClosed()) else block()

    /**
     * [block]'s Java-callable twin: delivers to [callback] on [DispatcherProvider.main] via
     * [launchCallback], parented to [coroutineScope] so [close] cancels a call still in flight. Once
     * already [isClosed] at the time of the call, delivers `Failure(SdkErrors.sessionClosed())`
     * without running [block] and without a parent, so that delivery itself cannot be cancelled by
     * the very [close] that already happened.
     */
    public fun <T> call(callback: ResultCallback<T>, block: suspend () -> SdkResult<T>): Cancellable =
        if (isClosed) {
            launchCallback(dispatchers, callback) { SdkResult.Failure(SdkErrors.sessionClosed()) }
        } else {
            launchCallback(dispatchers, callback, parent = coroutineScope.coroutineContext.job) {
                ifOpen(block)
            }
        }

    /** [flow]'s Java-callable observer, parented to [coroutineScope] so [close] stops it. */
    public fun <S> observe(flow: StateFlow<S>, listener: StateListener<S>): Cancellable =
        flow.observe(dispatchers, listener, parent = coroutineScope.coroutineContext.job)

    /**
     * Cancels [coroutineScope] and everything running on it. Idempotent and atomic: returns `true`
     * only for the call that actually closed the scope, `false` for every call after it.
     */
    public fun close(): Boolean {
        val firstCall = closed.compareAndSet(false, true)
        if (firstCall) {
            coroutineScope.cancel()
        }
        return firstCall
    }
}
