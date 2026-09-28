package io.github.thanhng224.sdkbase.otp.session

import io.github.thanhng224.sdkbase.otp.OtpErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OtpState] became a plain class instead of a `data class` (ABI: `copy`/`componentN` would freeze
 * the property list for every consumer) — this proves it kept value equality by hand.
 */
class OtpStateTest {

    @Test
    fun otpStateEqualityIsByValue() {
        val a = OtpState.initial(codeLength = 6)
            .copy(phase = OtpState.Phase.AwaitingCode, enteredCode = "12", attemptsRemaining = 3)
        val b = OtpState.initial(codeLength = 6)
            .copy(phase = OtpState.Phase.AwaitingCode, enteredCode = "12", attemptsRemaining = 3)
        val differentEnteredCode = a.copy(enteredCode = "123")
        val differentError = a.copy(error = OtpErrors.otpInvalid())

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, differentEnteredCode)
        assertNotEquals(a, differentError)
        assertEquals(false, a.equals(null))
        assertEquals(false, a.equals("not a state"))
        assertTrue(a.toString().contains("AwaitingCode"))
    }
}
