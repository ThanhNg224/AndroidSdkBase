package io.github.thanhng224.sdkbase.otp.session

import io.github.thanhng224.sdkbase.core.testing.assertValueSemantics
import io.github.thanhng224.sdkbase.otp.OtpErrors
import org.junit.Test

/**
 * [OtpState] is a plain class instead of a `data class` (ABI: `copy`/`componentN` would freeze the
 * property list for every consumer) — this proves its hand-written value semantics cover every
 * property.
 */
class OtpStateTest {

    private fun base(): OtpState = OtpState(
        phase = OtpState.Phase.AwaitingCode,
        codeLength = 6,
        enteredCode = "12",
        attemptsRemaining = 3,
        secondsUntilResend = 30,
        secondsUntilExpiry = 60,
        error = null,
    )

    @Test
    fun otpStateValueSemanticsCoverEveryProperty() {
        val base = base()
        assertValueSemantics(
            ::base,
            base.copy(phase = OtpState.Phase.Verifying),
            base.copy(codeLength = 4),
            base.copy(enteredCode = "123"),
            base.copy(attemptsRemaining = 2),
            base.copy(secondsUntilResend = 29),
            base.copy(secondsUntilExpiry = 59),
            base.copy(error = OtpErrors.otpInvalid()),
        )
    }
}
