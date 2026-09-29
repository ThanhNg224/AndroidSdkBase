package {{SDK_NAMESPACE}}.profile.config

import {{SDK_NAMESPACE}}.core.environment.SdkEnvironment
import {{SDK_NAMESPACE}}.profile.gateway.ProfileGateway

public class ProfileSdkConfig private constructor(
    public val gateway: ProfileGateway,
    public val environment: SdkEnvironment,
) {
    public class Builder(private val gateway: ProfileGateway) {
        private var environment: SdkEnvironment = SdkEnvironment.Default

        public fun environment(value: SdkEnvironment): Builder = apply { environment = value }

        public fun build(): ProfileSdkConfig = ProfileSdkConfig(gateway, environment)
    }
}
