package io.github.thanhng224.sdkbase.core.telemetry

import io.github.thanhng224.sdkbase.core.annotation.SdkInternalApi

/**
 * The single place telemetry is emitted from. Telemetry is host-supplied and ancillary: a throwing
 * [TelemetrySink] must never change an [io.github.thanhng224.sdkbase.core.result.SdkResult] or leak
 * the live session, so every call site goes through here instead of duplicating the try/catch.
 * Catches [Exception], never [Error].
 */
@SdkInternalApi
public fun TelemetrySink.emitSafely(name: String, attributes: Map<String, String> = emptyMap()) {
    try {
        onEvent(name, attributes)
    } catch (_: Exception) {
        // Telemetry is ancillary and must not change the caller's result or leak the live session.
    }
}
