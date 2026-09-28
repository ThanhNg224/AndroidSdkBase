package io.github.thanhng224.sdkbase.otp.session

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SdkSession

/**
 * A running OTP flow. The host holds this, renders [state], and closes it when done. `state`,
 * `observeState` and `close` come from [SdkSession] — every feature session extends it instead of
 * redeclaring the three members every session needs.
 */
public interface OtpSession : SdkSession<OtpState> {

    public suspend fun submit(code: String): SdkResult<Unit>

    public suspend fun resend(): SdkResult<Unit>

    /** For a host driving the bundled UI, which speaks in commands. */
    public fun dispatch(command: OtpCommand)

    /** The Java-callable twin of [submit]: no `Continuation`, delivered on the main dispatcher. */
    public fun submit(code: String, callback: ResultCallback<Unit>): Cancellable

    /** The Java-callable twin of [resend]. */
    public fun resend(callback: ResultCallback<Unit>): Cancellable
}
