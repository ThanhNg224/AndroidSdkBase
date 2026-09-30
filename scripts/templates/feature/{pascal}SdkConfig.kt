package {{pkg}}.config

import {{ns}}.core.config.validateConfig
import {{ns}}.core.environment.SdkEnvironment
import {{ns}}.core.result.SdkResult
import {{pkg}}.gateway.{{pascal}}Gateway

/**
 * Host-supplied configuration. Validated once, here, at the public boundary — never deeper. A
 * builder rather than default arguments because a Java host cannot use Kotlin default arguments.
 */
public class {{pascal}}SdkConfig private constructor(
    public val gateway: {{pascal}}Gateway,
    public val environment: SdkEnvironment,
) {
    public class Builder(private val gateway: {{pascal}}Gateway) {
        private var environment: SdkEnvironment = SdkEnvironment.Default

        public fun environment(value: SdkEnvironment): Builder = apply { environment = value }

        public fun build(): SdkResult<{{pascal}}SdkConfig> = validateConfig {
            // TODO: add `ensure(...) { "..." }` checks as this feature grows its own config —
            // see `:sdk:features:otp`'s OtpSdkConfig.Builder.build for the pattern.
            {{pascal}}SdkConfig(gateway = gateway, environment = environment)
        }
    }
}
