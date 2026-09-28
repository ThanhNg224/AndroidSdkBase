package io.github.thanhng224.sdkbase.core.config

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult

/**
 * Runs [block] against a fresh [ConfigChecks] and turns the outcome into an [SdkResult] — the style
 * every feature's `Builder.build()` validates itself in, instead of each hand-rolling its own chain
 * of `if`/`return Failure(...)`.
 *
 * The first failing [ConfigChecks.ensure] stops [block] immediately (it throws internally) and
 * becomes `Failure(SdkErrors.invalidConfig(message))`; later checks never run. Any other
 * [Exception] out of [block] becomes `Failure(SdkErrors.unknown(cause))` instead of crashing the
 * host. An [Error] is never caught.
 */
@SdkInternalApi
public fun <T> validateConfig(block: ConfigChecks.() -> T): SdkResult<T> =
    try {
        SdkResult.Success(ConfigChecks().block())
    } catch (e: ConfigViolation) {
        SdkResult.Failure(SdkErrors.invalidConfig(e.violationMessage))
    } catch (e: Exception) {
        SdkResult.Failure(SdkErrors.unknown(cause = e))
    }

/** The receiver [validateConfig]'s block runs against. Only ever obtained from [validateConfig]. */
@SdkInternalApi
public class ConfigChecks internal constructor() {

    /**
     * Fails validation with [message] when [condition] is `false`, stopping every check after it.
     * [message] is only ever invoked when [condition] is `false`.
     */
    public fun ensure(condition: Boolean, message: () -> String) {
        if (!condition) throw ConfigViolation(message())
    }
}

/** Internal signal used to unwind [validateConfig]'s block on the first failed [ConfigChecks.ensure]. */
private class ConfigViolation(val violationMessage: String) : RuntimeException(violationMessage)
