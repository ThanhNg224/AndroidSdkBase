package io.github.thanhng224.sdkbase.demo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thanhng224.sdkbase.demo.ui.component.DemoFloatingNavBar
import io.github.thanhng224.sdkbase.demo.ui.component.DemoNavItem
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoTheme
import io.github.thanhng224.sdkbase.ui.locale.ProvideSdkLocale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoNavigationBehaviorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachTabHasOneAccessibleNameAndOnlyChangesTriggerCallbackAndHaptic() {
        val selected = mutableIntStateOf(0)
        var callbacks = 0
        var haptics = 0
        val haptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                haptics++
            }
        }
        rule.setContent {
            ProvideSdkLocale(Locale.ENGLISH) {
                CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                    DemoTheme(dynamicColor = false) {
                        val icon = ImageVector.vectorResource(R.drawable.ic_nav_demo_filled)
                        DemoFloatingNavBar(
                            (0..2).map { index ->
                                DemoNavItem(
                                    id = index.toString(),
                                    selected = selected.intValue == index,
                                    onClick = {
                                        selected.intValue = index
                                        callbacks++
                                    },
                                    selectedIcon = icon,
                                    unselectedIcon = icon,
                                    label = "Tab $index",
                                    badgeCount = if (index == 1) 100 else 0,
                                )
                            },
                            Modifier,
                        )
                    }
                }
            }
        }
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
        rule.onAllNodesWithContentDescription("Tab 1, 100 notifications", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithContentDescription("Tab 0").performClick()
        assertEquals(0, callbacks)
        assertEquals(0, haptics)
        rule.onNodeWithContentDescription("Tab 1, 100 notifications").performClick().assertIsSelected().performClick()
        assertEquals(1, callbacks)
        assertEquals(1, haptics)
    }
}
