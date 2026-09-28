package io.github.thanhng224.sdkbase.core.call

import io.github.thanhng224.sdkbase.core.error.SdkError

/**
 * What a Java host implements to receive the outcome of a suspend-based SDK entry point (e.g.
 * `OtpSdk.start(config, callback)`), without ever naming a `Continuation`. Exactly one of
 * [onSuccess] or [onFailure] is delivered, exactly once, on [io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider.main] —
 * see [launchCallback].
 */
public interface ResultCallback<T> {

    public fun onSuccess(value: T)

    public fun onFailure(error: SdkError)
}
