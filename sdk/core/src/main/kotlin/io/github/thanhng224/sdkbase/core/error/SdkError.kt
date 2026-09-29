package io.github.thanhng224.sdkbase.core.error

/**
 * Every failure the SDK surfaces. The [code] is the stable contract — the [reason] is diagnostic
 * text for logs and may change without notice. Hosts branch on [code], never on [reason].
 *
 * Families: 1xxx common, 2xxx system, 3xxx business, 4xxx lifecycle.
 *
 * [isRetryable] says whether the same call may succeed if simply repeated (a dropped network, a
 * timeout); it is what [io.github.thanhng224.sdkbase.core.call.RetryPolicy] retries on by default.
 * The catalog that creates an error decides it — `false` unless stated.
 */
public sealed class SdkError(
    public val code: Int,
    public val reason: String,
    public val cause: Throwable?,
    public val isRetryable: Boolean,
) {
    public class Common @JvmOverloads constructor(
        code: Int,
        reason: String,
        cause: Throwable? = null,
        isRetryable: Boolean = false,
    ) : SdkError(code, reason, cause, isRetryable)

    public class System @JvmOverloads constructor(
        code: Int,
        reason: String,
        cause: Throwable? = null,
        isRetryable: Boolean = false,
    ) : SdkError(code, reason, cause, isRetryable)

    public class Business @JvmOverloads constructor(
        code: Int,
        reason: String,
        cause: Throwable? = null,
        isRetryable: Boolean = false,
    ) : SdkError(code, reason, cause, isRetryable)

    public class Lifecycle @JvmOverloads constructor(
        code: Int,
        reason: String,
        cause: Throwable? = null,
        isRetryable: Boolean = false,
    ) : SdkError(code, reason, cause, isRetryable)

    override fun toString(): String = "SdkError(code=$code, reason=$reason, retryable=$isRetryable)"
}
