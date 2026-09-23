package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkLogger
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.gateway.TelemetrySink

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

        public fun maxAttempts(value: Int): Builder = apply { maxAttempts = value }

        public fun logger(value: SdkLogger): Builder = apply { logger = value }

        public fun telemetry(value: TelemetrySink?): Builder = apply { telemetry = value }

        public fun build(): SdkResult<OtpSdkConfig> {
            if (destination.isBlank()) {
                return SdkResult.Failure(SdkErrors.invalidConfig("destination must not be blank"))
            }
            if (maxAttempts !in MIN_ATTEMPTS..MAX_ATTEMPTS) {
                return SdkResult.Failure(
                    SdkErrors.invalidConfig("maxAttempts must be in $MIN_ATTEMPTS..$MAX_ATTEMPTS")
                )
            }
            return SdkResult.Success(
                OtpSdkConfig(
                    destination = destination,
                    gateway = gateway,
                    maxAttempts = maxAttempts,
                    logger = logger,
                    telemetry = telemetry,
                )
            )
        }

        private companion object {
            // `private` on each const val, not just on the companion: a companion object's own
            // visibility does not make its JVM-level fields private — Kotlin still emits a
            // `public static final` field on the outer class for a `const val` unless the
            // property itself is marked private (verified: without this, DEFAULT_MAX_ATTEMPTS,
            // MIN_ATTEMPTS and MAX_ATTEMPTS showed up as public fields of OtpSdkConfig$Builder in
            // the committed ABI baseline). See docs/COMPATIBILITY.md's internal-package caveat —
            // this is the same class of leak, one level deeper.
            private const val DEFAULT_MAX_ATTEMPTS = 3
            private const val MIN_ATTEMPTS = 1
            private const val MAX_ATTEMPTS = 10
        }
    }
}
