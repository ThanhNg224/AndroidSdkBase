package io.github.thanhng224.sdkbase.demo.ui.component

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data item representing a destination within [DemoFloatingNavBar].
 */
internal data class DemoNavItem(
    internal val id: String,
    internal val selected: Boolean,
    internal val onClick: () -> Unit,
    internal val selectedIcon: ImageVector,
    internal val unselectedIcon: ImageVector,
    internal val label: String,
    internal val badgeCount: Int = 0,
    internal val contentDescription: String? = null,
)
