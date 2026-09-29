package io.github.thanhng224.sdkbase.logging.file

import io.github.thanhng224.sdkbase.logging.file.error.FileLoggingErrors

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig
import io.github.thanhng224.sdkbase.logging.file.internal.FileLoggingRuntime
import io.github.thanhng224.sdkbase.logging.file.session.FileLoggingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

public object FileLoggingSdk {
    /** Explicit start/stop owns the writer and, only when enabled, the process crash handler. */
    @JvmStatic
    public suspend fun start(config: FileLoggingConfig, environment: SdkEnvironment): SdkResult<FileLoggingSession> {
        if (config.maxFiles !in 1..32 || config.queueCapacity !in 1..4096 ||
            config.maxRecordBytes !in 256..65_536 || config.maxFileBytes < config.maxRecordBytes ||
            config.maxFileBytes > 16_777_216 || config.retentionMillis <= 0 || config.maxCrashFiles !in 1..32 ||
            config.sdkPackagePrefixes.isEmpty() || config.sdkPackagePrefixes.any { !it.matches(Regex("[A-Za-z_][A-Za-z0-9_.]*")) }
        ) return SdkResult.Failure(FileLoggingErrors.invalid())
        var runtime: FileLoggingRuntime? = null
        return try {
            val created = withContext(environment.dispatchers.io) {
                FileLoggingRuntime.create(config, environment).also { runtime = it }
            }
            val failure = created.start()
            if (failure != null) {
                created.close()
                SdkResult.Failure(failure)
            } else {
                currentCoroutineContext().ensureActive()
                SdkResult.Success(created)
            }
        } catch (e: CancellationException) {
            runtime?.close()
            throw e
        } catch (_: java.nio.channels.OverlappingFileLockException) {
            runtime?.close()
            SdkResult.Failure(FileLoggingErrors.busy())
        } catch (_: Exception) {
            runtime?.close()
            SdkResult.Failure(FileLoggingErrors.storage())
        }
    }

    @JvmStatic
    public fun start(config: FileLoggingConfig, environment: SdkEnvironment,
        callback: ResultCallback<FileLoggingSession>): Cancellable =
        launchCallback(environment.dispatchers, callback, onUndelivered = { it.close() }) { start(config, environment) }
}
