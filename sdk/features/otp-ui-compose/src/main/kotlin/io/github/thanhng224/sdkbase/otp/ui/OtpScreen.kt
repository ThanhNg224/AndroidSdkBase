package io.github.thanhng224.sdkbase.otp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpState
import io.github.thanhng224.sdkbase.otp.ui.internal.component.CodeCells
import io.github.thanhng224.sdkbase.otp.ui.internal.component.Keypad
import io.github.thanhng224.sdkbase.otp.ui.internal.text.errorMessageRes
import io.github.thanhng224.sdkbase.otp.ui.internal.theme.LocalOtpColors
import io.github.thanhng224.sdkbase.ui.theme.SdkDimens
import io.github.thanhng224.sdkbase.ui.theme.SdkSpacing

/**
 * Stateless by construction: it receives an [OtpState] and emits [OtpCommand]s. It holds no state
 * of its own, which is why it needs no unit test — every rule it renders was tested in the engine.
 */
@Composable
public fun OtpScreen(
    state: OtpState,
    onCommand: (OtpCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOtpColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = SdkSpacing.Three, vertical = SdkSpacing.Two),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.sdk_otp_ui_compose_enter_code, state.codeLength),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(SdkSpacing.Three))

        CodeCells(state = state, borderColor = colors.cellBorder, textColor = colors.onBackground)

        Spacer(Modifier.size(SdkSpacing.Two))

        val error = state.error
        when {
            state.phase == OtpState.Phase.Requesting || state.phase == OtpState.Phase.Verifying ->
                CircularProgressIndicator(Modifier.size(SdkSpacing.Three))

            // Verified must be checked before the expiry fallback, or the screen falls through to
            // a frozen "Expires in Ns" and never tells the user the flow succeeded.
            state.phase == OtpState.Phase.Verified ->
                Text(text = stringResource(R.string.sdk_otp_ui_compose_verified), color = colors.accent, textAlign = TextAlign.Center)

            error != null ->
                Text(
                    text = stringResource(errorMessageRes(error)),
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )

            state.secondsUntilExpiry > 0 ->
                Text(text = stringResource(R.string.sdk_otp_ui_compose_expires_in, state.secondsUntilExpiry))
        }

        // Primary actions live in the bottom half — thumb-zone ergonomics.
        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onCommand(OtpCommand.Submit) },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.sdk_otp_ui_compose_verify))
        }

        Spacer(Modifier.size(SdkSpacing.One))

        TextButton(
            onClick = { onCommand(OtpCommand.Resend) },
            enabled = state.canResend,
            modifier = Modifier.fillMaxWidth().height(SdkDimens.MinTouchTarget),
        ) {
            Text(
                if (state.canResend) stringResource(R.string.sdk_otp_ui_compose_resend)
                else stringResource(R.string.sdk_otp_ui_compose_resend_in, state.secondsUntilResend)
            )
        }

        Spacer(Modifier.size(SdkSpacing.One))

        Keypad(onCommand = onCommand, enabled = state.phase == OtpState.Phase.AwaitingCode)
    }
}
