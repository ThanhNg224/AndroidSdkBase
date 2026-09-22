package io.github.thanhng224.sdkbase.platform

import io.github.thanhng224.sdkbase.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The real dispatchers, including `Dispatchers.Main` — which is why this lives in the Android module
 * and the [DispatcherProvider] contract does not.
 */
public object AndroidDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.IO
}
