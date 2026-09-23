package io.github.thanhng224.sdkbase.otp.ui.internal

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.thanhng224.sdkbase.otp.ui.OtpColors

/**
 * Implementation detail of [io.github.thanhng224.sdkbase.otp.ui.OtpTheme] /
 * [io.github.thanhng224.sdkbase.otp.ui.OtpScreen]. Kotlin `internal` is not a JVM-visibility
 * concept for a top-level property — the compiler still emits a plain public getter — so this
 * lives in an `internal` PACKAGE, which the ABI baseline tooling excludes by convention. See
 * docs/COMPATIBILITY.md.
 */
internal val LocalOtpColors = staticCompositionLocalOf<OtpColors> {
    error("OtpScreen must be wrapped in OtpTheme")
}
