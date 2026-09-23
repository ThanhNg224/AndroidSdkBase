package io.github.thanhng224.sdkbase.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpSdk
import io.github.thanhng224.sdkbase.otp.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.OtpSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the demo screen renders. The session itself is never held by the Activity/Compose tree. */
internal sealed interface DemoUiState {
    data object Starting : DemoUiState
    data class Ready(val session: OtpSession) : DemoUiState
    data class Failed(val reason: String) : DemoUiState
}

/**
 * Owns the [OtpSession] for as long as the demo screen is alive. A `ViewModel` survives
 * configuration change, so the session is started exactly once per screen instance and closed
 * exactly once, in [onCleared] — never re-created on rotation and never leaked by an Activity
 * field that outlives its Compose content.
 */
internal class DemoOtpViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<DemoUiState>(DemoUiState.Starting)
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    private var session: OtpSession? = null

    init {
        viewModelScope.launch {
            val configResult = OtpSdkConfig.Builder(DEMO_DESTINATION, FakeOtpGateway())
                .logger(LogcatSdkLogger(debugEnabled = true))
                .maxAttempts(3)
                .build()

            when (configResult) {
                is SdkResult.Failure -> _uiState.value = DemoUiState.Failed(configResult.error.reason)
                is SdkResult.Success -> when (val start = OtpSdk.start(configResult.value)) {
                    is SdkResult.Failure -> _uiState.value = DemoUiState.Failed(start.error.reason)
                    is SdkResult.Success -> {
                        session = start.value
                        _uiState.value = DemoUiState.Ready(start.value)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        session?.close()
        session = null
        super.onCleared()
    }

    private companion object {
        private const val DEMO_DESTINATION = "0900000000"
    }
}
