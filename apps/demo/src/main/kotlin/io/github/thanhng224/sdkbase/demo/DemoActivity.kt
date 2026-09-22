package io.github.thanhng224.sdkbase.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thanhng224.sdkbase.otp.ui.OtpScreen
import io.github.thanhng224.sdkbase.otp.ui.OtpTheme

/**
 * The SDK's first customer: it supplies its own [FakeOtpGateway], owns nothing of the engine's
 * internals, and only ever talks to the published [io.github.thanhng224.sdkbase.otpsdk.OtpSdk]
 * facade and the optional Compose UI artifact.
 */
internal class DemoActivity : ComponentActivity() {

    private val viewModel: DemoOtpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                when (val current = uiState) {
                    is DemoUiState.Starting -> Text("Starting…")
                    is DemoUiState.Failed -> Text("Could not start: ${current.reason}")
                    is DemoUiState.Ready -> {
                        val state by current.session.state.collectAsStateWithLifecycle()
                        OtpTheme {
                            OtpScreen(state = state, onCommand = current.session::dispatch)
                        }
                    }
                }
            }
        }
    }
}
