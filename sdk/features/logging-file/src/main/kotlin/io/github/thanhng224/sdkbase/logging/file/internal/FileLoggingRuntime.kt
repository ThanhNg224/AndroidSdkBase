package io.github.thanhng224.sdkbase.logging.file.internal

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.logging.LogRecord
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.core.session.SdkSessionBase
import io.github.thanhng224.sdkbase.core.session.StateStore
import io.github.thanhng224.sdkbase.logging.file.error.FileLoggingErrors
import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig
import io.github.thanhng224.sdkbase.logging.file.crash.CrashReport
import io.github.thanhng224.sdkbase.logging.file.session.FileLoggingSession
import io.github.thanhng224.sdkbase.logging.file.session.FileLoggingState
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

internal class FileLoggingRuntime private constructor(
    private val config: FileLoggingConfig,
    private val environment: SdkEnvironment,
    scope: SessionScope,
    private val states: StateStore<FileLoggingState>,
    private val leaseFile: RandomAccessFile,
    private val lease: FileLock,
) : SdkSessionBase<FileLoggingState>(scope, states), FileLoggingSession {
    private sealed interface Write {
        class Record(val bytes: ByteArray) : Write
        class Fence(val result: CompletableDeferred<SdkResult<Unit>>) : Write
    }
    private val channel = Channel<Write>(config.queueCapacity)
    private val dropped = AtomicLong()
    private val storage = FileStorage(config)
    private val crash = CrashCapture(config, storage) { environment.clock.nowMillis() }
    private var started = false
    private var released = false

    fun start(): SdkError? {
        if (config.captureCrashes && !crash.install()) return SdkError.Business(
            FileLoggingErrors.CRASH_HANDLER_UNAVAILABLE, "SDK crash capture handler unavailable")
        started = true
        val writer = scope.coroutineScope.launch {
            try {
                withContext(environment.dispatchers.io) {
                    for (write in channel) {
                        when (write) {
                            is Write.Record -> {
                                var failed = false
                                try { storage.append(write.bytes, environment.clock.nowMillis()) }
                                catch (_: Exception) { failed = true }
                                states.withLock { update {
                                    FileLoggingState(it.written + if (failed) 0 else 1, dropped.get(),
                                        it.storageFailures + if (failed) 1 else 0)
                                } }
                            }
                            is Write.Fence -> {
                                states.withLock { update { FileLoggingState(it.written, dropped.get(), it.storageFailures) } }
                                write.result.complete(if (states.current.storageFailures == 0L) SdkResult.Success(Unit)
                                    else SdkResult.Failure(FileLoggingErrors.storage()))
                            }
                        }
                    }
                }
            } finally { releaseLease() }
        }
        writer.invokeOnCompletion { releaseLease() }
        return null
    }

    override fun write(record: LogRecord) {
        if (scope.isClosed) return
        val bytes = try {
            // Re-redact because hosts can also invoke LogSink.write directly. No raw exception identity retained.
            val text = buildString {
                append(record.timestampMillis).append(' ').append(record.level).append(' ')
                append(record.tag.take(128)).append(' ').append(record.message.take(config.maxRecordBytes))
                record.throwable?.let {
                    append(" exception=").append(renderThrowable(it, config.maxRecordBytes, emptySet()).text)
                }
                record.sessionId?.let { append(" session=").append(it.take(128)) }
            }
            boundedBytes(redactForStorage(config, text), config.maxRecordBytes - 1) + byteArrayOf(10)
        } catch (_: Exception) { dropped.incrementAndGet(); return }
        if (!channel.trySend(Write.Record(bytes)).isSuccess) dropped.incrementAndGet()
    }

    override suspend fun flush(): SdkResult<Unit> = scope.ifOpen {
        val fence = CompletableDeferred<SdkResult<Unit>>()
        channel.send(Write.Fence(fence))
        fence.await()
    }
    override fun flush(callback: ResultCallback<Unit>): Cancellable = scope.call(callback) { flush() }
    override suspend fun pendingCrashes(): SdkResult<List<CrashReport>> = scope.ifOpen {
        withContext(environment.dispatchers.io) {
            try { SdkResult.Success(storage.crashes(environment.clock.nowMillis())) }
            catch (_: Exception) { SdkResult.Failure(FileLoggingErrors.storage()) }
        }
    }
    override fun pendingCrashes(callback: ResultCallback<List<CrashReport>>): Cancellable = scope.call(callback) { pendingCrashes() }
    override suspend fun acknowledgeCrash(id: String): SdkResult<Unit> = scope.ifOpen {
        withContext(environment.dispatchers.io) {
            try { storage.acknowledge(id); SdkResult.Success(Unit) }
            catch (_: IllegalArgumentException) { SdkResult.Failure(SdkError.Business(FileLoggingErrors.INVALID_CRASH_ID, "Invalid crash ID")) }
            catch (_: Exception) { SdkResult.Failure(FileLoggingErrors.storage()) }
        }
    }
    override fun acknowledgeCrash(id: String, callback: ResultCallback<Unit>): Cancellable = scope.call(callback) { acknowledgeCrash(id) }

    override fun onClose() {
        crash.uninstall()
        channel.cancel()
        if (!started) releaseLease()
    }
    @Synchronized
    private fun releaseLease() {
        if (released) return
        released = true
        crash.uninstall()
        try { if (lease.isValid) lease.release() } finally { leaseFile.close() }
    }

    companion object {
        fun create(config: FileLoggingConfig, environment: SdkEnvironment): FileLoggingRuntime {
            check(config.directory.isDirectory || config.directory.mkdirs())
            val file = RandomAccessFile(File(config.directory, "writer.lock"), "rw")
            try {
                val lock = file.channel.tryLock() ?: throw java.nio.channels.OverlappingFileLockException()
                val scope = SessionScope(environment.dispatchers, environment.logger.tagged("FileLogging"))
                val runtime = FileLoggingRuntime(config, environment, scope, StateStore(FileLoggingState(0, 0, 0)), file, lock)
                try { runtime.storage.prepare(environment.clock.nowMillis()) }
                catch (e: Exception) { runtime.close(); throw e }
                return runtime
            } catch (e: Exception) { file.close(); throw e }
        }
    }
}
