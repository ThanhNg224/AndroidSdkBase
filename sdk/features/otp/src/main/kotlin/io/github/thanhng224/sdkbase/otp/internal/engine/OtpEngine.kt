package io.github.thanhng224.sdkbase.otp.internal.engine

import io.github.thanhng224.sdkbase.core.call.safeCall
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.core.logging.SdkLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.session.StateStore
import io.github.thanhng224.sdkbase.otp.OtpErrors
import io.github.thanhng224.sdkbase.otp.gateway.OtpGateway
import io.github.thanhng224.sdkbase.otp.session.OtpCommand
import io.github.thanhng224.sdkbase.otp.session.OtpState
import kotlinx.coroutines.flow.StateFlow

/**
 * Drives the OTP flow. Owns the effects (gateway calls, countdown); delegates every rule to
 * [OtpStateMachine]. Headless by design: no Android view, no Compose, no navigation. A host can
 * render [state] with its own UI, or add `otp-ui-compose` and get a screen for free.
 *
 * Every state transition — a UI-driven [dispatch], a host-driven [submitCode]/[resend], and the
 * timer's own tick — is serialized through [store]'s `withLock`, so no two can ever interleave.
 * The engine owns no [kotlinx.coroutines.CoroutineScope] of its own: [scope] is the single teardown
 * (`SessionScope.close`), which also stops the ticker since it runs on `scope.coroutineScope`.
 */
internal class OtpEngine(
    private val gateway: OtpGateway,
    private val scope: SessionScope,
    private val logger: SdkLogger = SdkLogger.NoOp,
    private val maxAttempts: Int = 3,
    private val gatewayTimeoutMillis: Long = 30_000L,
) {
    private val log = logger.tagged(TAG)
    private val timer = OtpTimer(scope.coroutineScope)

    /** The single mutation point: `onChange` stops the ticker the moment the phase becomes
     * terminal, so no caller has to remember to do it after every `update`. */
    public val store: StateStore<OtpState> = StateStore(OtpState.initial()) { newState ->
        if (newState.phase == OtpState.Phase.Verified || newState.phase == OtpState.Phase.Failed) {
            timer.stop()
        }
    }

    /** Convenience read of [store]'s own flow. */
    public val state: StateFlow<OtpState> get() = store.state

    private var challengeId: String? = null
    private var started = false
    private var lastDestination: String? = null

    /** Requests the first challenge for [destination]. Safe to call once per session. */
    public suspend fun start(destination: String): Unit = store.withLock {
        if (started) {
            update { it.copy(error = SdkErrors.alreadyRunning()) }
            return@withLock
        }
        started = true
        update { it.copy(phase = OtpState.Phase.Requesting, error = null) }
        log.d { "requesting challenge for ${DefaultRedactor.mask(destination, keepLast = 3)}" }
        requestChallenge(destination)
    }

    /** Applies [command], performing any effect it implies. */
    public suspend fun dispatch(command: OtpCommand): Unit = store.withLock { dispatchLocked(command) }

    /** Replaces any partial entry with [code] and submits it, atomically with respect to [dispatch]. */
    public suspend fun submitCode(code: String): SdkResult<Unit> = store.withLock {
        update { OtpStateMachine.clearCode(it) }
        code.forEach { dispatchLocked(OtpCommand.AppendDigit(it)) }
        dispatchLocked(OtpCommand.Submit)
        if (current.phase == OtpState.Phase.Verified) {
            SdkResult.Success(Unit)
        } else {
            SdkResult.Failure(current.error ?: OtpErrors.otpInvalid())
        }
    }

    /** Resends the challenge, atomically with respect to [dispatch]. */
    public suspend fun resend(): SdkResult<Unit> = store.withLock {
        dispatchLocked(OtpCommand.Resend)
        val error = current.error
        when {
            error != null -> SdkResult.Failure(error)
            current.phase == OtpState.Phase.Failed -> SdkResult.Failure(SdkErrors.unknown())
            else -> SdkResult.Success(Unit)
        }
    }

    /** The body [dispatch] used to be, run only while [store]'s lock is already held. */
    private suspend fun StateStore.Mutation<OtpState>.dispatchLocked(command: OtpCommand) {
        when (command) {
            OtpCommand.Submit -> {
                if (!current.canSubmit) return
                val code = current.enteredCode
                update { OtpStateMachine.reduce(it, OtpCommand.Submit) }
                verify(code)
            }

            OtpCommand.Resend -> {
                if (!current.canResend) {
                    update {
                        it.copy(error = OtpErrors.otpResendTooSoon(it.secondsUntilResend))
                    }
                    return
                }
                update { OtpStateMachine.reduce(it, OtpCommand.Resend) }
                requestChallenge(lastDestination ?: return)
            }

            else -> update { OtpStateMachine.reduce(it, command) }
        }
    }

    private suspend fun StateStore.Mutation<OtpState>.requestChallenge(destination: String) {
        lastDestination = destination
        when (
            val result = safeCall("requestOtp", gatewayTimeoutMillis) { gateway.requestOtp(destination) }
        ) {
            is SdkResult.Success -> {
                val violation = result.value.violation()
                if (violation != null) {
                    log.e { "invalid challenge from host: $violation" }
                    update {
                        OtpStateMachine.onFatal(
                            it,
                            SdkErrors.gatewayFailure("requestOtp returned an invalid challenge: $violation"),
                        )
                    }
                    return
                }
                challengeId = result.value.challengeId
                update {
                    OtpStateMachine.onChallengeIssued(
                        state = it,
                        codeLength = result.value.codeLength,
                        expiresInSeconds = result.value.expiresInSeconds,
                        resendAfterSeconds = result.value.resendAfterSeconds,
                        maxAttempts = maxAttempts,
                    )
                }
                // A challenge just became active: (re)arm the countdown. `OtpTimer.start` stops any
                // previous job first, so a resend cleanly replaces the prior challenge's ticker. Each
                // tick takes `store`'s lock itself — it runs on `scope.coroutineScope`, a coroutine
                // independent of this one, so it simply waits its turn instead of nesting.
                timer.start { store.withLock { update(OtpStateMachine::onTick) } }
            }

            is SdkResult.Failure -> {
                log.e { "challenge request failed: ${result.error}" }
                update { OtpStateMachine.onFatal(it, result.error) }
            }
        }
    }

    private suspend fun StateStore.Mutation<OtpState>.verify(code: String) {
        val id = challengeId
        if (id == null) {
            update { OtpStateMachine.onFatal(it, SdkErrors.notStarted()) }
            return
        }
        when (val result = safeCall("verifyOtp", gatewayTimeoutMillis) { gateway.verifyOtp(id, code) }) {
            is SdkResult.Success -> update { OtpStateMachine.onVerified(it) }
            is SdkResult.Failure -> update {
                OtpStateMachine.onVerificationFailed(it, result.error)
            }
        }
    }

    private companion object {
        const val TAG = "OtpEngine"
    }
}
