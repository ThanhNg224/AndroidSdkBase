package io.github.thanhng224.sdkbase.remoteconfig.gateway

import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback
import io.github.thanhng224.sdkbase.core.gateway.awaitCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig

/** Java host transport; callbacks may arrive on any thread. */
public fun interface RemoteConfigCallbackGateway {
    public fun fetch(callback: GatewayCallback<RemoteConfig>)
}

/** Adapts Java transport through core's first-terminal-callback primitive. */
public fun RemoteConfigCallbackGateway.asGateway(): RemoteConfigGateway {
    val delegate = this
    return RemoteConfigGateway { awaitCallback { callback -> delegate.fetch(callback) } }
}
