package io.github.thanhng224.sdkbase.core.logging

/**
 * Transforms a raw message into one safe to hand to a [LogSink]. [SdkLogger] runs this exactly
 * once per record, before any sink sees it.
 */
public fun interface Redactor {

    public fun redact(message: String): String

    public companion object {
        /** Passes the message through unchanged. Only for tests or a host that redacts itself. */
        public val None: Redactor = Redactor { message -> message }
    }
}
