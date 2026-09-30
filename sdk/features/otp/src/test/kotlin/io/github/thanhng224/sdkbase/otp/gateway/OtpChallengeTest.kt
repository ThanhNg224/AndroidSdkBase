package io.github.thanhng224.sdkbase.otp.gateway

import io.github.thanhng224.sdkbase.core.testing.assertValueSemantics
import org.junit.Test

/**
 * [OtpChallenge] is a plain class instead of a `data class` (ABI: `copy`/`componentN` would
 * freeze the property list for every consumer) — this proves its hand-written value semantics
 * cover every property.
 */
class OtpChallengeTest {

    @Test
    fun otpChallengeValueSemanticsCoverEveryProperty() {
        assertValueSemantics(
            { OtpChallenge("ch-1", 6, 60, 30) },
            OtpChallenge("ch-2", 6, 60, 30),
            OtpChallenge("ch-1", 4, 60, 30),
            OtpChallenge("ch-1", 6, 61, 30),
            OtpChallenge("ch-1", 6, 60, 31),
        )
    }
}
