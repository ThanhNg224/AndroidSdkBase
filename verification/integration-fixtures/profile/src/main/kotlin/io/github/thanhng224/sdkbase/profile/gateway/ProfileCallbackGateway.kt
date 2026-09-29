package {{SDK_NAMESPACE}}.profile.gateway

import {{SDK_NAMESPACE}}.core.gateway.GatewayCallback

/** Callback form for hosts whose backend client does not expose suspending calls. */
public fun interface ProfileCallbackGateway {
    public fun loadProfile(callback: GatewayCallback<String>)
}
