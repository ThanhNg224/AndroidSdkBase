package io.github.thanhng224.sdkbase.demo.otp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thanhng224.sdkbase.demo.R
import io.github.thanhng224.sdkbase.demo.settings.DemoSettingsViewModel
import io.github.thanhng224.sdkbase.demo.ui.component.DemoHeader
import io.github.thanhng224.sdkbase.demo.ui.component.DemoStatus
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoAppearance
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoDimens
import io.github.thanhng224.sdkbase.otp.ui.OtpScreen
import io.github.thanhng224.sdkbase.otp.ui.OtpTheme
import io.github.thanhng224.sdkbase.ui.error.sdkErrorMessage
import io.github.thanhng224.sdkbase.ui.theme.SdkSpacing

/** The host owns the Activity and session lifetime, while the SDK owns OTP state and rendering. */
internal class DemoActivity : ComponentActivity() {
    internal val viewModel: DemoOtpViewModel by viewModels()
    private val settings: DemoSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by settings.state.collectAsStateWithLifecycle()
            DemoAppearance(appearance, window) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        DemoHeader(
                            stringResource(R.string.demo_otp),
                            Modifier.padding(horizontal = SdkSpacing.Two, vertical = SdkSpacing.One),
                            onBack = { finish() },
                        )
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                            when (val current = uiState) {
                                DemoUiState.Starting -> DemoStatus(
                                    stringResource(R.string.demo_starting),
                                    stringResource(R.string.demo_starting_message),
                                    Modifier.padding(SdkSpacing.Three),
                                    loading = true,
                                )

                                is DemoUiState.Failed -> DemoStatus(
                                    stringResource(R.string.demo_start_failed),
                                    sdkErrorMessage(current.error),
                                    Modifier.padding(SdkSpacing.Three),
                                )

                                is DemoUiState.Ready -> {
                                    val state by current.session.state.collectAsStateWithLifecycle()
                                    val viewportHeight = maxHeight
                                    // The SDK screen remains unchanged; the host supplies a scrollable
                                    // viewport when its fixed keypad/actions exceed the available height.
                                    Box(
                                        Modifier.widthIn(max = DemoDimens.maxContentWidth).fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        OtpTheme {
                                            OtpScreen(
                                                state = state,
                                                onCommand = current.session::dispatch,
                                                modifier = Modifier.heightIn(min = viewportHeight),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
