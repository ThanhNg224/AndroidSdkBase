package {{SDK_NAMESPACE}}.profile.gateway

import {{SDK_NAMESPACE}}.core.result.SdkResult

/** Host-owned lookup of the profile created by a successful OTP verification. */
public fun interface ProfileGateway {
    public suspend fun loadProfile(): SdkResult<String>
}
