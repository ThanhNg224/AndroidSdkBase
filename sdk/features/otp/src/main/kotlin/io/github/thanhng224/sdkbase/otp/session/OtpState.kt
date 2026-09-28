package io.github.thanhng224.sdkbase.otp.session

import io.github.thanhng224.sdkbase.core.error.SdkError

/** Immutable snapshot of the OTP flow. The UI renders this and nothing else. */
public class OtpState(
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

    /** A plain class, not a `data class` (ABI: `copy`/`componentN` would freeze the property list
     * for every consumer). `internal`: only engine code inside this module needs to derive a new
     * state; a host only ever reads the properties above. */
    internal fun copy(
        phase: Phase = this.phase,
        codeLength: Int = this.codeLength,
        enteredCode: String = this.enteredCode,
        attemptsRemaining: Int = this.attemptsRemaining,
        secondsUntilResend: Int = this.secondsUntilResend,
        secondsUntilExpiry: Int = this.secondsUntilExpiry,
        error: SdkError? = this.error,
    ): OtpState = OtpState(
        phase = phase,
        codeLength = codeLength,
        enteredCode = enteredCode,
        attemptsRemaining = attemptsRemaining,
        secondsUntilResend = secondsUntilResend,
        secondsUntilExpiry = secondsUntilExpiry,
        error = error,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OtpState) return false
        return phase == other.phase &&
            codeLength == other.codeLength &&
            enteredCode == other.enteredCode &&
            attemptsRemaining == other.attemptsRemaining &&
            secondsUntilResend == other.secondsUntilResend &&
            secondsUntilExpiry == other.secondsUntilExpiry &&
            error == other.error
    }

    override fun hashCode(): Int {
        var result = phase.hashCode()
        result = 31 * result + codeLength
        result = 31 * result + enteredCode.hashCode()
        result = 31 * result + attemptsRemaining
        result = 31 * result + secondsUntilResend
        result = 31 * result + secondsUntilExpiry
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "OtpState(phase=$phase, codeLength=$codeLength, enteredCode=$enteredCode, " +
            "attemptsRemaining=$attemptsRemaining, secondsUntilResend=$secondsUntilResend, " +
            "secondsUntilExpiry=$secondsUntilExpiry, error=$error)"

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
