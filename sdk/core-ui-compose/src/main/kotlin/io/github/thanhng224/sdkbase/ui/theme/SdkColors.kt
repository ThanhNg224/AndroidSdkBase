package io.github.thanhng224.sdkbase.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens SDK UI modules draw with. A UI module never uses a colour literal (the
 * `checkSourceRules` gate fails on one): it takes every colour from a token set like this one, so a
 * host that themes its app themes the SDK screens too.
 *
 * A plain class, not a `data class` (ABI: `copy`/`componentN` would freeze the property list for
 * every consumer); [Immutable] tells the Compose compiler it is safe to skip recomposition on.
 */
@Immutable
public class SdkColors(
    public val background: Color,
    public val onBackground: Color,
    public val accent: Color,
    public val error: Color,
    public val outline: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SdkColors) return false
        return background == other.background &&
            onBackground == other.onBackground &&
            accent == other.accent &&
            error == other.error &&
            outline == other.outline
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + onBackground.hashCode()
        result = 31 * result + accent.hashCode()
        result = 31 * result + error.hashCode()
        result = 31 * result + outline.hashCode()
        return result
    }

    override fun toString(): String =
        "SdkColors(background=$background, onBackground=$onBackground, accent=$accent, " +
            "error=$error, outline=$outline)"

    public companion object {
        /** Derives the tokens from the host's Material theme, which is the sane default. */
        @Composable
        @ReadOnlyComposable
        public fun fromMaterialTheme(): SdkColors = SdkColors(
            background = MaterialTheme.colorScheme.surface,
            onBackground = MaterialTheme.colorScheme.onSurface,
            accent = MaterialTheme.colorScheme.primary,
            error = MaterialTheme.colorScheme.error,
            outline = MaterialTheme.colorScheme.outline,
        )
    }
}
