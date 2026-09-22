package io.github.thanhng224.sdkbase.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatchers are injected, never referenced statically inside the SDK, so every coroutine the SDK runs
 * is controllable from a test. Calling `GlobalScope` is banned repository-wide.
 *
 * This interface lives in `:core` rather than `:platform` deliberately: it needs only
 * `CoroutineDispatcher` from `coroutines-core`, so a headless host can consume an engine without
 * inheriting the Android utilities module. The Android-backed implementation is
 * `io.github.thanhng224.sdkbase.platform.AndroidDispatchers`.
 */
public interface DispatcherProvider {

    public val main: CoroutineDispatcher

    public val default: CoroutineDispatcher

    public val io: CoroutineDispatcher
}
