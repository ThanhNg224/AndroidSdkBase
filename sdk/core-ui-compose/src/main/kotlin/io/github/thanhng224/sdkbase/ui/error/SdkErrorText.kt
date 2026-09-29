package io.github.thanhng224.sdkbase.ui.error

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.ui.R

// Only the codes in core's own catalog. A feature's codes (OTP 3xxx, logging 21xx...) are not known
// here: a feature UI module maps its own and falls back to this for the shared ones.
internal val coreErrorMessages: Map<Int, Int> = mapOf(
    SdkErrors.UNKNOWN to R.string.sdk_core_ui_compose_error_unknown,
    SdkErrors.INVALID_CONFIG to R.string.sdk_core_ui_compose_error_invalid_config,
    SdkErrors.NETWORK_UNAVAILABLE to R.string.sdk_core_ui_compose_error_network,
    SdkErrors.GATEWAY_FAILURE to R.string.sdk_core_ui_compose_error_gateway,
    SdkErrors.TIMEOUT to R.string.sdk_core_ui_compose_error_timeout,
)

/**
 * The string to show for [error], chosen by [SdkError.code] only: `reason` is diagnostic text and
 * never reaches the screen. A code core's catalog does not own gets the generic "something went
 * wrong" text. Errors whose [SdkError.disposition] is `SILENT` should not be shown at all.
 * A host overrides any of these by declaring a string with the same name in its own resources.
 */
@StringRes
public fun sdkErrorMessageRes(error: SdkError): Int =
    coreErrorMessages[error.code] ?: R.string.sdk_core_ui_compose_error_unknown

@Composable
public fun sdkErrorMessage(error: SdkError): String = stringResource(sdkErrorMessageRes(error))
