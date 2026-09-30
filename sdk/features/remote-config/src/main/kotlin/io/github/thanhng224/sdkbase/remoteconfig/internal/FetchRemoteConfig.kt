package io.github.thanhng224.sdkbase.remoteconfig.internal

import io.github.thanhng224.sdkbase.core.call.safeCall
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.remoteconfig.config.RemoteConfigSdkConfig
import io.github.thanhng224.sdkbase.remoteconfig.error.RemoteConfigErrors
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig
import kotlinx.coroutines.withContext

internal suspend fun fetchRemoteConfig(config: RemoteConfigSdkConfig): SdkResult<RemoteConfig> =
    withContext(config.environment.dispatchers.default) {
        when (val result = safeCall("remote config fetch", config.gatewayTimeoutMillis) { config.gateway.fetch() }) {
            is SdkResult.Failure -> result

            is SdkResult.Success -> if (result.value.revision.isBlank()) {
                SdkResult.Failure(RemoteConfigErrors.invalidResponse())
            } else {
                result
            }
        }
    }
