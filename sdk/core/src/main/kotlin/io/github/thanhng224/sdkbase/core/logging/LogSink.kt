package io.github.thanhng224.sdkbase.core.logging

/**
 * A destination for [LogRecord]s: Logcat, a file, a remote log collector. Host-supplied and never
 * trusted: [SdkLogger] wraps every call so a throwing sink is contained and never stops the
 * remaining sinks from receiving the record.
 */
public fun interface LogSink {
    public fun write(record: LogRecord)
}
