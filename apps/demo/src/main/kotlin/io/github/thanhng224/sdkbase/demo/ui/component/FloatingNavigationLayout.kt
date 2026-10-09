package io.github.thanhng224.sdkbase.demo.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout

/** Measures the complete navigation slot before composing content, without a next-frame state write. */
@Composable
internal fun FloatingNavigationLayout(
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bars = subcompose(FloatingSlot.Navigation, navigation).map { it.measure(loose) }
        val barHeight = bars.maxOfOrNull { it.height } ?: 0
        val bottom = barHeight.toDp()
        val contentModifier =
            Modifier
                .padding(bottom = bottom)
                .consumeWindowInsets(PaddingValues(bottom = bottom))
        val screens = subcompose(FloatingSlot.Content) { content(contentModifier) }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            screens.forEach { it.placeRelative(0, 0) }
            bars.forEach { it.placeRelative((constraints.maxWidth - it.width) / 2, constraints.maxHeight - it.height) }
        }
    }
}

private enum class FloatingSlot { Navigation, Content }
