package io.github.thanhng224.sdkbase.core.session

/** What a Java host implements to observe a `StateFlow` without naming `Flow`/`Continuation`. */
public fun interface StateListener<S> {
    public fun onState(state: S)
}
