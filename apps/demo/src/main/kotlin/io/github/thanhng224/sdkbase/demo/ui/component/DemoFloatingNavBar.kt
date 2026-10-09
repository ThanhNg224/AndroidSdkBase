package io.github.thanhng224.sdkbase.demo.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import io.github.thanhng224.sdkbase.demo.R
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoDimens
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating capsule navigation with caller-owned selection and placement. Each tab needs at least
 * 48dp of available row width. Remaining width is shared by expanding labels, which ellipsize
 * without hiding their full accessible names. Use stable, unique item IDs.
 */
@Composable
internal fun DemoFloatingNavBar(
    items: List<DemoNavItem>,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedIndicatorColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val capsuleShape = RoundedCornerShape(DemoDimens.radiusPill)
    Surface(
        modifier = modifier,
        shape = capsuleShape,
        color = containerColor,
        tonalElevation = DemoDimens.elevationMedium,
        shadowElevation = DemoDimens.elevationMedium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Layout(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(DemoDimens.spaceSmall),
            content = {
                items.forEach { item ->
                    key(item.id) {
                        DemoNavItemPill(item, capsuleShape, contentColor, selectedContentColor, selectedIndicatorColor)
                    }
                }
            },
        ) { measurables, constraints ->
            require(constraints.hasBoundedWidth) { "Navigation row requires a bounded width." }
            val width = constraints.maxWidth
            // Below 48dp per tab the targets shrink evenly instead of failing the layout pass.
            val minimum = min(DemoDimens.minTouchTarget.roundToPx(), width / max(1, measurables.size))
            val widths = measurables.map { it.layoutId as NavItemWidth }
            val budget = (width - minimum * measurables.size).toFloat()
            val badgeRequest = widths.sumOf { it.badgeExtra }
            val badgeFactor = if (badgeRequest > budget) budget / badgeRequest else 1f
            val reserved = widths.map { (it.badgeExtra * badgeFactor).toInt() }
            val labelBudget = budget - reserved.sum()
            val extras = widths.map { it.extra().coerceAtLeast(0f) }
            val requested = extras.sum()
            val factor = if (requested > labelBudget) labelBudget / requested else 1f
            // Floor rather than round: simultaneous outgoing/incoming labels cannot exceed budget.
            val placeables =
                measurables.mapIndexed { index, measurable ->
                    val itemWidth = minimum + reserved[index] + (extras[index] * factor).toInt()
                    measurable.measure(constraints.copy(minWidth = itemWidth, maxWidth = itemWidth, minHeight = 0))
                }
            val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
            val gap = (width - placeables.sumOf { it.width }).toFloat() / max(1, placeables.size)
            layout(width, height) {
                var x = gap / 2f
                placeables.forEach { placeable ->
                    placeable.placeRelative(x.roundToInt(), (height - placeable.height) / 2)
                    x += placeable.width + gap
                }
            }
        }
    }
}

private class NavItemWidth(
    val badgeExtra: Int,
    val extra: () -> Float,
)

@Composable
private fun DemoNavItemPill(
    item: DemoNavItem,
    capsuleShape: Shape,
    contentColor: Color,
    selectedContentColor: Color,
    selectedIndicatorColor: Color,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val transition = updateTransition(item.selected, label = "nav:${item.id}")
    val indicator =
        transition.animateColor(transitionSpec = {
            spring(stiffness = Spring.StiffnessMediumLow)
        }, label = "indicator") {
            if (it) selectedIndicatorColor else Color.Transparent
        }
    val tint =
        transition.animateColor(transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) }, label = "content") {
            if (it) selectedContentColor else contentColor
        }
    val expansion =
        transition.animateFloat(transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) }, label = "label") {
            if (it) 1f else 0f
        }
    val scale =
        transition.animateFloat(
            transitionSpec = {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            },
            label = "iconScale",
        ) { if (it) 1.12f else 1f }
    val metrics = navItemMetrics(item)
    val width =
        remember(metrics.baseExtra, metrics.labelSize, metrics.labelGap, expansion) {
            NavItemWidth(metrics.baseExtra) {
                (metrics.labelSize.width + metrics.labelGap) *
                    expansion.value.coerceIn(0f, 1f)
            }
        }
    val badgeDescription =
        if (item.badgeCount > 0) {
            pluralStringResource(R.plurals.demo_nav_badge_count, item.badgeCount, item.badgeCount)
        } else {
            null
        }
    Box(
        modifier =
            Modifier
                .layoutId(width)
                .defaultMinSize(minWidth = DemoDimens.minTouchTarget, minHeight = DemoDimens.minTouchTarget)
                .clip(capsuleShape)
                .drawBehind { drawRect(indicator.value) }
                .selectable(
                    selected = item.selected,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = selectedContentColor),
                    role = Role.Tab,
                    onClick = {
                        if (!item.selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            item.onClick()
                        }
                    },
                ).semantics {
                    // stateDescription would replace the platform's selected announcement.
                    val name = item.contentDescription ?: item.label
                    contentDescription = if (badgeDescription != null) "$name, $badgeDescription" else name
                }.padding(vertical = DemoDimens.spaceSmall),
        contentAlignment = Alignment.Center,
    ) {
        NavItemContent(item, metrics, tint, scale, expansion)
    }
}

