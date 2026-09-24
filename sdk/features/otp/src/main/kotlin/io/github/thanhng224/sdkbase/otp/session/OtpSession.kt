package io.github.thanhng224.sdkbase.otp.session

import io.github.thanhng224.sdkbase.core.result.SdkResult
import kotlinx.coroutines.flow.StateFlow

/** A running OTP flow. The host holds this, renders [state], and closes it when done. */
public interface OtpSession {

    public val state: StateFlow<OtpState>

    public suspend fun submit(code: String): SdkResult<Unit>

    public suspend fun resend(): SdkResult<Unit>

    /** For a host driving the bundled UI, which speaks in commands. */
    public fun dispatch(command: OtpCommand)

    public fun close()
}
