package io.github.thanhng224.sdkbase.otp.fakesms

import com.fakesms.sdk.FakeSmsClient
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpErrors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeSmsOtpGatewayTest {
    private fun TestScope.gateway() = FakeSmsOtpGateway(FakeSmsClient(), StandardTestDispatcher(testScheduler))

    @Test
    fun requestReturnsAChallengeFromTheVendor() = runTest {
        val result = gateway().requestOtp("+84900000000")

        val challenge = (result as SdkResult.Success).value
        assertTrue(challenge.challengeId.startsWith("fake-"))
        assertEquals(6, challenge.codeLength)
    }

    @Test
    fun aVendorFailureBecomesAnSdkErrorNotAnException() = runTest {
        val result = gateway().requestOtp("  ")

        assertTrue(result is SdkResult.Failure)
    }

    @Test
    fun theAcceptedCodeVerifies() = runTest {
        val gateway = gateway()
        val id = ((gateway.requestOtp("+84900000000")) as SdkResult.Success).value.challengeId

        assertEquals(SdkResult.Success(Unit), gateway.verifyOtp(id, FakeSmsClient.ACCEPTED_CODE))
    }

    @Test
    fun aWrongCodeIsTheOtpInvalidError() = runTest {
        val gateway = gateway()
        val id = ((gateway.requestOtp("+84900000000")) as SdkResult.Success).value.challengeId

        val failure = gateway.verifyOtp(id, "111111") as SdkResult.Failure

        assertEquals(OtpErrors.OTP_INVALID, failure.error.code)
    }

    @Test
    fun anUnknownChallengeIsAFailure() = runTest {
        assertTrue(gateway().verifyOtp("nope", "000000") is SdkResult.Failure)
    }
}
