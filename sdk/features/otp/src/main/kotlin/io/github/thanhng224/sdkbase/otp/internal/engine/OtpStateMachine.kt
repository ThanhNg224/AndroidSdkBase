package io.github.thanhng224.sdkbase.otp.internal.engine

import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpState

/**
 * Pure, synchronous, side-effect-free. Every branch of the OTP flow is decided here so it can be
 * tested without a coroutine, a clock, or a gateway. The engine owns the effects; this owns the rules.
 */
internal object OtpStateMachine {

    fun reduce(state: OtpState, command: OtpCommand): OtpState = when (command) {
        is OtpCommand.AppendDigit -> when {
            state.phase != OtpState.Phase.AwaitingCode -> state
            !command.digit.isDigit() -> state
            state.enteredCode.length >= state.codeLength -> state
            else -> state.copy(enteredCode = state.enteredCode + command.digit, error = null)
        }

        OtpCommand.DeleteDigit -> when {
            state.phase != OtpState.Phase.AwaitingCode -> state
            state.enteredCode.isEmpty() -> state
            else -> state.copy(enteredCode = state.enteredCode.dropLast(1), error = null)
        }

        // Submit and Resend are requests for an effect; the engine performs them. The reducer only
        // moves the phase so the UI can disable input immediately.
        OtpCommand.Submit -> if (state.canSubmit) state.copy(phase = OtpState.Phase.Verifying) else state

        OtpCommand.Resend -> if (state.canResend) state.copy(phase = OtpState.Phase.Requesting) else state

        OtpCommand.Cancel -> if (state.phase == OtpState.Phase.Verified || state.phase == OtpState.Phase.Failed) {
            state
        } else {
            state.copy(phase = OtpState.Phase.Failed, error = SdkErrors.cancelledByUser())
        }
    }

    fun onChallengeIssued(
        state: OtpState,
        codeLength: Int,
        expiresInSeconds: Int,
        resendAfterSeconds: Int,
        maxAttempts: Int,
    ): OtpState = state.copy(
        phase = OtpState.Phase.AwaitingCode,
        codeLength = codeLength,
        enteredCode = "",
        attemptsRemaining = maxAttempts,
        secondsUntilResend = resendAfterSeconds,
        secondsUntilExpiry = expiresInSeconds,
        error = null,
    )

    fun onVerified(state: OtpState): OtpState {
        if (state.phase != OtpState.Phase.Verifying) return state
        return state.copy(phase = OtpState.Phase.Verified, error = null)
    }

    fun onVerificationFailed(state: OtpState, error: SdkError): OtpState {
        if (state.phase != OtpState.Phase.Verifying) return state
        val remaining = (state.attemptsRemaining - 1).coerceAtLeast(0)
        return if (remaining == 0) {
            state.copy(
                phase = OtpState.Phase.Failed,
                enteredCode = "",
                attemptsRemaining = 0,
                error = OtpErrors.otpAttemptsExceeded(),
            )
        } else {
            state.copy(
                phase = OtpState.Phase.AwaitingCode,
                enteredCode = "",
                attemptsRemaining = remaining,
                error = error,
            )
        }
    }

    fun onFatal(state: OtpState, error: SdkError): OtpState =
        state.copy(phase = OtpState.Phase.Failed, error = error)

    /** Clears any partial entry so a fresh code can be submitted without mixing with a stale one. */
    fun clearCode(state: OtpState): OtpState =
        if (state.phase == OtpState.Phase.AwaitingCode) state.copy(enteredCode = "") else state

    fun onTick(state: OtpState): OtpState {
        if (state.phase != OtpState.Phase.AwaitingCode) return state
        val expiry = (state.secondsUntilExpiry - 1).coerceAtLeast(0)
        val resend = (state.secondsUntilResend - 1).coerceAtLeast(0)
        return if (expiry == 0) {
            state.copy(
                phase = OtpState.Phase.Failed,
                secondsUntilExpiry = 0,
                secondsUntilResend = resend,
                error = OtpErrors.otpExpired(),
            )
        } else {
            state.copy(secondsUntilExpiry = expiry, secondsUntilResend = resend)
        }
    }
}
