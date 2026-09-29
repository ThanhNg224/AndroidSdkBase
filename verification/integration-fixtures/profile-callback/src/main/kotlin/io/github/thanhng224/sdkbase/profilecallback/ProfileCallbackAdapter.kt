package {{SDK_NAMESPACE}}.profilecallback

import {{SDK_NAMESPACE}}.core.gateway.awaitCallback
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.profile.gateway.ProfileCallbackGateway
import {{SDK_NAMESPACE}}.profile.gateway.ProfileGateway

/** Wraps a host callback client as the profile feature's suspending gateway. */
public class ProfileCallbackAdapter(private val host: ProfileCallbackGateway) : ProfileGateway {
    override suspend fun loadProfile(): SdkResult<String> =
        awaitCallback(host::loadProfile)
}
