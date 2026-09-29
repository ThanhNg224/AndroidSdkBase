package io.github.thanhng224.sdkbase.otp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.thanhng224.sdkbase.otp.ui.internal.theme.LocalOtpColors
import io.github.thanhng224.sdkbase.ui.theme.SdkColors

/**
 * Every colour the SDK's UI can draw. There are NO hardcoded colour literals anywhere else in this
 * module: a host that themes its app must be able to theme this screen too.
 *
 * A plain class, not a `data class` (ABI: `copy`/`componentN` would freeze the property list for
 * every consumer); [Immutable] tells the Compose compiler this is safe to skip recomposition on,
 * the same as the `data class` it replaces.
 */
@Immutable
public class OtpColors(
    public val background: Color,
    public val onBackground: Color,
    public val accent: Color,
    public val error: Color,
    public val cellBorder: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OtpColors) return false
        return background == other.background &&
            onBackground == other.onBackground &&
            accent == other.accent &&
            error == other.error &&
            cellBorder == other.cellBorder
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + onBackground.hashCode()
        result = 31 * result + accent.hashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + cellBorder.hashCode()
        return result
    }

    override fun toString(): String =
        "OtpColors(background=$background, onBackground=$onBackground, accent=$accent, " +
            "error=$error, cellBorder=$cellBorder)"

    public companion object {
        /** Derives the token set from the host's Material theme, which is the sane default. */
        @Composable
        @ReadOnlyComposable
        public fun fromMaterialTheme(): OtpColors {
            val tokens = SdkColors.fromMaterialTheme()
            return OtpColors(
                background = tokens.background,
                onBackground = tokens.onBackground,
                accent = tokens.accent,
                error = tokens.error,
                cellBorder = tokens.outline,
            )
        }
    }
}

@Composable
public fun OtpTheme(
    colors: OtpColors = OtpColors.fromMaterialTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalOtpColors provides colors, content = content)
}
