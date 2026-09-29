package {{SDK_NAMESPACE}}.fixture

import {{SDK_NAMESPACE}}.core.environment.SdkEnvironment
import {{SDK_NAMESPACE}}.core.gateway.CompletionCallback
import {{SDK_NAMESPACE}}.core.gateway.GatewayCallback
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.onboarding.OnboardingSdk
import {{SDK_NAMESPACE}}.onboarding.config.OnboardingSdkConfig
import {{SDK_NAMESPACE}}.otp.config.OtpSdkConfig
import {{SDK_NAMESPACE}}.otp.gateway.OtpCallbackGateway
import {{SDK_NAMESPACE}}.otp.gateway.OtpChallenge
import {{SDK_NAMESPACE}}.profile.gateway.ProfileCallbackGateway
import {{SDK_NAMESPACE}}.profilecallback.ProfileCallbackAdapter

public object KotlinConsumer {
    @JvmStatic public fun createConfig(): OnboardingSdkConfig {
        val otpGateway = object : OtpCallbackGateway {
            override fun requestOtp(destination: String, callback: GatewayCallback<OtpChallenge>) {
                callback.onSuccess(OtpChallenge("fixture", 4, 60, 0))
            }
            override fun verifyOtp(challengeId: String, code: String, callback: CompletionCallback) {
                callback.onSuccess()
            }
        }
        val otpConfig = (OtpSdkConfig.Builder("fixture", otpGateway)
            .environment(SdkEnvironment.Builder().build()).build() as SdkResult.Success).value
        val hostProfile = ProfileCallbackGateway { callback -> callback.onSuccess("Ada") }
        return OnboardingSdkConfig(otpConfig, ProfileCallbackAdapter(hostProfile))
    }

    public suspend fun runSuspendCall(): SdkResult<String> =
        OnboardingSdk.start(createConfig(), "1234")
}
