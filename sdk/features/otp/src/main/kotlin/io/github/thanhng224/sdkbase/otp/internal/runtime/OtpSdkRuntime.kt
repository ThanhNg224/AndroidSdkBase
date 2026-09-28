package io.github.thanhng224.sdkbase.otp.internal.runtime

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.StateListener
import io.github.thanhng224.sdkbase.core.session.observe
import io.github.thanhng224.sdkbase.otp.config.OtpSdkConfig
import io.github.thanhng224.sdkbase.otp.internal.emitSafely
import io.github.thanhng224.sdkbase.otp.internal.engine.OtpEngine
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpSession
import io.github.thanhng224.sdkbase.otp.session.OtpState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Wires an [OtpEngine] to the public [OtpSession] contract. Kept Kotlin `internal`. */
internal class OtpSdkRuntime(
    private val engine: OtpEngine,
    private val config: OtpSdkConfig,
    logger: SdkLogger,
) : OtpSession {

    private val log = logger.tagged(TAG)

    // Held directly (not read back off `scope`) so submit/resend/observeState can hand it to
    // launchCallback/observe as `parent`: cancelling it in close() below cancels every pending
    // Java-callable call this session has outstanding, not just dispatch()'s own fire-and-forget
    // launches.
    private val job = SupervisorJob()

    // dispatch() is non-suspending (a UI callback can't suspend), so it fires into this scope
    // instead. The handler matters: a SupervisorJob with none rethrows an uncaught exception to the
    // thread's default handler, which on Android kills the host process.
    private val scope = CoroutineScope(
        job + config.environment.dispatchers.default +
            CoroutineExceptionHandler { _, t -> log.e(t) { "dispatch failed" } },
    )

    override val state: StateFlow<OtpState> get() = engine.state

    override suspend fun submit(code: String): SdkResult<Unit> =
        engine.submitCode(code).also { result ->
            if (result is SdkResult.Success) config.environment.telemetry.emitSafely("otp_verified")
        }

    override suspend fun resend(): SdkResult<Unit> = engine.resend()

    override fun dispatch(command: OtpCommand) {
        scope.launch { engine.dispatch(command) }
    }

    override fun close() {
        scope.cancel()
        engine.close()
        // A legitimate diagnostic, not a workaround: lets a host (or a test) confirm a session
        // actually got released instead of leaked, e.g. when `OtpSdk.start(config, callback)`
        // releases one it could not deliver. close() has no other idempotency guard, so like
        // scope.cancel()/engine.close() above, calling close() again logs again rather than once.
        log.d { "session closed" }
    }

    override fun submit(code: String, callback: ResultCallback<Unit>): Cancellable =
        launchCallback(config.environment.dispatchers, callback, parent = job) { submit(code) }

    override fun resend(callback: ResultCallback<Unit>): Cancellable =
        launchCallback(config.environment.dispatchers, callback, parent = job) { resend() }

    override fun observeState(listener: StateListener<OtpState>): Cancellable =
        state.observe(config.environment.dispatchers, listener, parent = job)

    private companion object {
        const val TAG = "OtpSession"
    }
}