private data class NavItemMetrics(
    val iconSize: IntSize,
    val labelSize: IntSize,
    val baseExtra: Int,
    val labelGap: Int,
    val ellipsisWidth: Int,
    val labelStyle: TextStyle,
    val badgeStyle: TextStyle,
    val badgeText: String,
)

@Composable
private fun navItemMetrics(item: DemoNavItem): NavItemMetrics {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val badgeStyle = MaterialTheme.typography.labelSmall
    val badgeText = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()
    // TextMeasurer owns its font-aware cache; do not retain sizes across font resolver changes.
    val labelSize = textMeasurer.measure(item.label, labelStyle, softWrap = false, maxLines = 1).size
    val badgeSize =
        if (item.badgeCount >
            0
        ) {
            textMeasurer.measure(badgeText, badgeStyle, softWrap = false, maxLines = 1).size
        } else {
            IntSize.Zero
        }
    return with(density) {
        val iconWidth = max(
            DemoDimens.iconSizeMedium.roundToPx(),
            if (item.badgeCount > 0) badgeSize.width + 8.dp.roundToPx() else 0,
        )
        val badgeHeight = if (item.badgeCount > 0) max(16.dp.roundToPx(), badgeSize.height) / 2 else 0
        val iconHeight = DemoDimens.iconSizeMedium.roundToPx() + badgeHeight
        val paddedIconWidth = iconWidth + (DemoDimens.spaceMediumSmall * 2).roundToPx()
        val baseExtra = paddedIconWidth - DemoDimens.minTouchTarget.roundToPx()
        NavItemMetrics(
            iconSize = IntSize(iconWidth, iconHeight),
            labelSize = labelSize,
            baseExtra = baseExtra.coerceAtLeast(0),
            labelGap = DemoDimens.spaceSmall.roundToPx(),
            ellipsisWidth = textMeasurer.measure("…", labelStyle, softWrap = false, maxLines = 1).size.width,
            labelStyle = labelStyle,
            badgeStyle = badgeStyle,
            badgeText = badgeText,
        )
    }
}

@Composable
private fun NavItemContent(
    item: DemoNavItem,
    metrics: NavItemMetrics,
    tint: State<Color>,
    scale: State<Float>,
    expansion: State<Float>,
) {
    Layout(
        modifier = Modifier.clearAndSetSemantics {},
        content = {
            NavItemIcon(item, metrics, tint, scale)
            NavItemLabel(item.label, metrics.labelStyle, tint, expansion)
        },
    ) { measurables, constraints ->
        val progress = expansion.value.coerceIn(0f, 1f)
        val hasLabel = progress > 0f
        val preferredPadding = DemoDimens.spaceMediumSmall.roundToPx()
        val minimumPadding = DemoDimens.spaceXSmall.roundToPx()
        val fixedWidth = metrics.iconSize.width + minimumPadding * 2 + metrics.ellipsisWidth
        val gap = metrics.labelGap.coerceAtMost((constraints.maxWidth - fixedWidth).coerceAtLeast(0))
        val targetPadding =
            if (hasLabel) {
                ((constraints.maxWidth - metrics.iconSize.width - gap - metrics.ellipsisWidth) / 2)
                    .coerceIn(minimumPadding, preferredPadding)
            } else {
                preferredPadding
            }
        val padding = preferredPadding + ((targetPadding - preferredPadding) * progress).roundToInt()
        val contentWidth = (constraints.maxWidth - padding * 2).coerceAtLeast(0)
        val icon = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = contentWidth, minHeight = 0))
        val remaining = if (hasLabel) (contentWidth - icon.width - gap).coerceAtLeast(0) else 0
        val label = measurables[1].measure(Constraints.fixedWidth(remaining).copy(maxHeight = constraints.maxHeight))
        val height = constraints.constrainHeight(max(icon.height, metrics.labelSize.height))
        layout(constraints.maxWidth, height) {
            val iconX = if (remaining == 0) (constraints.maxWidth - icon.width) / 2 else padding
            icon.placeRelative(iconX, (height - icon.height) / 2)
            if (remaining > 0) label.placeRelative(iconX + icon.width + gap, (height - label.height) / 2)
        }
    }
}

@Composable
private fun NavItemIcon(
    item: DemoNavItem,
    metrics: NavItemMetrics,
    tint: State<Color>,
    scale: State<Float>,
) {
    val density = LocalDensity.current
    Box(modifier = with(density) { Modifier.size(metrics.iconSize.width.toDp(), metrics.iconSize.height.toDp()) }) {
        Icon(
            imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null,
            tint = Color.Unspecified,
            modifier =
                Modifier.align(Alignment.BottomStart).size(DemoDimens.iconSizeMedium).graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    // Tint in the draw phase so the animation does not recompose per frame.
                    colorFilter = ColorFilter.tint(tint.value)
                },
        )
        if (item.badgeCount > 0) {
            Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                Text(metrics.badgeText, style = metrics.badgeStyle, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun NavItemLabel(
    label: String,
    style: TextStyle,
    tint: State<Color>,
    expansion: State<Float>,
) {
    BasicText(
        text = label,
        style = style.copy(textDirection = TextDirection.ContentOrLtr, textAlign = TextAlign.Left),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        color = { tint.value },
        modifier = Modifier.graphicsLayer { alpha = expansion.value.coerceIn(0f, 1f) },
    )
}
