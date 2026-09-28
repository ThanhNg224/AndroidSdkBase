package io.github.thanhng224.sdkbase.otp.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OtpChallenge] became a plain class instead of a `data class` (ABI: `copy`/`componentN` would
 * freeze the property list for every consumer) — this proves it kept value equality by hand.
 */
class OtpChallengeTest {

    @Test
    fun otpChallengeEqualityIsByValue() {
        val a = OtpChallenge("ch-1", 6, 60, 30)
        val b = OtpChallenge("ch-1", 6, 60, 30)
        val differentId = OtpChallenge("ch-2", 6, 60, 30)
        val differentCodeLength = OtpChallenge("ch-1", 4, 60, 30)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, differentId)
        assertNotEquals(a, differentCodeLength)
        assertEquals(false, a.equals(null))
        assertEquals(false, a.equals("not a challenge"))
        assertTrue(a.toString().contains("ch-1"))
    }
}
