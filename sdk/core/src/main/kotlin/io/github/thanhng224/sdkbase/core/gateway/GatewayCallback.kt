package io.github.thanhng224.sdkbase.core.gateway

import io.github.thanhng224.sdkbase.core.SdkError

/**
 * What a Java host calls when its async work finishes.
 *
 * Exactly one of [onSuccess] or [onFailure] must be called, exactly once, from any thread. A host
 * that calls back more than once is misbehaving, but the SDK survives it rather than crashing —
 * every call after the first is ignored.
 */
public interface GatewayCallback<T> {

    /** Delivers the successful result of the host's asynchronous work. */
    public fun onSuccess(value: T)

    /** Delivers the failure of the host's asynchronous work. */
    public fun onFailure(error: SdkError)
}
