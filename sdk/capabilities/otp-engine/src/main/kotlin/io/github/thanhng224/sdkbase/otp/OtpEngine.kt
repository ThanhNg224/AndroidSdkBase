package io.github.thanhng224.sdkbase.otp

import io.github.thanhng224.sdkbase.core.DispatcherProvider
import io.github.thanhng224.sdkbase.core.SdkErrors
import io.github.thanhng224.sdkbase.core.SdkLogger
import io.github.thanhng224.sdkbase.core.SdkResult
import io.github.thanhng224.sdkbase.core.gateway.OtpGateway
import io.github.thanhng224.sdkbase.core.redact
import io.github.thanhng224.sdkbase.otp.internal.OtpStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Drives the OTP flow. Owns the effects (gateway calls, countdown); delegates every rule to
 * [OtpStateMachine]. Headless by design: no Android view, no Compose, no navigation. A host can
 * render [state] with its own UI, or add `otp-ui-compose` and get a screen for free.
 *
 * The engine owns its [CoroutineScope] and cancels it in [close]. Nothing here touches
 * `GlobalScope` or a static dispatcher.
 */
public class OtpEngine(
    private val gateway: OtpGateway,
    private val dispatchers: DispatcherProvider,
    private val logger: SdkLogger = SdkLogger.NoOp,
    private val maxAttempts: Int = 3,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _state = MutableStateFlow(OtpState.initial())
    private var challengeId: String? = null

    public val state: StateFlow<OtpState> = _state.asStateFlow()

    /** Requests the first challenge for [destination]. Safe to call once per session. */
    public suspend fun start(destination: String) {
        _state.update { it.copy(phase = OtpState.Phase.Requesting, error = null) }
        logger.debug(TAG, "requesting challenge for ${redact(destination, keepLast = 3)}")
        requestChallenge(destination)
    }

    /** Applies [command], performing any effect it implies. */
    public suspend fun dispatch(command: OtpCommand) {
        when (command) {
            OtpCommand.Submit -> {
                if (!_state.value.canSubmit) return
                val code = _state.value.enteredCode
                _state.update { OtpStateMachine.reduce(it, OtpCommand.Submit) }
                verify(code)
            }

            OtpCommand.Resend -> {
                if (!_state.value.canResend) {
                    _state.update {
                        it.copy(error = SdkErrors.otpResendTooSoon(it.secondsUntilResend))
                    }
                    return
                }
                _state.update { OtpStateMachine.reduce(it, OtpCommand.Resend) }
                requestChallenge(lastDestination ?: return)
            }

            else -> _state.update { OtpStateMachine.reduce(it, command) }
        }
    }

    /** Cancels the engine's scope. After this the engine must not be reused. */
    public fun close() {
        scope.cancel()
    }

    private var lastDestination: String? = null

    private suspend fun requestChallenge(destination: String) {
        lastDestination = destination
        when (val result = gateway.requestOtp(destination)) {
            is SdkResult.Success -> {
                challengeId = result.value.challengeId
                _state.update {
                    OtpStateMachine.onChallengeIssued(
                        state = it,
                        codeLength = result.value.codeLength,
                        expiresInSeconds = result.value.expiresInSeconds,
                        resendAfterSeconds = result.value.resendAfterSeconds,
                        maxAttempts = maxAttempts,
                    )
                }
            }

            is SdkResult.Failure -> {
                logger.error(TAG, "challenge request failed: ${result.error}")
                _state.update { OtpStateMachine.onFatal(it, result.error) }
            }
        }
    }

    private suspend fun verify(code: String) {
        val id = challengeId
        if (id == null) {
            _state.update { OtpStateMachine.onFatal(it, SdkErrors.notStarted()) }
            return
        }
        when (val result = gateway.verifyOtp(id, code)) {
            is SdkResult.Success -> _state.update { OtpStateMachine.onVerified(it) }
            is SdkResult.Failure -> _state.update {
                OtpStateMachine.onVerificationFailed(it, result.error)
            }
        }
    }

    private companion object {
        const val TAG = "OtpEngine"
    }
}
