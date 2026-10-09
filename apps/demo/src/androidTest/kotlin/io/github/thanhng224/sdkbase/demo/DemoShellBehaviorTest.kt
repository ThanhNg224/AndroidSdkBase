package io.github.thanhng224.sdkbase.demo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thanhng224.sdkbase.demo.host.DemoShell
import io.github.thanhng224.sdkbase.demo.settings.DemoSettings
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoAppearance
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoTheme
import io.github.thanhng224.sdkbase.ui.locale.ProvideSdkLocale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoShellBehaviorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingAndReselectingTabsPreserveGalleryAndBackReturnsToFeatures() {
        var launches = 0
        rule.setContent {
            ProvideSdkLocale(Locale.ENGLISH) {
                DemoTheme(dynamicColor = false) {
                    DemoShell(DemoSettings(), onOpenOtp = { launches++ }, onThemeSelected = {}, onLanguageSelected = {})
                }
            }
        }
        rule.onNodeWithContentDescription("UI").performClick().assertIsSelected()
        rule.onNodeWithTag("gallery_action").performClick()
        rule.onNodeWithContentDescription("UI").performClick()
        rule.onNodeWithTag("gallery_count").assertTextEquals("Actions performed: 1")
        rule.onNodeWithContentDescription("Settings").performClick().assertIsSelected()
        rule.onNodeWithContentDescription("UI").performClick()
        rule.onNodeWithTag("gallery_count").assertTextEquals("Actions performed: 1")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithContentDescription("Features").assertIsSelected()
        rule.onNodeWithText("Open OTP demo").performClick()
        assertEquals(1, launches)
    }

    @Test
    fun tabAndGalleryValuesRestoreAfterSavedStateRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            ProvideSdkLocale(Locale.ENGLISH) {
                DemoTheme(dynamicColor = false) {
                    DemoShell(DemoSettings(), onOpenOtp = {}, onThemeSelected = {}, onLanguageSelected = {})
                }
            }
        }
        rule.onNodeWithContentDescription("UI").performClick()
        rule.onNodeWithTag("gallery_action").performClick()
        rule.onNodeWithTag("gallery_switch").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithContentDescription("UI").assertIsSelected()
        rule.onNodeWithTag("gallery_switch").performScrollTo().assertIsOff()
        rule.onNodeWithTag("gallery_count").performScrollTo().assertTextEquals("Actions performed: 1")
    }

    @Test
    fun galleryStateSurvivesMovingBetweenFloatingNavigationAndRail() {
        var expanded by mutableStateOf(false)
        rule.setContent {
            val window = object : WindowInfo {
                override val isWindowFocused = true
                override val containerDpSize = if (expanded) DpSize(800.dp, 1000.dp) else DpSize(400.dp, 800.dp)
            }
            ProvideSdkLocale(Locale.ENGLISH) {
                DemoTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalWindowInfo provides window) {
                        DemoShell(DemoSettings(), onOpenOtp = {}, onThemeSelected = {}, onLanguageSelected = {})
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("UI").performClick()
        rule.onNodeWithTag("gallery_action").performClick()
        rule.runOnIdle { expanded = true }
        rule.onNode(hasText("UI") and hasClickAction()).assertIsSelected()
        rule.onNodeWithTag("gallery_count").assertTextEquals("Actions performed: 1")
        rule.runOnIdle { expanded = false }
        rule.onNodeWithContentDescription("UI").assertIsSelected()
        rule.onNodeWithTag("gallery_count").assertTextEquals("Actions performed: 1")
    }

    @Test
    fun settingsChoicesChangeLocaleWithoutResettingSelectedTab() {
        var settings by mutableStateOf(DemoSettings())
        rule.setContent {
            ProvideSdkLocale(Locale.ENGLISH) {
                DemoAppearance(settings, rule.activity.window) {
                    DemoShell(
                        settings,
                        onOpenOtp = {},
                        onThemeSelected = { settings = settings.copy(theme = it) },
                        onLanguageSelected = { settings = settings.copy(language = it) },
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithTag("theme_Dark").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("language_Vietnamese").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithContentDescription("Cài đặt").assertIsSelected()
        rule.onNodeWithTag("theme_Dark").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("language_System").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithContentDescription("Settings").assertIsSelected()
        rule.onNodeWithTag("language_English").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithContentDescription("Settings").assertIsSelected()
    }
}
