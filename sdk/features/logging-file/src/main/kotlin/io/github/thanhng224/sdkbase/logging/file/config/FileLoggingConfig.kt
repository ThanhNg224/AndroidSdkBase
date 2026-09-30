package io.github.thanhng224.sdkbase.logging.file.config

import io.github.thanhng224.sdkbase.core.logging.DefaultRedactor
import io.github.thanhng224.sdkbase.core.logging.Redactor
import java.io.File
import java.util.Collections

/** Use a dedicated app-private directory (prefer Context.noBackupFilesDir). */
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
        public fun maxFileBytes(value: Int): Builder = apply { maxFileBytes = value }
        public fun maxFiles(value: Int): Builder = apply { maxFiles = value }
        public fun queueCapacity(value: Int): Builder = apply { queueCapacity = value }
        public fun maxRecordBytes(value: Int): Builder = apply { maxRecordBytes = value }
        public fun retentionMillis(value: Long): Builder = apply { retentionMillis = value }
        public fun captureCrashes(value: Boolean): Builder = apply { captureCrashes = value }
        public fun sdkPackagePrefixes(value: Set<String>): Builder = apply {
            sdkPackagePrefixes =
                Collections.unmodifiableSet(value.toSet())
        }
        public fun maxCrashFiles(value: Int): Builder = apply { maxCrashFiles = value }
        public fun redactor(value: Redactor): Builder = apply { redactor = value }
        public fun build(): FileLoggingConfig = FileLoggingConfig(
            directory, maxFileBytes, maxFiles,
            queueCapacity, maxRecordBytes, retentionMillis, captureCrashes, sdkPackagePrefixes,
            maxCrashFiles, redactor,
        )
    }
}
