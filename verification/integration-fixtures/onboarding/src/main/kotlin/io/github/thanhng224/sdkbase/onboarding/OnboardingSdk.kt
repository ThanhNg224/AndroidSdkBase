package {{SDK_NAMESPACE}}.onboarding

import {{SDK_NAMESPACE}}.core.call.Cancellable
import {{SDK_NAMESPACE}}.core.call.ResultCallback
import {{SDK_NAMESPACE}}.core.call.launchCallback
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.core.session.SessionScope
import {{SDK_NAMESPACE}}.onboarding.config.OnboardingSdkConfig
import {{SDK_NAMESPACE}}.otp.OtpSdk
import {{SDK_NAMESPACE}}.otp.session.OtpSession
import {{SDK_NAMESPACE}}.profile.ProfileSdk
import {{SDK_NAMESPACE}}.profile.config.ProfileSdkConfig
import {{SDK_NAMESPACE}}.profile.session.ProfileSession
import kotlinx.coroutines.CancellationException

/** Runs request OTP, verify OTP, and profile lookup as one owned flow. */
public object OnboardingSdk {
    @JvmStatic
    public suspend fun start(config: OnboardingSdkConfig, code: String): SdkResult<String> {
        val environment = config.otp.environment
        val logger = environment.logger.withSession(environment.idGenerator.newId())
        val flowScope = SessionScope(environment.dispatchers, logger.tagged("Onboarding"))
        var otpSession: OtpSession? = null
        var profileSession: ProfileSession? = null
        return try {
            flowScope.ifOpen {
                when (val started = OtpSdk.start(config.otp)) {
                    is SdkResult.Failure -> started
                    is SdkResult.Success -> {
                        otpSession = started.value
                        when (val verified = started.value.submit(code)) {
                            is SdkResult.Failure -> verified
                            is SdkResult.Success -> {
                                val profileConfig = ProfileSdkConfig.Builder(config.profileGateway)
                                    .environment(environment)
                                    .build()
                                when (val loaded = ProfileSdk.start(profileConfig)) {
                                    is SdkResult.Failure -> loaded
                                    is SdkResult.Success -> {
                                        profileSession = loaded.value
                                        SdkResult.Success(loaded.value.state.value.displayName)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            profileSession?.close()
            otpSession?.close()
            flowScope.close()
        }
    }

    /** Java-callable twin delivered on the shared environment's main dispatcher. */
    @JvmStatic
    public fun start(
        config: OnboardingSdkConfig,
        code: String,
        callback: ResultCallback<String>,
    ): Cancellable = launchCallback(
        dispatchers = config.otp.environment.dispatchers,
        callback = callback,
    ) { start(config, code) }
}
