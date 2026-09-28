package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes every mutation of [S] through [withLock], including timer ticks, so a state machine
 * driven from more than one caller (a UI dispatch, a host-driven call, a timer) can never interleave
 * two transitions. [Mutation] has no public implementation: the only way to reach one is the
 * receiver [withLock] passes into its block, so `update` can never be called outside a held lock.
 *
 * A nested [withLock] call on the *same* [StateStore] — from inside another [withLock] block on
 * this instance, on the same coroutine — would deadlock on [Mutex.withLock]; instead it is detected
 * (via a [CoroutineContext] element keyed to this instance) and fails fast with
 * [IllegalStateException]. Nesting a *different* [StateStore]'s [withLock] inside this one is
 * unaffected — the marker is per-instance.
 */
@SdkInternalApi
public class StateStore<S>(initial: S, private val onChange: (S) -> Unit) {

    /** No [onChange] callback. */
    public constructor(initial: S) : this(initial, {})

    private val mutableState = MutableStateFlow(initial)
    private val mutex = Mutex()

    /** A [CoroutineContext.Key] unique to this instance, so nesting is detected per-store. */
    private val key = object : CoroutineContext.Key<Marker> {}

    private inner class Marker : AbstractCoroutineContextElement(key)

    private inner class MutationImpl : Mutation<S> {
        override val current: S
            get() = mutableState.value

        override fun update(transform: (S) -> S) {
            val newState = transform(mutableState.value)
            mutableState.value = newState
            onChange(newState)
        }
    }

    /** Every state this store has held, starting with `initial`. */
    public val state: StateFlow<S> = mutableState.asStateFlow()

    /** The current state. Equivalent to `state.value`. */
    public val current: S
        get() = mutableState.value

    /**
     * Runs [block] with exclusive access to this store's state. See the class doc for the
     * non-reentrancy guarantee.
     */
    public suspend fun <R> withLock(block: suspend Mutation<S>.() -> R): R {
        check(coroutineContext[key] == null) { "StateStore.withLock is not reentrant" }
        return mutex.withLock {
            withContext(Marker()) {
                MutationImpl().block()
            }
        }
    }

    /** What a [withLock] block mutates through. Only ever obtained from inside [withLock]. */
    @SdkInternalApi
    public interface Mutation<S> {

        /** The state at the moment this is read. */
        public val current: S

        /** Sets the state to `transform(current)`, then notifies this store's `onChange`. */
        public fun update(transform: (S) -> S)
    }
}
