package io.github.thanhng224.consumer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.getOrNull
import io.github.thanhng224.sdkbase.otp.OtpChallenge
import io.github.thanhng224.sdkbase.otp.OtpCommand
import io.github.thanhng224.sdkbase.otp.OtpGateway
import io.github.thanhng224.sdkbase.otp.OtpSdk
import io.github.thanhng224.sdkbase.otp.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.OtpState
import io.github.thanhng224.sdkbase.otp.ui.OtpScreen
import io.github.thanhng224.sdkbase.otp.ui.OtpTheme

/**
 * Proves the Kotlin surface resolves from the published AAR, including the optional UI artifact and
 * the metadata floor — if the AAR emitted newer Kotlin metadata than this build's Kotlin can read,
 * this file fails with "was compiled with a newer Kotlin version".
 */
public object KotlinConsumer {

    private val gateway = object : OtpGateway {
        override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
            SdkResult.Success(OtpChallenge("kt-ch", 6, 60, 30))

        override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> =
            SdkResult.Success(Unit)
    }

    public suspend fun run(): String {
        val config = OtpSdkConfig.Builder("0900000000", gateway).build().getOrNull()
            ?: return "config rejected"
        val session = OtpSdk.start(config).getOrNull() ?: return "start failed"
        session.dispatch(OtpCommand.AppendDigit('1'))
        val phase = session.state.value.phase
        session.close()
        return "ok: $phase"
    }
}

/** Proves the optional UI artifact (`OtpTheme`/`OtpScreen`) is consumable on its own terms. */
@Composable
public fun ConsumerScreen() {
    OtpTheme {
        val state by remember { mutableStateOf(OtpState.initial()) }
        OtpScreen(state = state, onCommand = {})
    }
}
