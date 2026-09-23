package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.otp.internal.OtpStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpStateMachineTest {

    private val awaiting = OtpState.initial(codeLength = 6)
        .copy(phase = OtpState.Phase.AwaitingCode, attemptsRemaining = 3, secondsUntilResend = 30)

    @Test
    fun `appending digits fills the code up to its length`() {
        var state = awaiting
        "123456".forEach { state = OtpStateMachine.reduce(state, OtpCommand.AppendDigit(it)) }
        assertEquals("123456", state.enteredCode)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `appending past the code length is ignored`() {
        var state = awaiting
        "1234567890".forEach { state = OtpStateMachine.reduce(state, OtpCommand.AppendDigit(it)) }
        assertEquals("123456", state.enteredCode)
    }

    @Test
    fun `non digit input is ignored`() {
        val state = OtpStateMachine.reduce(awaiting, OtpCommand.AppendDigit('a'))
        assertEquals("", state.enteredCode)
    }

    @Test
    fun `delete removes the last digit and never underflows`() {
        var state = OtpStateMachine.reduce(awaiting, OtpCommand.AppendDigit('7'))
        state = OtpStateMachine.reduce(state, OtpCommand.DeleteDigit)
        assertEquals("", state.enteredCode)
        state = OtpStateMachine.reduce(state, OtpCommand.DeleteDigit)
        assertEquals("", state.enteredCode)
    }

    @Test
    fun `submit is blocked until the code is complete`() {
        var state = awaiting
        assertFalse(state.canSubmit)
        "12345".forEach { state = OtpStateMachine.reduce(state, OtpCommand.AppendDigit(it)) }
        assertFalse(state.canSubmit)
        state = OtpStateMachine.reduce(state, OtpCommand.AppendDigit('6'))
        assertTrue(state.canSubmit)
    }

    @Test
    fun `resend is blocked while the cooldown is running`() {
        assertFalse(awaiting.canResend)
        assertTrue(awaiting.copy(secondsUntilResend = 0).canResend)
    }

    @Test
    fun `a wrong code consumes one attempt and clears the entry`() {
        // Verifying, not AwaitingCode: onVerificationFailed only applies while a verify is in flight
        // (see `a verify result is ignored unless a verify is in flight`), which is the phase Submit
        // leaves the state in before the engine calls this reducer.
        val entered = awaiting.copy(phase = OtpState.Phase.Verifying, enteredCode = "123456")
        val next = OtpStateMachine.onVerificationFailed(entered, OtpErrors.otpInvalid())
        assertEquals(2, next.attemptsRemaining)
        assertEquals("", next.enteredCode)
        assertEquals(OtpState.Phase.AwaitingCode, next.phase)
        assertEquals(OtpErrors.OTP_INVALID, next.error?.code)
    }

    @Test
    fun `running out of attempts is terminal`() {
        var state = awaiting.copy(phase = OtpState.Phase.Verifying, enteredCode = "111111", attemptsRemaining = 1)
        state = OtpStateMachine.onVerificationFailed(state, OtpErrors.otpInvalid())
        assertEquals(0, state.attemptsRemaining)
        assertEquals(OtpState.Phase.Failed, state.phase)
        assertEquals(OtpErrors.OTP_ATTEMPTS_EXCEEDED, state.error?.code)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `expiry is terminal regardless of attempts remaining`() {
        val state = OtpStateMachine.onTick(awaiting.copy(secondsUntilExpiry = 1, secondsUntilResend = 0))
        assertEquals(OtpState.Phase.Failed, state.phase)
        assertEquals(OtpErrors.OTP_EXPIRED, state.error?.code)
    }

    @Test
    fun `tick decrements both counters but never below zero`() {
        val state = OtpStateMachine.onTick(awaiting.copy(secondsUntilExpiry = 60, secondsUntilResend = 1))
        assertEquals(59, state.secondsUntilExpiry)
        assertEquals(0, state.secondsUntilResend)
        val again = OtpStateMachine.onTick(state)
        assertEquals(0, again.secondsUntilResend)
    }

    @Test
    fun `cancel is terminal and reports cancellation`() {
        val state = OtpStateMachine.reduce(awaiting, OtpCommand.Cancel)
        assertEquals(OtpState.Phase.Failed, state.phase)
        assertEquals(SdkErrors.CANCELLED_BY_USER, state.error?.code)
    }

    @Test
    fun `cancel does not undo a terminal phase`() {
        val verified = OtpState.initial().copy(phase = OtpState.Phase.Verified)
        assertEquals(verified, OtpStateMachine.reduce(verified, OtpCommand.Cancel))
    }

    @Test
    fun `a verify result is ignored unless a verify is in flight`() {
        val expired = OtpState.initial().copy(phase = OtpState.Phase.Failed, error = OtpErrors.otpExpired())
        assertEquals(expired, OtpStateMachine.onVerified(expired))
        assertEquals(expired, OtpStateMachine.onVerificationFailed(expired, OtpErrors.otpInvalid()))
    }

    @Test
    fun `clearCode only clears while awaiting a code`() {
        val awaiting = OtpState.initial().copy(phase = OtpState.Phase.AwaitingCode, enteredCode = "12")
        assertEquals("", OtpStateMachine.clearCode(awaiting).enteredCode)
        val verifying = awaiting.copy(phase = OtpState.Phase.Verifying)
        assertEquals(verifying, OtpStateMachine.clearCode(verifying))
    }
}
