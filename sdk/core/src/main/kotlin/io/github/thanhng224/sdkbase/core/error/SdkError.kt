package io.github.thanhng224.sdkbase.core.error

/**
 * Every failure the SDK surfaces. The [code] is the stable contract — the [reason] is diagnostic
 * text for logs and may change without notice. Hosts branch on [code], never on [reason].
 *
 * Families: 1xxx common, 2xxx system, 3xxx business, 4xxx lifecycle.
 */
public sealed class SdkError(
    public val code: Int,
    public val reason: String,
    public val cause: Throwable? = null,
) {
    public class Common(code: Int, reason: String, cause: Throwable? = null) :
        SdkError(code, reason, cause)

    public class System(code: Int, reason: String, cause: Throwable? = null) :
        SdkError(code, reason, cause)

    public class Business(code: Int, reason: String, cause: Throwable? = null) :
        SdkError(code, reason, cause)

    public class Lifecycle(code: Int, reason: String, cause: Throwable? = null) :
        SdkError(code, reason, cause)

    override fun toString(): String = "SdkError(code=$code, reason=$reason)"
}
