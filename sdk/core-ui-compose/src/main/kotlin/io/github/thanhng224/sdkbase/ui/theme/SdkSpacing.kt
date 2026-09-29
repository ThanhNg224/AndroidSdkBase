package io.github.thanhng224.sdkbase.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The 8-point spacing scale: every step is a multiple of 8dp, except the half step. */
public object SdkSpacing {
    public val Half: Dp = 4.dp
    public val One: Dp = 8.dp
    public val Two: Dp = 16.dp
    public val Three: Dp = 24.dp
    public val Four: Dp = 32.dp
}

public object SdkDimens {
    /** The smallest touch target Android recommends; every tappable SDK control is at least this. */
    public val MinTouchTarget: Dp = 48.dp
}
