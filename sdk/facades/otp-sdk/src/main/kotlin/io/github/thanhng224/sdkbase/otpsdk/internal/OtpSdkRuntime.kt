package io.github.thanhng224.sdkbase.otpsdk.internal

import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpCommand
import io.github.thanhng224.sdkbase.otp.OtpEngine
import io.github.thanhng224.sdkbase.otp.OtpState
import io.github.thanhng224.sdkbase.otpsdk.OtpSdkConfig
import io.github.thanhng224.sdkbase.otpsdk.OtpSession
import io.github.thanhng224.sdkbase.platform.AndroidDispatchers
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
    // instance OtpSdk.start used to construct the engine.
    private val scope = CoroutineScope(SupervisorJob() + AndroidDispatchers.default)

    override val state: StateFlow<OtpState> get() = engine.state

    override suspend fun submit(code: String): SdkResult<Unit> {
        // Clear whatever the engine already holds before entering the caller's code. Appending on
        // top of a stale partial entry (left over from a UI dispatch, or SMS autofill racing a
        // hand-typed digit) would silently submit a wrong, mixed code.
        val staleDigits = engine.state.value.enteredCode.length
        repeat(staleDigits) { engine.dispatch(OtpCommand.DeleteDigit) }
        code.forEach { engine.dispatch(OtpCommand.AppendDigit(it)) }
        engine.dispatch(OtpCommand.Submit)

        val current = engine.state.value
        return when (current.phase) {
            OtpState.Phase.Verified -> {
                config.telemetry?.onEvent("otp_verified", emptyMap())
                SdkResult.Success(Unit)
            }
            else -> SdkResult.Failure(current.error ?: SdkErrors.otpInvalid())
        }
    }

    override suspend fun resend(): SdkResult<Unit> {
        engine.dispatch(OtpCommand.Resend)
        val error = engine.state.value.error
        return if (error?.code == SdkErrors.OTP_RESEND_TOO_SOON) {
            SdkResult.Failure(error)
        } else {
            SdkResult.Success(Unit)
        }
    }

    override fun dispatch(command: OtpCommand) {
        scope.launch { engine.dispatch(command) }
    }

    override fun close() {
        scope.cancel()
        engine.close()
    }
}
