package io.github.thanhng224.sdkbase.otp.ui.internal.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.thanhng224.sdkbase.otp.session.OtpState

/** One box per digit of the code, rendering what has been entered so far. */
@Composable
internal fun CodeCells(state: OtpState, borderColor: Color, textColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(state.codeLength) { index ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = state.enteredCode.getOrNull(index)?.toString() ?: "", color = textColor)
            }
        }
    }
}
