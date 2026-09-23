package io.github.thanhng224.sdkbase.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatchers are injected, never referenced statically inside the SDK, so every coroutine the SDK runs
 * is controllable from a test. Calling `GlobalScope` is banned repository-wide.
 *
 * This interface lives in `:core` deliberately: it needs only `CoroutineDispatcher` from
 * `coroutines-core`, so a headless host can consume an engine without inheriting Android utilities
 * it does not need. The Android-backed implementation lives inside each feature's own `internal`
 * package, e.g. `io.github.thanhng224.sdkbase.otp.internal.AndroidDispatchers`.
 */
public interface DispatcherProvider {

    public val main: CoroutineDispatcher

    public val default: CoroutineDispatcher

    public val io: CoroutineDispatcher
}
