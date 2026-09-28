package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.telemetry.emitSafely
import io.github.thanhng224.sdkbase.otp.config.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.internal.engine.OtpEngine
import io.github.thanhng224.sdkbase.otp.internal.runtime.OtpSdkRuntime
import io.github.thanhng224.sdkbase.otp.session.OtpSession
import io.github.thanhng224.sdkbase.otp.session.OtpState

/**
 * The only class a host needs to know about. It validates, wires and delegates — and nothing else.
 * No networking, no storage, no global mutable state, no DI container.
 */
public object OtpSdk {

    @JvmStatic
    public suspend fun start(config: OtpSdkConfig): SdkResult<OtpSession> {
        val environment = config.environment
        val sessionLogger = environment.logger.withSession(environment.idGenerator.newId())
        // The one SessionScope the whole session lives on: the engine's ticker runs on it, and a
        // start failure below closes it - there is no other CoroutineScope anywhere in this module.
        val scope = SessionScope(environment.dispatchers, sessionLogger.tagged("OtpSession"))
        val engine = OtpEngine(
            gateway = config.gateway,
            scope = scope,
            logger = sessionLogger,
            maxAttempts = config.maxAttempts,
            gatewayTimeoutMillis = config.gatewayTimeoutSeconds * 1_000L,
        )
        // OtpEngine.start is suspend and updates `state` synchronously (via direct suspend calls,
        // not a launch into its own scope) before returning, so inspecting state.value immediately
        // afterwards reflects the real outcome of the request — not a race.
        engine.start(config.destination)

        val state = engine.state.value
        if (state.phase == OtpState.Phase.Failed) {
            scope.close()
            // onFatal (see OtpStateMachine) always populates `error` from the gateway's own
            // SdkResult.Failure, so this reaches SdkErrors.unknown() only if that invariant is ever
            // broken — it is a defensive fallback, not the expected path.
            return SdkResult.Failure(state.error ?: SdkErrors.unknown())
        }

        environment.telemetry.emitSafely("otp_started", mapOf("max_attempts" to config.maxAttempts.toString()))
        return SdkResult.Success(OtpSdkRuntime(scope, engine, config, sessionLogger))
    }

    /**
     * The Java-callable twin of [start]: no `Continuation`, delivered on
     * [io.github.thanhng224.sdkbase.core.environment.SdkEnvironment.dispatchers]' main dispatcher. A
     * session that finishes starting but never gets delivered — the caller cancelled first — is
     * closed instead of leaked.
     */
    @JvmStatic
    public fun start(config: OtpSdkConfig, callback: ResultCallback<OtpSession>): Cancellable =
        launchCallback(
            dispatchers = config.environment.dispatchers,
            callback = callback,
            onUndelivered = { it.close() },
        ) { start(config) }
}
