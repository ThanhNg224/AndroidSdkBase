package io.github.thanhng224.sdkbase.otp.internal

import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpCommand
import io.github.thanhng224.sdkbase.otp.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.OtpSession
import io.github.thanhng224.sdkbase.otp.OtpState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Wires an [OtpEngine] to the public [OtpSession] contract. Kept in an `internal` **package**, not
 * just marked Kotlin-`internal`: the ABI baseline tool (`sdkbase.abi`) only excludes declarations by
 * package path, so a top-level `internal` class would still show up in the committed dump (see
 * docs/COMPATIBILITY.md and the lesson from Task 7).
 */
internal class OtpSdkRuntime(
    private val engine: OtpEngine,
    private val config: OtpSdkConfig,
) : OtpSession {

    // dispatch() below is deliberately non-suspending (a UI callback can't suspend), so it needs a
    // scope to fire-and-forget into the suspend engine. AndroidDispatchers is the same dispatcher
    // instance OtpSdk.start used to construct the engine. The CoroutineExceptionHandler is not
    // optional: a SupervisorJob with no handler rethrows an uncaught exception to the thread's
    // default handler, which on Android kills the host process — see OtpEngine's own scope for the
    // same reasoning.
    private val scope = CoroutineScope(
        SupervisorJob() + AndroidDispatchers.default +
            CoroutineExceptionHandler { _, t -> config.logger.error(TAG, "dispatch failed", t) },
    )

    override val state: StateFlow<OtpState> get() = engine.state

    override suspend fun submit(code: String): SdkResult<Unit> =
        engine.submitCode(code).also { result ->
            if (result is SdkResult.Success) config.telemetry?.onEvent("otp_verified", emptyMap())
        }

    override suspend fun resend(): SdkResult<Unit> = engine.resend()

    override fun dispatch(command: OtpCommand) {
        scope.launch { engine.dispatch(command) }
    }

    override fun close() {
        scope.cancel()
        engine.close()
    }

    private companion object {
        const val TAG = "OtpSession"
    }
}
