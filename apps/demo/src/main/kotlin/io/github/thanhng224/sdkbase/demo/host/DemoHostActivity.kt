package io.github.thanhng224.sdkbase.demo.host

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thanhng224.sdkbase.demo.otp.presentation.DemoActivity
import io.github.thanhng224.sdkbase.demo.settings.DemoSettingsViewModel
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoAppearance

internal class DemoHostActivity : ComponentActivity() {
    private val settings: DemoSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by settings.state.collectAsStateWithLifecycle()
            DemoAppearance(appearance, window) {
                DemoShell(
                    settings = appearance,
                    onOpenOtp = { startActivity(Intent(this, DemoActivity::class.java)) },
                    onThemeSelected = settings::setTheme,
                    onLanguageSelected = settings::setLanguage,
                )
            }
        }
    }
}
