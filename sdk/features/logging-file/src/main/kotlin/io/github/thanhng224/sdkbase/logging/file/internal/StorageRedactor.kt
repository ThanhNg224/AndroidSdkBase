package io.github.thanhng224.sdkbase.logging.file.internal

import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig

private val baselineRedactor = DefaultRedactor()

/** Host-specific scrubbing supplements the mandatory baseline before any local persistence. */
internal fun redactForStorage(config: FileLoggingConfig, text: String): String =
    baselineRedactor.redact(config.redactor.redact(text))
