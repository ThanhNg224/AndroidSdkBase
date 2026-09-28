package io.github.thanhng224.sdkbase.otp.internal.runtime

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SdkSessionBase
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.telemetry.emitSafely
import io.github.thanhng224.sdkbase.otp.config.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.internal.engine.OtpEngine
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpSession
import io.github.thanhng224.sdkbase.otp.session.OtpState

/**
 * Wires an [OtpEngine] to the public [OtpSession] contract, on top of [SdkSessionBase]. Kept
 * Kotlin `internal`. Every operation is routed through [scope] (inherited from [SdkSessionBase]):
 * [dispatch] is fire-and-forget via `scope.launch`, [submit]/[resend] via `scope.ifOpen`, and their
 * Java twins via `scope.call` — so `close()` racing any of them is decided once, in [SessionScope],
 * instead of every feature re-implementing the same guard.
 */
internal class OtpSdkRuntime(
    scope: SessionScope,
    private val engine: OtpEngine,
    private val config: OtpSdkConfig,
    logger: SdkLogger,
) : SdkSessionBase<OtpState>(scope, engine.store), OtpSession {

    private val log = logger.tagged(TAG)

    private suspend fun submitInternal(code: String): SdkResult<Unit> =
        engine.submitCode(code).also { result ->
            if (result is SdkResult.Success) config.environment.telemetry.emitSafely("otp_verified")
        }

    override suspend fun submit(code: String): SdkResult<Unit> = scope.ifOpen { submitInternal(code) }

    override suspend fun resend(): SdkResult<Unit> = scope.ifOpen { engine.resend() }

    override fun dispatch(command: OtpCommand) {
        scope.launch { engine.dispatch(command) }
    }

    override fun submit(code: String, callback: ResultCallback<Unit>): Cancellable =
        scope.call(callback) { submitInternal(code) }

    override fun resend(callback: ResultCallback<Unit>): Cancellable =
        scope.call(callback) { engine.resend() }

    /** Runs exactly once, the first time [close] actually closes the session (see
     * [SdkSessionBase.close]'s idempotency guarantee) — a legitimate diagnostic, not a
     * workaround: lets a host (or a test) confirm a session actually got released instead of
     * leaked, e.g. when `OtpSdk.start(config, callback)` releases one it could not deliver. */
    override fun onClose() {
        log.d { "session closed" }
    }

    private companion object {
        const val TAG = "OtpSession"
    }
}
