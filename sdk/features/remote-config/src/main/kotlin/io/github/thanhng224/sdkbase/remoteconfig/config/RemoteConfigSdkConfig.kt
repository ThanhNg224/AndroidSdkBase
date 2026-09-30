package io.github.thanhng224.sdkbase.remoteconfig.config

import io.github.thanhng224.sdkbase.core.config.validateConfig
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigCallbackGateway
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigGateway
import io.github.thanhng224.sdkbase.remoteconfig.gateway.asGateway

/** Host transport and environment, validated once by [Builder.build]. */
public class RemoteConfigSdkConfig private constructor(
    public val gateway: RemoteConfigGateway,
    public val environment: SdkEnvironment,
    public val gatewayTimeoutMillis: Long,
) {
    public class Builder(private val gateway: RemoteConfigGateway) {
        public constructor(gateway: RemoteConfigCallbackGateway) : this(gateway.asGateway())

        private var environment: SdkEnvironment = SdkEnvironment.Default
        private var gatewayTimeoutMillis: Long = 30_000L

        public fun environment(value: SdkEnvironment): Builder = apply { environment = value }

        public fun gatewayTimeoutMillis(value: Long): Builder = apply { gatewayTimeoutMillis = value }

        public fun build(): SdkResult<RemoteConfigSdkConfig> = validateConfig {
            ensure(gatewayTimeoutMillis > 0L) { "gatewayTimeoutMillis must be positive" }
            RemoteConfigSdkConfig(gateway, environment, gatewayTimeoutMillis)
        }
    }
}
