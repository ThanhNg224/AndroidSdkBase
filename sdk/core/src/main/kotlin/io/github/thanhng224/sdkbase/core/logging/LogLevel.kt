package io.github.thanhng224.sdkbase.core.logging

/**
 * Severity of a [LogRecord], from most to least verbose. Ordinal order is the comparison order:
 * [SdkLogger.isLoggable] treats a level as loggable when it is at or above the logger's
 * `minLevel`.
 */
public enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }
