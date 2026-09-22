package io.github.thanhng224.sdkbase.core

/**
 * Technical logging contract. `:core` owns the contract and the redaction policy;
 * `:platform` owns the Logcat sink. The SDK must never log a secret, a token, or a full
 * user identifier — route anything sensitive through [redact] first.
 */
public interface SdkLogger {

    public fun debug(tag: String, message: String)

    public fun info(tag: String, message: String)

    public fun error(tag: String, message: String, throwable: Throwable? = null)

    public companion object {
        public val NoOp: SdkLogger = object : SdkLogger {
            override fun debug(tag: String, message: String): Unit = Unit
            override fun info(tag: String, message: String): Unit = Unit
            override fun error(tag: String, message: String, throwable: Throwable?): Unit = Unit
        }
    }
}

/**
 * Masks all but the last [keepLast] characters. Never reveals more characters than the input has,
 * and never reveals anything when [keepLast] is zero or negative.
 */
public fun redact(value: String, keepLast: Int = 2): String {
    if (value.isEmpty()) return value
    val keep = keepLast.coerceIn(0, value.length)
    if (keep == value.length) return value
    return "*".repeat(value.length - keep) + value.takeLast(keep)
}
