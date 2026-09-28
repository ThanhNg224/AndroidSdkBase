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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
    private val logger: TaggedLogger,
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

    /**
     * Runs [block] as a child of the session rather than of the caller: `Failure(sessionClosed())`
     * without running [block] once already [isClosed]; otherwise [block] runs on
     * [DispatcherProvider.default] via `coroutineScope.async`, so a [close] that lands while it is
     * still running cancels it too — closing the race a plain "check, then call" would leave open
     * — and this suspend call itself returns `Failure(SdkErrors.sessionClosed())` rather than
     * whatever [block] was doing.
     *
     * The caller's own cancellation is distinguished from the session's: if the calling coroutine
     * is no longer active when [block] is interrupted, that is the caller being cancelled, not the
     * session closing — [block] is cancelled and the [CancellationException] is rethrown so it
     * propagates normally, instead of being reported as [SdkErrors.sessionClosed].
     *
     * A non-cancellation [Exception] from [block] never reaches the caller: it is logged and turned
     * into `Failure(SdkErrors.unknown(cause))`, the same containment rule
     * [io.github.thanhng224.sdkbase.core.call.launchCallback] follows for its own `block`. An
     * [Error] is never caught.
     */
    public suspend fun <T> ifOpen(block: suspend () -> SdkResult<T>): SdkResult<T> {
        if (isClosed) return SdkResult.Failure(SdkErrors.sessionClosed())

        val deferred = coroutineScope.async { block() }
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            if (coroutineContext[Job]?.isActive == false) {
                deferred.cancel()
                throw e
            }
            SdkResult.Failure(SdkErrors.sessionClosed())
        } catch (e: Exception) {
            logger.e(e) { "session operation failed" }
            SdkResult.Failure(SdkErrors.unknown(cause = e))
        }
    }

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
