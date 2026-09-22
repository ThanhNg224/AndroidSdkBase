package io.github.thanhng224.sdkbase.core.gateway

/**
 * Business telemetry. The SDK emits named events; the host decides whether and where they go.
 * The SDK never opens its own analytics connection.
 */
public fun interface TelemetrySink {
    public fun onEvent(name: String, attributes: Map<String, String>)
}
