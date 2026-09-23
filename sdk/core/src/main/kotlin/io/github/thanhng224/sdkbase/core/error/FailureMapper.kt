package io.github.thanhng224.sdkbase.core.error

import java.io.IOException

/**
 * Turns a throwable a host call raised into a stable [SdkError]. Features can supply their own to
 * add finer-grained mapping; [Default] is the one every feature falls back to.
 */
public fun interface FailureMapper {

    public fun map(operation: String, throwable: Throwable): SdkError

    public companion object {
        /** [IOException] -> [SdkErrors.networkUnavailable]; anything else -> [SdkErrors.gatewayFailure]. */
        public val Default: FailureMapper = FailureMapper { operation, throwable ->
            when (throwable) {
                is IOException -> SdkErrors.networkUnavailable(throwable)
                else -> SdkErrors.gatewayFailure(operation, throwable)
            }
        }
    }
}
