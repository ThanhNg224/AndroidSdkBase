package io.github.thanhng224.sdkbase.logging.file.internal

import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig
import io.github.thanhng224.sdkbase.logging.file.crash.CrashReport
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class FileStorage(private val config: FileLoggingConfig) {
    @Synchronized
    fun prepare(now: Long) {
        val pending = File(config.directory, "crash.pending")
        if (pending.exists()) {
            if (pending.length() in 1..config.maxRecordBytes.toLong()) {
                check(pending.renameTo(File(config.directory, "crash-${UUID.randomUUID()}.txt")))
            } else {
                check(pending.delete())
            }
        }
        prune(now)
    }

    @Synchronized
    fun append(bytes: ByteArray, now: Long) {
        prune(now)
        val active = File(config.directory, "active.log")
        if (active.length() + bytes.size > config.maxFileBytes) {
            File(config.directory, "${config.maxFiles - 1}.log").delete()
            for (i in config.maxFiles - 2 downTo 1) {
                val source = File(config.directory, "$i.log")
                if (source.exists()) check(source.renameTo(File(config.directory, "${i + 1}.log")))
            }
            if (config.maxFiles > 1 && active.exists()) {
                check(active.renameTo(File(config.directory, "1.log")))
            } else if (active.exists()) {
                check(active.delete())
            }
        }
        FileOutputStream(active, true).use {
            it.write(bytes)
            it.fd.sync()
        }
    }

    @Synchronized
    fun saveCrash(bytes: ByteArray, now: Long) {
        prune(now)
        val existing = crashFiles()
        existing.take((existing.size - config.maxCrashFiles + 1).coerceAtLeast(0)).forEach { check(it.delete()) }
        val file = File(config.directory, "crash-${UUID.randomUUID()}.txt")
        val pending = File(config.directory, "crash.pending")
        FileOutputStream(pending).use {
            it.write(bytes)
            it.fd.sync()
        }
        check(pending.renameTo(file))
    }

    @Synchronized
    fun crashes(now: Long): List<CrashReport> {
        prune(now)
        return crashFiles().map { file ->
            check(file.length() <= config.maxRecordBytes)
            CrashReport(file.name, file.readText(Charsets.UTF_8))
        }
    }

    @Synchronized
    fun acknowledge(id: String) {
        require(id.matches(Regex("crash-[a-f0-9-]{36}\\.txt")))
        val file = File(config.directory, id)
        if (file.exists()) check(file.delete())
    }

    private fun crashFiles(): List<File> = config.directory.listFiles()?.filter {
        it.isFile && it.name.matches(Regex("crash-[a-f0-9-]{36}\\.txt"))
    }?.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name }) ?: emptyList()

    private fun prune(now: Long) {
        config.directory.listFiles()?.filter {
            it.name == "active.log" || it.name.matches(Regex("[0-9]+\\.log")) ||
                it.name.matches(Regex("crash-[a-f0-9-]{36}\\.txt")) || it.name == "crash.pending"
        }?.forEach {
            if (!it.isFile) return@forEach
            val index = it.name.removeSuffix(".log").toIntOrNull()
            val oversized = it.length() > if (it.name.endsWith(".log")) config.maxFileBytes else config.maxRecordBytes
            if (it.lastModified() < now - config.retentionMillis || oversized ||
                (index != null && index >= config.maxFiles)
            ) {
                check(it.delete())
            }
        }
        val crashes = crashFiles()
        crashes.take((crashes.size - config.maxCrashFiles).coerceAtLeast(0)).forEach { check(it.delete()) }
    }
}

/** Bound memory before redaction, then UTF-8 bytes after redaction; never split a codepoint. */
internal fun boundedBytes(text: String, limit: Int): ByteArray {
    val cut = text.take(limit)
    val bounded = if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    var end = bounded.length
    var bytes = bounded.toByteArray(Charsets.UTF_8)
    while (bytes.size > limit) {
        end -= (bytes.size - limit).coerceAtLeast(1).coerceAtMost(end)
        if (end > 0 && bounded[end - 1].isHighSurrogate()) end--
        bytes = bounded.substring(0, end).toByteArray(Charsets.UTF_8)
    }
    return bytes
}
