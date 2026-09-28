package io.github.thanhng224.sdkbase.core.call

/** A handle to stop an outbound async call or subscription. Cancelling twice is a no-op. */
public fun interface Cancellable {
    public fun cancel()
}
