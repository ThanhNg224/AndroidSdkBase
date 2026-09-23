package io.github.thanhng224.sdkbase.demo

import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpChallenge
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.OtpGateway
import kotlinx.coroutines.delay

/**
 * This is the host's job, not the SDK's — which is the point. A real app would put its Retrofit or
 * Ktor call here. The SDK never learns which client the host uses.
 *
 * The accepted code is 123456.
 */
internal class FakeOtpGateway : OtpGateway {

    override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> {
        delay(400)
        return SdkResult.Success(
            OtpChallenge(
                challengeId = "demo-challenge",
                codeLength = 6,
                expiresInSeconds = 120,
                resendAfterSeconds = 15,
            )
        )
    }

    override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> {
        delay(400)
        return if (code == "123456") {
            SdkResult.Success(Unit)
        } else {
            SdkResult.Failure(OtpErrors.otpInvalid())
        }
    }
}
