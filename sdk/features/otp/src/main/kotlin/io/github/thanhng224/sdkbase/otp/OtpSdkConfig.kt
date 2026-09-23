package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink

/**
 * Host-supplied configuration. Validated once, here, at the public boundary — never deeper. A
 * builder rather than default arguments because a Java host cannot use Kotlin default arguments.
 *
 * Validation returns an [SdkResult] instead of throwing: a published SDK that throws out of its
 * entry point crashes someone else's app.
 */
public class OtpSdkConfig private constructor(
    public val destination: String,
    public val gateway: OtpGateway,
    public val maxAttempts: Int,
    public val logger: SdkLogger,
    public val telemetry: TelemetrySink?,
    public val gatewayTimeoutSeconds: Int,
) {
    public class Builder(
        private val destination: String,
        private val gateway: OtpGateway,
    ) {
        /**
         * A Java host with a callback-based async client never needs to see [OtpGateway] or a
         * `Continuation` at all: it implements [OtpCallbackGateway] and this constructor adapts it
         * via [asGateway]. Kotlin hosts should keep using the suspend-based constructor above.
         */
        public constructor(destination: String, gateway: OtpCallbackGateway) :
            this(destination, gateway.asGateway())

        private var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
        private var logger: SdkLogger = SdkLogger.NoOp
        private var telemetry: TelemetrySink? = null
        private var gatewayTimeoutSeconds: Int = DEFAULT_GATEWAY_TIMEOUT_SECONDS

        public fun maxAttempts(value: Int): Builder = apply { maxAttempts = value }

        public fun logger(value: SdkLogger): Builder = apply { logger = value }

        public fun telemetry(value: TelemetrySink?): Builder = apply { telemetry = value }

        public fun gatewayTimeoutSeconds(value: Int): Builder = apply { gatewayTimeoutSeconds = value }

        public fun build(): SdkResult<OtpSdkConfig> {
            if (destination.isBlank()) {
                return SdkResult.Failure(SdkErrors.invalidConfig("destination must not be blank"))
            }
            if (maxAttempts !in MIN_ATTEMPTS..MAX_ATTEMPTS) {
                return SdkResult.Failure(
                    SdkErrors.invalidConfig("maxAttempts must be in $MIN_ATTEMPTS..$MAX_ATTEMPTS")
                )
            }
            if (gatewayTimeoutSeconds !in 1..MAX_GATEWAY_TIMEOUT_SECONDS) {
                return SdkResult.Failure(
                    SdkErrors.invalidConfig("gatewayTimeoutSeconds must be in 1..$MAX_GATEWAY_TIMEOUT_SECONDS")
                )
            }
            return SdkResult.Success(
                OtpSdkConfig(
                    destination = destination,
                    gateway = gateway,
                    maxAttempts = maxAttempts,
                    logger = logger,
                    telemetry = telemetry,
                    gatewayTimeoutSeconds = gatewayTimeoutSeconds,
                )
            )
        }

        private companion object {
            // private per const: a const in a companion is otherwise a public static field.
            private const val DEFAULT_MAX_ATTEMPTS = 3
            private const val MIN_ATTEMPTS = 1
            private const val MAX_ATTEMPTS = 10
            private const val DEFAULT_GATEWAY_TIMEOUT_SECONDS = 30
            private const val MAX_GATEWAY_TIMEOUT_SECONDS = 300
        }
    }
}
