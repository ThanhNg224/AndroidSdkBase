package io.github.thanhng224.sdkbase.demo.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.thanhng224.sdkbase.demo.R

/** Explicit palette roles, selected independently of the device uiMode. */
internal fun staticColorScheme(
    context: Context,
    darkTheme: Boolean,
): ColorScheme =
    if (darkTheme) {
        createDarkColorScheme(context)
    } else {
        createLightColorScheme(context)
    }

private fun createLightColorScheme(context: Context): ColorScheme =
    lightColorScheme(
        primary = context.color(R.color.demo_color_primary_light),
        onPrimary = context.color(R.color.demo_color_on_primary_light),
        primaryContainer = context.color(R.color.demo_color_primary_container_light),
        onPrimaryContainer = context.color(R.color.demo_color_on_primary_container_light),
        secondary = context.color(R.color.demo_color_secondary_light),
        onSecondary = context.color(R.color.demo_color_on_secondary_light),
        secondaryContainer = context.color(R.color.demo_color_secondary_container_light),
        onSecondaryContainer = context.color(R.color.demo_color_on_secondary_container_light),
        tertiary = context.color(R.color.demo_color_tertiary_light),
        onTertiary = context.color(R.color.demo_color_on_tertiary_light),
        tertiaryContainer = context.color(R.color.demo_color_tertiary_container_light),
        onTertiaryContainer = context.color(R.color.demo_color_on_tertiary_container_light),
        background = context.color(R.color.demo_color_background_light),
        onBackground = context.color(R.color.demo_color_on_background_light),
        surface = context.color(R.color.demo_color_surface_light),
        onSurface = context.color(R.color.demo_color_on_surface_light),
        surfaceVariant = context.color(R.color.demo_color_surface_variant_light),
        onSurfaceVariant = context.color(R.color.demo_color_on_surface_variant_light),
        surfaceContainerLowest = context.color(R.color.demo_color_surface_container_lowest_light),
        surfaceContainerLow = context.color(R.color.demo_color_surface_container_low_light),
        surfaceContainer = context.color(R.color.demo_color_surface_container_light),
        surfaceContainerHigh = context.color(R.color.demo_color_surface_container_high_light),
        surfaceContainerHighest = context.color(R.color.demo_color_surface_container_highest_light),
        outline = context.color(R.color.demo_color_outline_light),
        outlineVariant = context.color(R.color.demo_color_outline_variant_light),
        error = context.color(R.color.demo_color_error_light),
        onError = context.color(R.color.demo_color_on_error_light),
        errorContainer = context.color(R.color.demo_color_error_container_light),
        onErrorContainer = context.color(R.color.demo_color_on_error_container_light),
    )

private fun createDarkColorScheme(context: Context): ColorScheme =
    darkColorScheme(
        primary = context.color(R.color.demo_color_primary_dark),
        onPrimary = context.color(R.color.demo_color_on_primary_dark),
        primaryContainer = context.color(R.color.demo_color_primary_container_dark),
        onPrimaryContainer = context.color(R.color.demo_color_on_primary_container_dark),
        secondary = context.color(R.color.demo_color_secondary_dark),
        onSecondary = context.color(R.color.demo_color_on_secondary_dark),
        secondaryContainer = context.color(R.color.demo_color_secondary_container_dark),
        onSecondaryContainer = context.color(R.color.demo_color_on_secondary_container_dark),
        tertiary = context.color(R.color.demo_color_tertiary_dark),
        onTertiary = context.color(R.color.demo_color_on_tertiary_dark),
        tertiaryContainer = context.color(R.color.demo_color_tertiary_container_dark),
        onTertiaryContainer = context.color(R.color.demo_color_on_tertiary_container_dark),
        background = context.color(R.color.demo_color_background_dark),
        onBackground = context.color(R.color.demo_color_on_background_dark),
        surface = context.color(R.color.demo_color_surface_dark),
        onSurface = context.color(R.color.demo_color_on_surface_dark),
        surfaceVariant = context.color(R.color.demo_color_surface_variant_dark),
        onSurfaceVariant = context.color(R.color.demo_color_on_surface_variant_dark),
        surfaceContainerLowest = context.color(R.color.demo_color_surface_container_lowest_dark),
        surfaceContainerLow = context.color(R.color.demo_color_surface_container_low_dark),
        surfaceContainer = context.color(R.color.demo_color_surface_container_dark),
        surfaceContainerHigh = context.color(R.color.demo_color_surface_container_high_dark),
        surfaceContainerHighest = context.color(R.color.demo_color_surface_container_highest_dark),
        outline = context.color(R.color.demo_color_outline_dark),
        outlineVariant = context.color(R.color.demo_color_outline_variant_dark),
        error = context.color(R.color.demo_color_error_dark),
        onError = context.color(R.color.demo_color_on_error_dark),
        errorContainer = context.color(R.color.demo_color_error_container_dark),
        onErrorContainer = context.color(R.color.demo_color_on_error_container_dark),
    )

private fun Context.color(resourceId: Int): Color = Color(getColor(resourceId))
