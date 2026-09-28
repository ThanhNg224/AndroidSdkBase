package io.github.thanhng224.sdkbase.otp.session

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.StateListener
import kotlinx.coroutines.flow.StateFlow

/** A running OTP flow. The host holds this, renders [state], and closes it when done. */
public interface OtpSession {

    public val state: StateFlow<OtpState>

    public suspend fun submit(code: String): SdkResult<Unit>

    public suspend fun resend(): SdkResult<Unit>

    /** For a host driving the bundled UI, which speaks in commands. */
    public fun dispatch(command: OtpCommand)

    public fun close()

    /** The Java-callable twin of [submit]: no `Continuation`, delivered on the main dispatcher. */
    public fun submit(code: String, callback: ResultCallback<Unit>): Cancellable

    /** The Java-callable twin of [resend]. */
    public fun resend(callback: ResultCallback<Unit>): Cancellable

    /**
     * The Java-callable twin of collecting [state] directly: delivers the current [OtpState], then
     * every later one, until cancelled or until this session is [close]d.
     */
    public fun observeState(listener: StateListener<OtpState>): Cancellable
}
