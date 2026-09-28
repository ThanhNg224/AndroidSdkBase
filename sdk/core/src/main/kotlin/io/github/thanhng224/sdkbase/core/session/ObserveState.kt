package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Delivers this [StateFlow]'s current value, then every later change, to [listener] on
 * [DispatcherProvider.main] — the Java-callable twin of collecting a `StateFlow` directly — until
 * the returned [Cancellable] is cancelled or [parent] is cancelled.
 *
 * A throwing [listener] is caught ([Exception], never [Error]) and dropped, the same containment
 * rule [io.github.thanhng224.sdkbase.core.call.launchCallback] follows for a
 * [io.github.thanhng224.sdkbase.core.call.ResultCallback]: one bad state does not stop later ones
 * from being delivered.
 */
@SdkInternalApi
public fun <S> StateFlow<S>.observe(
    dispatchers: DispatcherProvider,
    listener: StateListener<S>,
    parent: Job? = null,
): Cancellable {
    val scope = CoroutineScope(SupervisorJob(parent) + dispatchers.main)
    val flow = this
    val job = scope.launch {
        flow.collect { state ->
            try {
                listener.onState(state)
            } catch (e: Exception) {
                // listener is host-supplied; a throw here must not stop later states.
            }
        }
    }
    return Cancellable { job.cancel() }
}
