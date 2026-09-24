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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpState
import io.github.thanhng224.sdkbase.otp.ui.internal.component.CodeCells
import io.github.thanhng224.sdkbase.otp.ui.internal.component.Keypad
import io.github.thanhng224.sdkbase.otp.ui.internal.theme.LocalOtpColors

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
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = "Enter the ${state.codeLength}-digit code",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.size(24.dp))

        CodeCells(state = state, borderColor = colors.cellBorder, textColor = colors.onBackground)

        Spacer(Modifier.size(16.dp))

        val error = state.error
        when {
            state.phase == OtpState.Phase.Requesting || state.phase == OtpState.Phase.Verifying ->
                CircularProgressIndicator(Modifier.size(24.dp))

            // Verified must be checked before the expiry fallback, or the screen falls through to
            // a frozen "Expires in Ns" and never tells the user the flow succeeded.
            state.phase == OtpState.Phase.Verified ->
                Text(text = "Verified", color = colors.accent, textAlign = TextAlign.Center)

            error != null ->
                Text(text = error.reason, color = colors.error, textAlign = TextAlign.Center)

            state.secondsUntilExpiry > 0 ->
                Text(text = "Expires in ${state.secondsUntilExpiry}s")
        }

        // Primary actions live in the bottom half — thumb-zone ergonomics.
        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onCommand(OtpCommand.Submit) },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Verify")
        }

        Spacer(Modifier.size(8.dp))

        TextButton(
            onClick = { onCommand(OtpCommand.Resend) },
            enabled = state.canResend,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                if (state.canResend) "Resend code"
                else "Resend in ${state.secondsUntilResend}s"
            )
        }

        Spacer(Modifier.size(8.dp))

        Keypad(onCommand = onCommand, enabled = state.phase == OtpState.Phase.AwaitingCode)
    }
}
