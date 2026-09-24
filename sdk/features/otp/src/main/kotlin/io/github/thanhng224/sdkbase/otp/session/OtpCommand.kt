package io.github.thanhng224.sdkbase.otp.session

/** Everything a host or the bundled UI can ask the engine to do. */
public sealed interface OtpCommand {
    public data class AppendDigit(public val digit: Char) : OtpCommand
    public data object DeleteDigit : OtpCommand
    public data object Submit : OtpCommand
    public data object Resend : OtpCommand
    public data object Cancel : OtpCommand
}
