package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs [block] on [DispatcherProvider.default] and delivers its [SdkResult] to [callback] on
 * [DispatcherProvider.main] — the one primitive every Java-callable outbound entry point
 * (`OtpSdk.start`, `OtpSession.submit`, ...) is built from, so they all share identical threading,
 * cancellation and containment semantics instead of each hand-rolling them.
 *
 * A single [AtomicBoolean] decides, exactly once, whether the result is delivered or released:
 * whichever of "the returned [Cancellable] (or [parent]) is cancelled" and "[block] finished and is
 * ready to deliver" claims it first wins. The loser either never gets a value at all (cancel won
 * first, so [block] itself is cancelled before it can produce one) or hands a successful value to
 * [onUndelivered] instead of the callback ([block] won the claim, but cancellation had already been
 * requested) — so a caller that closes a live resource while an outbound call happens to be
 * finishing can never see the callback fire afterwards, and never leaks whatever the call produced.
 *
 * A non-cancellation exception from [block] is reported as [SdkErrors.unknown]; an enclosing
 * cancellation ([parent], or the returned [Cancellable]) is never reported as a failure. A throw out
 * of [callback] or [onUndelivered] — both host-supplied — is caught ([Exception], never [Error]) and
 * dropped, the same containment rule [io.github.thanhng224.sdkbase.core.logging.LogSink] follows.
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
    // Guards the single decision point below: whichever of `cancel()` and "block just finished"
    // flips this first owns the outcome. It is intentionally independent of the scope's own Job
    // state — that state only stops `block` cooperatively at its own suspension points, which does
    // not help once `block` has already returned a value on a non-suspending code path.
    val claimed = AtomicBoolean(false)

    val job = scope.launch {
        val result = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SdkResult.Failure(SdkErrors.unknown(cause = e))
        }

        if (claimed.compareAndSet(false, true)) {
            withContext(dispatchers.main) {
                try {
                    when (result) {
                        is SdkResult.Success -> callback.onSuccess(result.value)
                        is SdkResult.Failure -> callback.onFailure(result.error)
                    }
                } catch (e: Exception) {
                    // callback is host-supplied; contained the same way a LogSink is.
                }
            }
        } else if (result is SdkResult.Success) {
            try {
                onUndelivered?.invoke(result.value)
            } catch (e: Exception) {
                // onUndelivered is host-supplied too.
            }
        }
    }

    return Cancellable {
        if (claimed.compareAndSet(false, true)) {
            job.cancel()
        }
    }
}
