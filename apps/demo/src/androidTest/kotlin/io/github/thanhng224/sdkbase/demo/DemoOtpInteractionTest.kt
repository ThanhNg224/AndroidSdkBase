package io.github.thanhng224.sdkbase.demo

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thanhng224.sdkbase.demo.otp.presentation.DemoActivity
import io.github.thanhng224.sdkbase.demo.otp.presentation.DemoUiState
import io.github.thanhng224.sdkbase.otp.session.OtpState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DemoOtpInteractionTest {
    @get:Rule
    val rule = createAndroidComposeRule<DemoActivity>()

    @Test
    fun keypadRemainsInteractiveAfterRotatingIntoAShortWindow() {
        val originalOrientation = rule.activity.requestedOrientation
        try {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            rule.waitUntil(timeoutMillis = 5_000) {
                (rule.activity.viewModel.uiState.value as? DemoUiState.Ready)?.session?.state?.value?.phase ==
                    OtpState.Phase.AwaitingCode
            }
            rule.onNode(hasText("0") and hasClickAction()).performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                (rule.activity.viewModel.uiState.value as? DemoUiState.Ready)?.session?.state?.value?.enteredCode == "0"
            }
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = originalOrientation }
        }
    }
}
