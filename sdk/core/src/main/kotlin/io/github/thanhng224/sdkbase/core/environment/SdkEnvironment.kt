package io.github.thanhng224.sdkbase.core.environment

import io.github.thanhng224.sdkbase.core.concurrency.AndroidDispatchers
import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink
import io.github.thanhng224.sdkbase.core.time.Clock
import io.github.thanhng224.sdkbase.core.time.IdGenerator

/**
 * One logger/telemetry/dispatchers/clock/id source, shared by every feature in a composition,
 * instead of each feature's config carrying its own copies (e.g. the `logger()`/`telemetry()`
 * builder methods `OtpSdkConfig` used to have). A builder rather than default arguments because a
 * Java host cannot use Kotlin default arguments; [Builder.build] has nothing to validate, so it
 * returns the instance directly instead of an `SdkResult`.
 */
public class SdkEnvironment private constructor(
    public val logger: SdkLogger,
    public val telemetry: TelemetrySink,
    public val dispatchers: DispatcherProvider,
    public val clock: Clock,
    public val idGenerator: IdGenerator,
) {

    public class Builder {
        private var logger: SdkLogger = SdkLogger.NoOp
        private var telemetry: TelemetrySink = TelemetrySink.None
        private var dispatchers: DispatcherProvider = AndroidDispatchers
        private var clock: Clock = Clock.System
        private var idGenerator: IdGenerator = IdGenerator.Uuid

        public fun logger(value: SdkLogger): Builder = apply { logger = value }

        public fun telemetry(value: TelemetrySink): Builder = apply { telemetry = value }

        public fun dispatchers(value: DispatcherProvider): Builder = apply { dispatchers = value }

        public fun clock(value: Clock): Builder = apply { clock = value }

        public fun idGenerator(value: IdGenerator): Builder = apply { idGenerator = value }

        public fun build(): SdkEnvironment =
            SdkEnvironment(logger, telemetry, dispatchers, clock, idGenerator)
    }

    public companion object {
        /**
         * Every field at its default: [SdkLogger.NoOp], [TelemetrySink.None], [AndroidDispatchers],
         * [Clock.System], [IdGenerator.Uuid].
         */
        public val Default: SdkEnvironment = Builder().build()
    }
}
