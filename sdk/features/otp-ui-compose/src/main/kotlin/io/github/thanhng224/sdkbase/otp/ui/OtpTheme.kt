package io.github.thanhng224.sdkbase.otp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.thanhng224.sdkbase.otp.ui.internal.LocalOtpColors

/**
 * Every colour the SDK's UI can draw. There are NO hardcoded colour literals anywhere else in this
 * module: a host that themes its app must be able to theme this screen too.
 */
public data class OtpColors(
    public val background: Color,
    public val onBackground: Color,
    public val accent: Color,
    public val error: Color,
    public val cellBorder: Color,
) {
    public companion object {
        /** Derives the token set from the host's Material theme, which is the sane default. */
        @Composable
        @ReadOnlyComposable
        public fun fromMaterialTheme(): OtpColors = OtpColors(
            background = MaterialTheme.colorScheme.surface,
            onBackground = MaterialTheme.colorScheme.onSurface,
            accent = MaterialTheme.colorScheme.primary,
            error = MaterialTheme.colorScheme.error,
            cellBorder = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
public fun OtpTheme(
    colors: OtpColors = OtpColors.fromMaterialTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalOtpColors provides colors, content = content)
}
