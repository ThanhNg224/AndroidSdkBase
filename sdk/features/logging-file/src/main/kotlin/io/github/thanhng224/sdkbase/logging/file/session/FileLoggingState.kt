package io.github.thanhng224.sdkbase.logging.file.session

/** Counts only; never exposes log text or crash payloads. Counters are per open session. */
public class FileLoggingState(
    public val written: Long,
    public val dropped: Long,
    public val storageFailures: Long,
)
