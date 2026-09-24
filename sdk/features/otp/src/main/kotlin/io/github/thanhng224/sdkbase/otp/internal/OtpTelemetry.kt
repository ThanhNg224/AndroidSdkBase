package io.github.thanhng224.sdkbase.otp.internal

import io.github.thanhng224.sdkbase.core.telemetry.TelemetrySink

/**
 * The single place telemetry is emitted from. Telemetry is host-supplied and ancillary: a throwing
 * sink must never change an [io.github.thanhng224.sdkbase.core.result.SdkResult] or leak the live
 * engine, so every call site (`OtpSdk`, `OtpSdkRuntime`) goes through here instead of duplicating
 * the try/catch.
 */
internal fun TelemetrySink?.emitSafely(name: String, attributes: Map<String, String> = emptyMap()) {
    try {
        this?.onEvent(name, attributes)
    } catch (_: Exception) {
        // Telemetry is ancillary and must not change the OTP result or leak the live engine.
    }
}
