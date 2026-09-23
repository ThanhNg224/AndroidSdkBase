package io.github.thanhng224.sdkbase.otp.internal

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.otp.OtpCommand
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.OtpGateway
import io.github.thanhng224.sdkbase.otp.OtpState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Drives the OTP flow. Owns the effects (gateway calls, countdown); delegates every rule to
 * [OtpStateMachine]. Headless by design: no Android view, no Compose, no navigation. A host can
 * render [state] with its own UI, or add `otp-ui-compose` and get a screen for free.
 *
 * The engine owns its [CoroutineScope] and cancels it in [close]. Nothing here touches
 * `GlobalScope` or a static dispatcher.
 */
internal class OtpEngine(
    private val gateway: OtpGateway,
    private val dispatchers: DispatcherProvider,
    private val logger: SdkLogger = SdkLogger.NoOp,
    private val maxAttempts: Int = 3,
    private val gatewayTimeoutMillis: Long = 30_000L,
) {
    // A SupervisorJob with no handler rethrows an uncaught exception to the thread's default
    // handler, which on Android kills the host process. The ticker runs in this scope, so it needs
    // one; every gateway call that could throw goes through `callGateway` instead.
    private val log = logger.tagged(TAG)
    private val scope = CoroutineScope(
        SupervisorJob() + dispatchers.default +
            CoroutineExceptionHandler { _, throwable -> log.e(throwable) { "unexpected failure" } },
    )
    private val timer = OtpTimer(scope)
    private val _state = MutableStateFlow(OtpState.initial())
    private var challengeId: String? = null

    // Guards every state transition: `dispatch` (fired from a UI callback on Dispatchers.Default via
    // OtpSdkRuntime) and `submitCode`/`resend` (called directly by a host coroutine) must not
    // interleave, or two concurrent submits could both observe `canSubmit` true and both verify.
    private val mutex = Mutex()
    private var started = false

    public val state: StateFlow<OtpState> = _state.asStateFlow()

    /** Requests the first challenge for [destination]. Safe to call once per session. */
    public suspend fun start(destination: String): Unit = mutex.withLock {
        if (started) {
            updateState { it.copy(error = SdkErrors.alreadyRunning()) }
            return@withLock
        }
        started = true
        updateState { it.copy(phase = OtpState.Phase.Requesting, error = null) }
        log.d { "requesting challenge for ${DefaultRedactor.mask(destination, keepLast = 3)}" }
        requestChallenge(destination)
    }

    /** Applies [command], performing any effect it implies. */
    public suspend fun dispatch(command: OtpCommand): Unit = mutex.withLock { dispatchLocked(command) }

    /** Replaces any partial entry with [code] and submits it, atomically with respect to [dispatch]. */
    public suspend fun submitCode(code: String): SdkResult<Unit> = mutex.withLock {
        updateState { OtpStateMachine.clearCode(it) }
        code.forEach { dispatchLocked(OtpCommand.AppendDigit(it)) }
        dispatchLocked(OtpCommand.Submit)
        val current = _state.value
        if (current.phase == OtpState.Phase.Verified) {
            SdkResult.Success(Unit)
        } else {
            SdkResult.Failure(current.error ?: OtpErrors.otpInvalid())
        }
    }

    /** Resends the challenge, atomically with respect to [dispatch]. */
    public suspend fun resend(): SdkResult<Unit> = mutex.withLock {
        dispatchLocked(OtpCommand.Resend)
        val current = _state.value
        val error = current.error
        when {
            error != null -> SdkResult.Failure(error)
            current.phase == OtpState.Phase.Failed -> SdkResult.Failure(SdkErrors.unknown())
            else -> SdkResult.Success(Unit)
        }
    }

    /** Cancels the engine's scope; the engine must not be reused. */
    public fun close() {
        timer.stop()
        scope.cancel()
    }

    private var lastDestination: String? = null

    /** The single mutation point, so the ticker always stops when the phase becomes terminal. */
    private fun updateState(transform: (OtpState) -> OtpState) {
        _state.update(transform)
        if (_state.value.phase == OtpState.Phase.Verified || _state.value.phase == OtpState.Phase.Failed) {
            timer.stop()
        }
    }

    /** The body [dispatch] used to be, run only while [mutex] is already held. */
    private suspend fun dispatchLocked(command: OtpCommand) {
        when (command) {
            OtpCommand.Submit -> {
                if (!_state.value.canSubmit) return
                val code = _state.value.enteredCode
                updateState { OtpStateMachine.reduce(it, OtpCommand.Submit) }
                verify(code)
            }

            OtpCommand.Resend -> {
                if (!_state.value.canResend) {
                    updateState {
                        it.copy(error = OtpErrors.otpResendTooSoon(it.secondsUntilResend))
                    }
                    return
                }
                updateState { OtpStateMachine.reduce(it, OtpCommand.Resend) }
                requestChallenge(lastDestination ?: return)
            }

            else -> updateState { OtpStateMachine.reduce(it, command) }
        }
    }

    private suspend fun requestChallenge(destination: String) {
        lastDestination = destination
        when (val result = callGateway("requestOtp") { gateway.requestOtp(destination) }) {
            is SdkResult.Success -> {
                challengeId = result.value.challengeId
                updateState {
                    OtpStateMachine.onChallengeIssued(
                        state = it,
                        codeLength = result.value.codeLength,
                        expiresInSeconds = result.value.expiresInSeconds,
                        resendAfterSeconds = result.value.resendAfterSeconds,
                        maxAttempts = maxAttempts,
                    )
                }
                // A challenge just became active: (re)arm the countdown. `OtpTimer.start` stops any
                // previous job first, so a resend cleanly replaces the prior challenge's ticker.
                timer.start { updateState { OtpStateMachine.onTick(it) } }
            }

            is SdkResult.Failure -> {
                log.e { "challenge request failed: ${result.error}" }
                updateState { OtpStateMachine.onFatal(it, result.error) }
            }
        }
    }

    private suspend fun verify(code: String) {
        val id = challengeId
        if (id == null) {
            updateState { OtpStateMachine.onFatal(it, SdkErrors.notStarted()) }
            return
        }
        when (val result = callGateway("verifyOtp") { gateway.verifyOtp(id, code) }) {
            is SdkResult.Success -> updateState { OtpStateMachine.onVerified(it) }
            is SdkResult.Failure -> updateState {
                OtpStateMachine.onVerificationFailed(it, result.error)
            }
        }
    }

    /** Every host call goes through here: nothing the host throws or hangs on reaches the caller. */
    private suspend fun <T> callGateway(operation: String, call: suspend () -> SdkResult<T>): SdkResult<T> =
        try {
            withTimeout(gatewayTimeoutMillis) { call() }
        } catch (e: TimeoutCancellationException) {
            SdkResult.Failure(SdkErrors.timeout(operation))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SdkResult.Failure(SdkErrors.networkUnavailable(e))
        } catch (e: Exception) {
            SdkResult.Failure(SdkErrors.gatewayFailure(operation, e))
        }

    private companion object {
        const val TAG = "OtpEngine"
    }
}
