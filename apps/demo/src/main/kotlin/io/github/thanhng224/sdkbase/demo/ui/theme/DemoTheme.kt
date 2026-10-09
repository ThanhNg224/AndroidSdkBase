package io.github.thanhng224.sdkbase.demo.ui.theme

import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.thanhng224.sdkbase.demo.settings.DemoSettings
import io.github.thanhng224.sdkbase.demo.settings.DemoThemeMode
import io.github.thanhng224.sdkbase.ui.locale.ProvideSdkLocale
import io.github.thanhng224.sdkbase.ui.theme.SdkDimens
import io.github.thanhng224.sdkbase.ui.theme.SdkSpacing

/** Host-owned appearance, applied before any host or SDK strings are resolved. */
@Composable
internal fun DemoAppearance(settings: DemoSettings, window: Window, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        DemoThemeMode.System -> isSystemInDarkTheme()
        DemoThemeMode.Light -> false
        DemoThemeMode.Dark -> true
    }
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    // Always use the localized branch: toggling System/custom locale must keep the content's
    // composition identity, including the shell's saved tab and each screen's UI state.
    val locale = settings.locale ?: LocalConfiguration.current.locales[0]
    ProvideSdkLocale(locale) { DemoTheme(darkTheme = dark, content = content) }
}

@Composable
internal fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        staticColorScheme(context, darkTheme)
    }
    MaterialTheme(colorScheme = colors, typography = DemoTypography, shapes = DemoShapes, content = content)
}

/** Only the dimensions needed by this demo's shared components. */
internal object DemoDimens {
    val spaceXSmall = SdkSpacing.Half
    val spaceSmall = SdkSpacing.One
    val spaceMediumSmall = SdkSpacing.Two
    val minTouchTarget = SdkDimens.MinTouchTarget
    val iconSizeMedium = 24.dp
    val radiusPill = 100.dp
    val elevationMedium = 2.dp
    val maxNavBarWidth = 600.dp
    val maxContentWidth = 720.dp
}
