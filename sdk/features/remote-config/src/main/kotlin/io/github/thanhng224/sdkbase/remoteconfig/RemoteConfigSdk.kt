package io.github.thanhng224.sdkbase.remoteconfig

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.remoteconfig.config.RemoteConfigSdkConfig
import io.github.thanhng224.sdkbase.remoteconfig.internal.fetchRemoteConfig
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig

/** One-shot example: fetch a host-provided snapshot without opening a session. */
public object RemoteConfigSdk {
    @JvmStatic
    public suspend fun fetch(config: RemoteConfigSdkConfig): SdkResult<RemoteConfig> = fetchRemoteConfig(config)

    /** Delivers on the environment's main dispatcher; cancelling suppresses the callback. */
    @JvmStatic
    public fun fetch(config: RemoteConfigSdkConfig, callback: ResultCallback<RemoteConfig>): Cancellable =
        launchCallback(config.environment.dispatchers, callback) { fetch(config) }
}
