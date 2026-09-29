package io.github.thanhng224.sdkbase.core.error

/**
 * How a UI should present an [SdkError]. It is presentation metadata, independent of
 * [SdkError.isRetryable]: `isRetryable` says whether repeating the same call may succeed on its own
 * (what [io.github.thanhng224.sdkbase.core.call.RetryPolicy] acts on); a disposition says what the
 * user is shown. A wrong OTP code is not retryable by the SDK but is [INLINE_RETRY] for the user.
 *
 * The catalog that creates an error may choose one; otherwise it is derived: lifecycle errors are
 * [SILENT], a retryable error is [DIALOG_RETRY], anything else is [DIALOG_TERMINAL].
 */
public enum class Disposition {
    /** Show the failure in place; the user can correct the input and try again. */
    INLINE_RETRY,

    /** Show a dialog and let the user retry. */
    DIALOG_RETRY,

    /** Show a dialog and end the flow. */
    DIALOG_TERMINAL,

    /** Show nothing: the failure is for the host or the logs, not the end user. */
    SILENT,
}

internal fun defaultDisposition(isRetryable: Boolean, lifecycle: Boolean): Disposition = when {
    lifecycle -> Disposition.SILENT
    isRetryable -> Disposition.DIALOG_RETRY
    else -> Disposition.DIALOG_TERMINAL
}
