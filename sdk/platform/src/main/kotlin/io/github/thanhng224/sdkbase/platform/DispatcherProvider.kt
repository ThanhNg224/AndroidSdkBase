package io.github.thanhng224.sdkbase.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers are injected, never referenced statically inside the SDK, so every coroutine the SDK
 * runs is controllable from a test. `GlobalScope` is banned repository-wide.
 */
public interface DispatcherProvider {

    public val main: CoroutineDispatcher

    public val default: CoroutineDispatcher

    public val io: CoroutineDispatcher

    public companion object {
        public val Default: DispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher get() = Dispatchers.Main
            override val default: CoroutineDispatcher get() = Dispatchers.Default
            override val io: CoroutineDispatcher get() = Dispatchers.IO
        }
    }
}
