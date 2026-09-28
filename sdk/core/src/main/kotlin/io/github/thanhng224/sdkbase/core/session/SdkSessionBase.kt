package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.call.Cancellable
import kotlinx.coroutines.flow.StateFlow

/**
 * Implements [SdkSession]'s three members once, on top of a [SessionScope] and a [StateStore], so
 * a feature's runtime only adds its own operations. [close] is `final`: it calls
 * [SessionScope.close] and, only for the call that actually closes the scope (see
 * [SessionScope.close]'s atomicity guarantee), runs [onClose] — so [onClose] runs exactly once no
 * matter how many times [close] is called.
 */
@SdkInternalApi
public abstract class SdkSessionBase<S>(
    protected val scope: SessionScope,
    private val store: StateStore<S>,
) : SdkSession<S> {

    public final override val state: StateFlow<S>
        get() = store.state

    public final override fun observeState(listener: StateListener<S>): Cancellable =
        scope.observe(store.state, listener)

    public final override fun close() {
        if (scope.close()) {
            onClose()
        }
    }

    /** Runs exactly once, the first time [close] actually closes the session. No-op by default. */
    protected open fun onClose() {
    }
}
