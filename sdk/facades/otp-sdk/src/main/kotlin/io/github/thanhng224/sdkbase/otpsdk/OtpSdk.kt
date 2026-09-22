package io.github.thanhng224.sdkbase.otpsdk

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpEngine
import io.github.thanhng224.sdkbase.otp.OtpState
import io.github.thanhng224.sdkbase.otpsdk.internal.OtpSdkRuntime
import io.github.thanhng224.sdkbase.platform.AndroidDispatchers

/**
 * The only class a host needs to know about. It validates, wires and delegates — and nothing else.
 * No networking, no storage, no global mutable state, no DI container.
 */
public object OtpSdk {

    public suspend fun start(config: OtpSdkConfig): SdkResult<OtpSession> {
        val engine = OtpEngine(
            gateway = config.gateway,
            dispatchers = AndroidDispatchers,
            logger = config.logger,
            maxAttempts = config.maxAttempts,
        )
        // OtpEngine.start is suspend and updates `state` synchronously (via direct suspend calls,
        // not a launch into its own scope) before returning, so inspecting state.value immediately
        // afterwards reflects the real outcome of the request — not a race.
        engine.start(config.destination)

        val state = engine.state.value
        if (state.phase == OtpState.Phase.Failed) {
            engine.close()
            // onFatal (see OtpStateMachine) always populates `error` from the gateway's own
            // SdkResult.Failure, so this reaches SdkErrors.unknown() only if that invariant is ever
            // broken — it is a defensive fallback, not the expected path.
            return SdkResult.Failure(state.error ?: SdkErrors.unknown())
        }

        config.telemetry?.onEvent("otp_started", mapOf("max_attempts" to config.maxAttempts.toString()))
        return SdkResult.Success(OtpSdkRuntime(engine, config))
    }
}
