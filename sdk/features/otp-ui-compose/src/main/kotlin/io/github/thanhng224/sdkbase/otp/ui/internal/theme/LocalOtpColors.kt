package io.github.thanhng224.sdkbase.otp.ui.internal.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.thanhng224.sdkbase.otp.ui.OtpColors

/**
 * Implementation detail of [io.github.thanhng224.sdkbase.otp.ui.OtpTheme] /
 * [io.github.thanhng224.sdkbase.otp.ui.OtpScreen]. Kotlin `internal`, so abi-tools excludes it from
 * the published contract.
 */
internal val LocalOtpColors = staticCompositionLocalOf<OtpColors> {
    error("OtpScreen must be wrapped in OtpTheme")
}
