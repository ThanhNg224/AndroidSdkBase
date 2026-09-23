package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.error.SdkError

/** Immutable snapshot of the OTP flow. The UI renders this and nothing else. */
public data class OtpState(
    public val phase: Phase,
    public val codeLength: Int,
    public val enteredCode: String,
    public val attemptsRemaining: Int,
    public val secondsUntilResend: Int,
    public val secondsUntilExpiry: Int,
    public val error: SdkError?,
) {
    public enum class Phase { Idle, Requesting, AwaitingCode, Verifying, Verified, Failed }

    public val canSubmit: Boolean
        get() = phase == Phase.AwaitingCode &&
            enteredCode.length == codeLength &&
            attemptsRemaining > 0

    public val canResend: Boolean
        get() = phase == Phase.AwaitingCode && secondsUntilResend <= 0

    public companion object {
        public fun initial(codeLength: Int = 6): OtpState = OtpState(
            phase = Phase.Idle,
            codeLength = codeLength,
            enteredCode = "",
            attemptsRemaining = 0,
            secondsUntilResend = 0,
            secondsUntilExpiry = 0,
            error = null,
        )
    }
}
