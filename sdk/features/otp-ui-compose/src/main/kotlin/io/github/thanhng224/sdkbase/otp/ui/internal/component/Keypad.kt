package io.github.thanhng224.sdkbase.otp.ui.internal.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thanhng224.sdkbase.otp.session.OtpCommand

/** The digit pad the host UI drives [OtpCommand]s from. */
@Composable
internal fun Keypad(onCommand: (OtpCommand) -> Unit, enabled: Boolean) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    // 56.dp exceeds the 48.dp Material minimum touch target.
                    TextButton(
                        onClick = { onCommand(OtpCommand.AppendDigit(digit)) },
                        enabled = enabled,
                        modifier = Modifier.size(56.dp),
                    ) { Text(digit.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onCommand(OtpCommand.AppendDigit('0')) },
                enabled = enabled,
                modifier = Modifier.size(56.dp),
            ) { Text("0") }
            TextButton(
                onClick = { onCommand(OtpCommand.DeleteDigit) },
                enabled = enabled,
                modifier = Modifier.size(56.dp),
            ) { Text("⌫") }
        }
    }
}
