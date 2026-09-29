package io.github.thanhng224.sdkbase.otp.internal.engine

import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpChallengeValidationTest {
    private fun challenge(
        id: String = "ch-1",
        codeLength: Int = 6,
        expires: Int = 60,
        resendAfter: Int = 30,
    ) = OtpChallenge(id, codeLength, expires, resendAfter)

    @Test
    fun aValidChallengeHasNoViolation() {
        assertNull(challenge().violation())
    }

    @Test
    fun aBlankChallengeIdIsRejected() {
        assertEquals("challengeId is blank", challenge(id = "  ").violation())
    }

    @Test
    fun codeLengthMustBeFourToTen() {
        assertEquals("codeLength must be in 4..10, was 3", challenge(codeLength = 3).violation())
        assertEquals("codeLength must be in 4..10, was 11", challenge(codeLength = 11).violation())
        assertNull(challenge(codeLength = 4).violation())
        assertNull(challenge(codeLength = 10).violation())
    }

    @Test
    fun expiryMustBePositive() {
        assertEquals("expiresInSeconds must be > 0, was 0", challenge(expires = 0).violation())
        assertEquals("expiresInSeconds must be > 0, was -5", challenge(expires = -5).violation())
    }

    @Test
    fun resendDelayMustNotBeNegative() {
        assertEquals("resendAfterSeconds must be >= 0, was -1", challenge(resendAfter = -1).violation())
        assertNull(challenge(resendAfter = 0).violation())
    }

    @Test
    fun theFirstViolationWins() {
        assertEquals("challengeId is blank", challenge(id = "", codeLength = 1, expires = 0).violation())
    }
}
