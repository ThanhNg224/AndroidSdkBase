package io.github.thanhng224.sdkbase.logging.file.config

import io.github.thanhng224.sdkbase.core.config.validateConfig
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.core.logging.Redactor
import io.github.thanhng224.sdkbase.core.result.SdkResult
import java.io.File
import java.util.Collections

/**
 * Storage, rotation and crash-capture limits plus the [environment] for one file logger. Validated
 * once, in [Builder.build]. Use a dedicated app-private directory (prefer Context.noBackupFilesDir).
 */
public class FileLoggingConfig private constructor(
    public val directory: File,
    public val maxFileBytes: Int,
    public val maxFiles: Int,
    public val queueCapacity: Int,
    public val maxRecordBytes: Int,
    public val retentionMillis: Long,
    public val captureCrashes: Boolean,
    public val sdkPackagePrefixes: Set<String>,
    public val maxCrashFiles: Int,
    public val redactor: Redactor,
    public val environment: SdkEnvironment,
) {
    public class Builder(private val directory: File) {
        private var maxFileBytes = 262_144
        private var maxFiles = 4
        private var queueCapacity = 256
        private var maxRecordBytes = 16_384
        private var retentionMillis = 7 * 24 * 60 * 60 * 1000L
        private var captureCrashes = false
        private var sdkPackagePrefixes: Set<String> = setOf("io.github.thanhng224.sdkbase")
        private var maxCrashFiles = 4
        private var redactor: Redactor = DefaultRedactor()
        private var environment: SdkEnvironment = SdkEnvironment.Default

        public fun maxFileBytes(value: Int): Builder = apply { maxFileBytes = value }

        public fun maxFiles(value: Int): Builder = apply { maxFiles = value }

        public fun queueCapacity(value: Int): Builder = apply { queueCapacity = value }

        public fun maxRecordBytes(value: Int): Builder = apply { maxRecordBytes = value }

        public fun retentionMillis(value: Long): Builder = apply { retentionMillis = value }

        public fun captureCrashes(value: Boolean): Builder = apply { captureCrashes = value }

        public fun sdkPackagePrefixes(value: Set<String>): Builder = apply {
            sdkPackagePrefixes = Collections.unmodifiableSet(value.toSet())
        }

        public fun maxCrashFiles(value: Int): Builder = apply { maxCrashFiles = value }

        public fun redactor(value: Redactor): Builder = apply { redactor = value }

        public fun environment(value: SdkEnvironment): Builder = apply { environment = value }

        public fun build(): SdkResult<FileLoggingConfig> = validateConfig {
            ensure(maxFiles in 1..MAX_FILES) { "maxFiles must be in 1..$MAX_FILES" }
            ensure(queueCapacity in 1..MAX_QUEUE_CAPACITY) { "queueCapacity must be in 1..$MAX_QUEUE_CAPACITY" }
            ensure(maxRecordBytes in MIN_RECORD_BYTES..MAX_RECORD_BYTES) {
                "maxRecordBytes must be in $MIN_RECORD_BYTES..$MAX_RECORD_BYTES"
            }
            ensure(maxFileBytes in maxRecordBytes..MAX_FILE_BYTES) {
                "maxFileBytes must be in maxRecordBytes..$MAX_FILE_BYTES"
            }
            ensure(retentionMillis > 0L) { "retentionMillis must be positive" }
            ensure(maxCrashFiles in 1..MAX_FILES) { "maxCrashFiles must be in 1..$MAX_FILES" }
            ensure(sdkPackagePrefixes.isNotEmpty()) { "sdkPackagePrefixes must not be empty" }
            ensure(sdkPackagePrefixes.all { PACKAGE_PATTERN.matches(it) }) {
                "sdkPackagePrefixes must be Java package prefixes"
            }
            FileLoggingConfig(
                directory, maxFileBytes, maxFiles, queueCapacity, maxRecordBytes, retentionMillis,
                captureCrashes, sdkPackagePrefixes, maxCrashFiles, redactor, environment,
            )
        }

        private companion object {
            private const val MAX_FILES = 32
            private const val MAX_QUEUE_CAPACITY = 4096
            private const val MIN_RECORD_BYTES = 256
            private const val MAX_RECORD_BYTES = 65_536
            private const val MAX_FILE_BYTES = 16_777_216
            private val PACKAGE_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_.]*")
        }
    }
}
