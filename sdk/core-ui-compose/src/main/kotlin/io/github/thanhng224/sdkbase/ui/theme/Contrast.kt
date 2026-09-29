package io.github.thanhng224.sdkbase.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** WCAG AA minimum contrast for normal text. */
public const val MIN_TEXT_CONTRAST: Float = 4.5f

/** The WCAG contrast ratio of [foreground] on [background], from 1 (identical) to 21 (black on white). */
public fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Whether [foreground] is readable on [background] at [minimum] contrast (WCAG AA text by default). */
public fun meetsContrast(foreground: Color, background: Color, minimum: Float = MIN_TEXT_CONTRAST): Boolean =
    contrastRatio(foreground, background) >= minimum
