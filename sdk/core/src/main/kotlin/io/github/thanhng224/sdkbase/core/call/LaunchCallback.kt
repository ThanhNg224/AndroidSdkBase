package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [block] on [DispatcherProvider.default] and delivers its [SdkResult] to [callback] on
 * [DispatcherProvider.main] — the one primitive every Java-callable outbound entry point
 * (`OtpSdk.start`, `OtpSession.submit`, ...) is built from, so they all share identical threading,
 * cancellation and containment semantics instead of each hand-rolling them.
 *
 * The guarantee: a `cancel()` on the returned [Cancellable], or a cancellation of [parent], that
 * happens-before delivery on [DispatcherProvider.main] — in particular any call made on the main
 * thread before the callback starts running — means the callback never runs at all. Whenever
 * [block] still produces a successful value but that value does not get delivered because of such a
 * cancellation, the value is instead handed to [onUndelivered] exactly once, so it is never silently
 * leaked. This is decided by re-checking this coroutine's own [Job] *after* hopping onto
 * [DispatcherProvider.main] (inside a [NonCancellable] context, so the hop itself cannot be skipped
 * by that very cancellation) rather than right after [block] returns on the worker thread: deciding
 * on the worker thread would let a `cancel()`/[parent] cancellation issued on main, but arriving
 * after that early decision, race a callback that has already committed to firing.
 *
 * A non-cancellation exception from [block] is reported as [SdkErrors.unknown]; a cancellation
 * [block] itself does not survive (it never produced a value) is never reported as a failure. A
 * throw out of [callback] or [onUndelivered] — both host-supplied — is caught ([Exception], never
 * [Error]) and dropped, the same containment rule [io.github.thanhng224.sdkbase.core.logging.LogSink]
 * follows.
 */
@SdkInternalApi
public fun <T> launchCallback(
    dispatchers: DispatcherProvider,
    callback: ResultCallback<T>,
    parent: Job? = null,
    onUndelivered: ((T) -> Unit)? = null,
    block: suspend () -> SdkResult<T>,
): Cancellable {
    val scope = CoroutineScope(SupervisorJob(parent) + dispatchers.default)
    // `cancel()` below still races the launched coroutine, so both must agree on the same claim
    // exactly once: `cancel()` sets it eagerly so a still-suspended `block` gets interrupted, while
    // the coroutine only consults it for real after the main-dispatcher hop, once this coroutine's
    // own Job can no longer flip from active to cancelled underneath it.
    val claimed = AtomicBoolean(false)

    lateinit var job: Job
    job = scope.launch {
        val result = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SdkResult.Failure(SdkErrors.unknown(cause = e))
        }

        // NonCancellable: this hop must always run to completion so the deliver-vs-release decision
        // always gets made - a cancellation arriving exactly during the hop must not skip it and
        // leave `result` neither delivered nor released. `job.isActive` re-checks, now that we are
        // safely on `dispatchers.main`, whether a cancel()/parent-cancel already happened - closing
        // the race an immediate, worker-thread decision (the previous implementation) could not.
        withContext(dispatchers.main + NonCancellable) {
            if (job.isActive && claimed.compareAndSet(false, true)) {
                try {
                    when (result) {
                        is SdkResult.Success -> callback.onSuccess(result.value)
                        is SdkResult.Failure -> callback.onFailure(result.error)
                    }
                } catch (e: Exception) {
                    // callback is host-supplied; contained the same way a LogSink is.
                }
            } else if (result is SdkResult.Success) {
                try {
                    onUndelivered?.invoke(result.value)
                } catch (e: Exception) {
                    // onUndelivered is host-supplied too.
                }
            }
        }
    }

    return Cancellable {
        if (claimed.compareAndSet(false, true)) {
            job.cancel()
        }
    }
}
