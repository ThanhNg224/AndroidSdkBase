package io.github.thanhng224.sdkbase.eventlogging.internal.storage

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.error.invalidEventQueueState
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingRecord
import io.github.thanhng224.sdkbase.eventlogging.internal.redaction.EventAttributeRedactor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class QueueStatus(val events: List<EventLoggingRecord>, val byteCount: Int)

internal data class AppendStatus(val becameNonEmpty: Boolean, val eventCount: Int, val byteCount: Int)

internal class DurableEventQueue(
    private val config: EventLoggingConfig,
    private val dispatchers: DispatcherProvider,
    private val nowMillis: () -> Long,
) {
    private val directory: File = File(config.storageDirectory, "event-logging/${config.namespace}")
    private val queueFile: File = File(directory, "queue.bin")
    private val lockFile: File = File(directory, "queue.lock")
    private val drainLockFile: File = File(directory, "drain.lock")

    suspend fun load(): SdkResult<QueueStatus> = locked { readAndExpire() }

    suspend fun append(event: EventLoggingRecord): SdkResult<AppendStatus> = locked {
        val previous = readAndExpire()
        if (previous is SdkResult.Failure) return@locked previous
        val old = (previous as SdkResult.Success).value.events
        if (old.size >= config.maxEvents) return@locked SdkResult.Failure(EventLoggingErrors.queueFull())
        val encoded = encode(old + event)
        if (encoded.size > config.maxBytes) return@locked SdkResult.Failure(EventLoggingErrors.queueFull())
        writeAtomically(encoded)
        SdkResult.Success(AppendStatus(old.isEmpty(), old.size + 1, encoded.size))
    }

    suspend fun remove(id: String): SdkResult<QueueStatus> = locked {
        val loaded = readAndExpire()
        if (loaded is SdkResult.Failure) return@locked loaded
        val old = (loaded as SdkResult.Success).value.events
        val next = old.filterNot { it.id == id }
        if (next.size != old.size) writeAtomically(encode(next))
        SdkResult.Success(QueueStatus(next, fileSize()))
    }

    suspend fun acquireDrainLease(): DrainLease? {
        var acquired: DrainLease? = null
        return try {
            withContext(dispatchers.io) {
                ensureDirectory()
                val channel = RandomAccessFile(drainLockFile, "rw").channel
                try {
                    val lock = try {
                        channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    }
                    if (lock == null) {
                        channel.close()
                        null
                    } else {
                        DrainLease(channel, lock).also { acquired = it }
                    }
                } catch (e: Exception) {
                    channel.close()
                    throw e
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + dispatchers.io) { acquired?.close() }
            throw e
        }
    }

    suspend fun releaseDrainLease(lease: DrainLease) = withContext(NonCancellable + dispatchers.io) { lease.close() }

    inner class DrainLease internal constructor(
        private val channel: java.nio.channels.FileChannel,
        private val lock: FileLock,
    ) : AutoCloseable {
        override fun close() {
            try {
                lock.release()
            } finally {
                channel.close()
            }
        }
    }

    private suspend fun <T> locked(block: () -> SdkResult<T>): SdkResult<T> = try {
        withContext(dispatchers.io) {
            ensureDirectory()
            RandomAccessFile(lockFile, "rw").use { raf ->
                var lock: FileLock? = null
                while (lock == null) {
                    lock = try {
                        raf.channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    }
                    if (lock == null) delay(10L)
                }
                lock.use { block() }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SdkResult.Failure(EventLoggingErrors.storageFailure(e))
    }

    private fun readAndExpire(): SdkResult<QueueStatus> {
        return try {
            if (!queueFile.exists()) return SdkResult.Success(QueueStatus(emptyList(), 0))
            val length = queueFile.length()
            if (length > config.maxBytes || length > HARD_MAX_FILE_BYTES) {
                return SdkResult.Failure(invalidEventQueueState(IOException("queue file exceeds configured bounds")))
            }
            val content = FileInputStream(queueFile).use { input -> input.readBytes() }
            val events = decode(content)
            val retentionCutoff = (nowMillis() - config.retentionMillis).coerceAtLeast(0L)
            val retained = events.filter { it.createdAtMillis >= retentionCutoff }
            val canonical = encode(retained)
            if (!content.contentEquals(canonical)) writeAtomically(canonical)
            SdkResult.Success(QueueStatus(retained, canonical.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidQueueFileException) {
            SdkResult.Failure(invalidEventQueueState(e))
        } catch (e: Exception) {
            SdkResult.Failure(EventLoggingErrors.storageFailure(e))
        }
    }

    private fun decode(content: ByteArray): List<EventLoggingRecord> {
        try {
            if (content.isEmpty()) throw InvalidQueueFileException("empty queue file")
            DataInputStream(ByteArrayInputStream(content)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                    throw InvalidQueueFileException("unsupported queue format")
                }
                val count = input.readInt()
                if (count !in 0..config.maxEvents) throw InvalidQueueFileException("invalid event count")
                val events = ArrayList<EventLoggingRecord>(count)
                val ids = HashSet<String>(count)
                repeat(count) {
                    val id = input.readUTF()
                    val sessionId = input.readUTF()
                    val timestamp = input.readLong()
                    val elapsed = input.readLong()
                    val action = input.readUTF()
                    val level = input.readUTF()
                    val screen = if (input.readBoolean()) input.readUTF() else null
                    val attributeCount = input.readInt()
                    if (attributeCount !in 0..config.maxAttributes) {
                        throw InvalidQueueFileException("invalid attribute count")
                    }
                    val attributes = LinkedHashMap<String, String>(attributeCount)
                    val seenKeys = HashSet<String>(attributeCount)
                    repeat(attributeCount) {
                        val key = input.readUTF()
                        val value = input.readUTF()
                        if (key.isBlank() || key.length > MAX_KEY_LENGTH || !seenKeys.add(key)) {
                            throw InvalidQueueFileException("invalid event attributes")
                        }
                        if (key in config.allowedAttributeKeys && attributes.size < config.maxAttributes) {
                            attributes[key] = EventAttributeRedactor.redact(
                                key,
                                value.take(config.maxValueLength),
                                EventAttributeRedactor.default,
                            ).take(config.maxValueLength)
                        }
                    }
                    if (id.isBlank() || sessionId.isBlank() || action.isBlank() || timestamp < 0L || elapsed < 0L) {
                        throw InvalidQueueFileException("invalid event record")
                    }
                    if (action.length > MAX_ACTION_LENGTH || screen?.length?.let { it > MAX_SCREEN_LENGTH } == true ||
                        !ids.add(id)
                    ) {
                        throw InvalidQueueFileException("invalid event fields")
                    }
                    if (id.length > MAX_IDENTIFIER_LENGTH || sessionId.length > MAX_IDENTIFIER_LENGTH ||
                        level !in ALLOWED_LEVELS
                    ) {
                        throw InvalidQueueFileException("invalid event identity")
                    }
                    val safeAction = redact(action.take(config.maxActionLength)).take(config.maxActionLength)
                    val safeScreen = screen?.let { redact(it.take(MAX_SCREEN_LENGTH)).take(MAX_SCREEN_LENGTH) }
                    events += EventLoggingRecord(
                        id, sessionId, timestamp, elapsed, safeAction, level, safeScreen, attributes,
                    )
                }
                if (input.available() != 0) throw InvalidQueueFileException("trailing queue bytes")
                return events
            }
        } catch (e: InvalidQueueFileException) {
            throw e
        } catch (e: IOException) {
            throw InvalidQueueFileException("queue file is truncated or malformed", e)
        }
    }

    private fun encode(events: List<EventLoggingRecord>): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(events.size)
            for (event in events) {
                output.writeUTF(event.id)
                output.writeUTF(event.sessionId)
                output.writeLong(event.createdAtMillis)
                output.writeLong(event.elapsedSinceSessionStartMillis)
                output.writeUTF(event.action)
                output.writeUTF(event.level)
                output.writeBoolean(event.screenId != null)
                event.screenId?.let(output::writeUTF)
                output.writeInt(event.attributes.size)
                for ((key, value) in event.attributes) {
                    output.writeUTF(key)
                    output.writeUTF(value)
                }
            }
        }
        bytes.toByteArray()
    }

    private fun writeAtomically(bytes: ByteArray) {
        if (bytes.size > config.maxBytes || bytes.size > HARD_MAX_FILE_BYTES) {
            throw InvalidQueueFileException("queue exceeds configured bounds")
        }
        ensureDirectory()
        val temporary = File(directory, "queue.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (!temporary.renameTo(queueFile)) {
            temporary.delete()
            throw IOException("could not atomically replace queue")
        }
    }

    private fun redact(value: String): String = EventAttributeRedactor.default.redact(value)

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("could not create event queue directory")
        }
        if (!directory.isDirectory) throw IOException("event queue path is not a directory")
    }

    private fun fileSize(): Int =
        if (queueFile.exists()) queueFile.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0

    private companion object {
        private const val MAGIC: Int = 0x45564C47
        private const val FORMAT_VERSION: Int = 2
        private const val HARD_MAX_FILE_BYTES: Int = 16 * 1_048_576
        private const val MAX_KEY_LENGTH: Int = 64
        private const val MAX_SCREEN_LENGTH: Int = 256
        private const val MAX_ACTION_LENGTH: Int = 512
        private const val MAX_IDENTIFIER_LENGTH: Int = 128
        private val ALLOWED_LEVELS = setOf("debug", "info", "warn", "error")
    }
}

private class InvalidQueueFileException(message: String, cause: Throwable? = null) : IOException(message, cause)
