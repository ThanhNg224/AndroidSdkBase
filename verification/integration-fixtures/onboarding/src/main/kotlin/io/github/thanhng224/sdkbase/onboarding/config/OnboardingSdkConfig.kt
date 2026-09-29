package {{SDK_NAMESPACE}}.onboarding.config

import {{SDK_NAMESPACE}}.otp.config.OtpSdkConfig
import {{SDK_NAMESPACE}}.profile.gateway.ProfileGateway

/** One-shot flow inputs. The profile feature receives the OTP configuration's exact environment. */
public class OnboardingSdkConfig(
    public val otp: OtpSdkConfig,
    public val profileGateway: ProfileGateway,
)
