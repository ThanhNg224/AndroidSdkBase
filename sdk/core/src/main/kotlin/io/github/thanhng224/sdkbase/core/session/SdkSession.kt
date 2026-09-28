package io.github.thanhng224.sdkbase.core.session

import io.github.thanhng224.sdkbase.core.call.Cancellable
import kotlinx.coroutines.flow.StateFlow

/**
 * The contract every SDK session exposes to a host: its current [state], a way to [observeState]
 * it without naming a `Flow`, and one [close] that tears the whole session down. Every feature's
 * own session interface (e.g. `OtpSession`) extends this instead of redeclaring these three
 * members itself — [io.github.thanhng224.sdkbase.core.session.SdkSessionBase] is the one place
 * that implements them.
 */
public interface SdkSession<S> {

    /** The session's current state, and every state it moves through afterwards. */
    public val state: StateFlow<S>

    /**
     * Delivers the current state, then every later change, to [listener] on the environment's main
     * dispatcher — the Java-callable twin of collecting [state] directly — until the returned
     * [Cancellable] is cancelled or the session is [close]d.
     */
    public fun observeState(listener: StateListener<S>): Cancellable

    /**
     * Tears the session down: cancels its pending work and, from then on, every operation fails
     * with [io.github.thanhng224.sdkbase.core.error.SdkErrors.sessionClosed]. Idempotent — calling
     * it more than once has no further effect.
     */
    public fun close()
}
