package io.github.thanhng224.sdkbase.demo.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.thanhng224.sdkbase.demo.R
import io.github.thanhng224.sdkbase.demo.settings.DemoLanguage
import io.github.thanhng224.sdkbase.demo.settings.DemoSettings
import io.github.thanhng224.sdkbase.demo.settings.DemoThemeMode
import io.github.thanhng224.sdkbase.demo.ui.component.DemoFloatingNavBar
import io.github.thanhng224.sdkbase.demo.ui.component.DemoNavItem
import io.github.thanhng224.sdkbase.demo.ui.component.FloatingNavigationLayout
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoDimens
import io.github.thanhng224.sdkbase.ui.theme.SdkSpacing

internal enum class DemoTab(val labelRes: Int, val selectedIcon: Int, val icon: Int) {
    Features(R.string.demo_features, R.drawable.ic_nav_demo_filled, R.drawable.ic_nav_demo_outlined),
    Components(R.string.demo_ui, R.drawable.ic_nav_design_filled, R.drawable.ic_nav_design_outlined),
    Settings(R.string.demo_settings, R.drawable.ic_nav_settings_filled, R.drawable.ic_nav_settings_outlined),
}

/** Plain shell: Activity launches the SDK flow; this composable owns only tab and screen UI state. */
@Composable
internal fun DemoShell(
    settings: DemoSettings,
    onOpenOtp: () -> Unit,
    onThemeSelected: (DemoThemeMode) -> Unit,
    onLanguageSelected: (DemoLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(DemoTab.Features) }
    val savedScreens = rememberSaveableStateHolder()
    BackHandler(enabled = tab != DemoTab.Features) { tab = DemoTab.Features }
    val items = DemoTab.entries.map { destination ->
        val label = stringResource(destination.labelRes)
        val selectedIcon = ImageVector.vectorResource(destination.selectedIcon)
        val icon = ImageVector.vectorResource(destination.icon)
        val selected = destination == tab
        remember(destination, label, selectedIcon, icon, selected) {
            DemoNavItem(
                id = destination.name,
                selected = selected,
                onClick = { tab = destination },
                selectedIcon = selectedIcon,
                unselectedIcon = icon,
                label = label,
            )
        }
    }
    val latestSettings by rememberUpdatedState(settings)
    val latestOpenOtp by rememberUpdatedState(onOpenOtp)
    val latestThemeSelected by rememberUpdatedState(onThemeSelected)
    val latestLanguageSelected by rememberUpdatedState(onLanguageSelected)
    // The same screen composition moves between the floating layout and rail row. Its saveable
    // registry must not change identity merely because the navigation's placement changes.
    val content = remember {
        movableContentOf<Modifier> { contentModifier ->
            savedScreens.SaveableStateProvider(tab.name) {
                when (tab) {
                    DemoTab.Features -> DemoFeaturesScreen(latestOpenOtp, contentModifier)

                    DemoTab.Components -> DemoGalleryScreen(contentModifier)

                    DemoTab.Settings -> DemoSettingsScreen(
                        latestSettings,
                        latestThemeSelected,
                        latestLanguageSelected,
                        contentModifier,
                    )
                }
            }
        }
    }
    val size = LocalWindowInfo.current.containerDpSize
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (size.width >= 600.dp && size.height >= 480.dp) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surface) {
                    items.forEach { item ->
                        NavigationRailItem(
                            selected = item.selected,
                            onClick = { if (!item.selected) item.onClick() },
                            icon = {
                                Icon(
                                    if (item.selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
                content(Modifier.weight(1f))
            }
        } else {
            FloatingNavigationLayout(
                modifier = Modifier.fillMaxSize(),
                navigation = {
                    DemoFloatingNavBar(
                        items,
                        Modifier.navigationBarsPadding().padding(horizontal = SdkSpacing.Two, vertical = SdkSpacing.One)
                            .widthIn(max = DemoDimens.maxNavBarWidth),
                    )
                },
                content = content,
            )
        }
    }
}
