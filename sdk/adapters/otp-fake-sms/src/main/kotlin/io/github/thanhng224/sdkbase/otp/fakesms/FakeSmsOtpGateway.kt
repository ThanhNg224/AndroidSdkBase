package io.github.thanhng224.sdkbase.otp.fakesms

import com.fakesms.sdk.FakeSmsClient
import io.github.thanhng224.sdkbase.core.call.safeCall
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge
import io.github.thanhng224.sdkbase.otp.gateway.OtpGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An [OtpGateway] backed by the fake SMS vendor's blocking SDK. It lives in the `adapter` zone
 * because the vendor's jar has no Maven coordinate: a host that wants this copies the adapter and
 * the binary, or writes its own gateway. Nothing published depends on it.
 *
 * The vendor calls block, so they run on [dispatcher]; [safeCall] maps anything the vendor throws to
 * an [io.github.thanhng224.sdkbase.core.error.SdkError].
 */
public class FakeSmsOtpGateway @JvmOverloads constructor(
    private val client: FakeSmsClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OtpGateway {

    override suspend fun requestOtp(destination: String): SdkResult<OtpChallenge> =
        safeCall("fake sms request") {
            val challengeId = withContext(dispatcher) { client.requestCode(destination) }
            SdkResult.Success(
                OtpChallenge(
                    challengeId = challengeId,
                    codeLength = CODE_LENGTH,
                    expiresInSeconds = EXPIRES_IN_SECONDS,
                    resendAfterSeconds = RESEND_AFTER_SECONDS,
                ),
            )
        }

    override suspend fun verifyOtp(challengeId: String, code: String): SdkResult<Unit> =
        safeCall("fake sms verify") {
            val accepted = withContext(dispatcher) { client.checkCode(challengeId, code) }
            if (accepted) SdkResult.Success(Unit) else SdkResult.Failure(OtpErrors.otpInvalid())
        }

    private companion object {
        const val CODE_LENGTH = 6
        const val EXPIRES_IN_SECONDS = 120
        const val RESEND_AFTER_SECONDS = 30
    }
}
