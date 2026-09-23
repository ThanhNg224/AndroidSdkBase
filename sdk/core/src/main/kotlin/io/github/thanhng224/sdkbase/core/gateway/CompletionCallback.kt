package io.github.thanhng224.sdkbase.core.gateway

import io.github.thanhng224.sdkbase.core.error.SdkError

/**
 * What a Java host calls when async work with no result finishes. Call exactly one method, exactly
 * once, from any thread; later calls are ignored.
 */
public interface CompletionCallback {
    public fun onSuccess()

    public fun onFailure(error: SdkError)
}
