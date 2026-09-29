package io.github.thanhng224.sdkbase.otp.ui.internal.text

import androidx.annotation.StringRes
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.ui.R
import io.github.thanhng224.sdkbase.ui.error.sdkErrorMessageRes

/**
 * Maps on [SdkError.code] only: `reason` is diagnostic text and never reaches the screen. OTP's own
 * codes get OTP text; every other code (network, timeout, unknown...) gets the shared SDK text.
 */
@StringRes
internal fun errorMessageRes(error: SdkError): Int = when (error.code) {
    OtpErrors.OTP_INVALID -> R.string.sdk_otp_ui_compose_error_invalid
    OtpErrors.OTP_EXPIRED -> R.string.sdk_otp_ui_compose_error_expired
    OtpErrors.OTP_ATTEMPTS_EXCEEDED -> R.string.sdk_otp_ui_compose_error_attempts_exceeded
    OtpErrors.OTP_RESEND_TOO_SOON -> R.string.sdk_otp_ui_compose_error_resend_too_soon
    else -> sdkErrorMessageRes(error)
}
