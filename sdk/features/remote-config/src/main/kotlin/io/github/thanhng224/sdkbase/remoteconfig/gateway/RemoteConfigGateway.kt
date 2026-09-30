package io.github.thanhng224.sdkbase.remoteconfig.gateway

import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig

/** Host-owned transport. Implementations must cooperate with coroutine cancellation. */
public fun interface RemoteConfigGateway {
    public suspend fun fetch(): SdkResult<RemoteConfig>
}
