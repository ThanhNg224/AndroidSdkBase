package io.github.thanhng224.sdkbase.core.logging

/**
 * Transforms one raw text field into a safe value to hand to a [LogSink]. [SdkLogger] applies this
 * to the log message and to text fields in a detached Throwable snapshot before any sink sees them.
 */
public fun interface Redactor {

    public fun redact(message: String): String

    public companion object {
        /** Passes the message through unchanged. Only for tests or a host that redacts itself. */
        public val None: Redactor = Redactor { message -> message }
    }
}
