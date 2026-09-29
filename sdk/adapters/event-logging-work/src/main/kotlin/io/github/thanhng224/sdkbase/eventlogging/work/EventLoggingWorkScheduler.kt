package io.github.thanhng224.sdkbase.eventlogging.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.common.util.concurrent.ListenableFuture
import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.delivery.EventDeliveryScheduler
import io.github.thanhng224.sdkbase.eventlogging.work.internal.EventLoggingWorker
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Schedules durable event delivery when a network connection is available.
 *
 * Each namespace has a 15-minute periodic recovery request in addition to one-time delivery. It
 * remains registered after the queue drains or a session closes so an accepted event survives a
 * process death between queue persistence and the first one-time enqueue. WorkManager may defer
 * execution for network, battery, or operating-system constraints. Call [cancel] after the host
 * disables logging for that namespace to stop both requests.
 */
public class EventLoggingWorkScheduler @JvmOverloads constructor(
    context: Context,
    private val initialBackoffMillis: Long = DEFAULT_INITIAL_BACKOFF_MILLIS,
    private val environment: SdkEnvironment = SdkEnvironment.Default,
) : EventDeliveryScheduler {
    private val appContext: Context = context.applicationContext

    init {
        require(initialBackoffMillis in MIN_BACKOFF_MILLIS..MAX_BACKOFF_MILLIS) {
            "initialBackoffMillis must be between $MIN_BACKOFF_MILLIS and $MAX_BACKOFF_MILLIS milliseconds"
        }
    }

    override suspend fun schedule(namespace: String): SdkResult<Unit> {
        if (!isSafeNamespace(namespace)) return SdkResult.Failure(
            SdkErrors.invalidConfig("namespace must be a short URL-safe opaque identifier"),
        )
        return try {
            // Calls are coalesced by the feature to durable queue transitions. KEEP limits this
            // to one active one-time request per namespace; the periodic request is the recovery
            // path if an enqueue races with a finishing worker or process death.
            val workManager = WorkManager.getInstance(appContext)
            workManager.enqueueUniquePeriodicWork(
                periodicWorkName(namespace),
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest(namespace),
            ).result.awaitCompletion()
            workManager.enqueueUniqueWork(
                uniqueWorkName(namespace),
                ExistingWorkPolicy.KEEP,
                oneTimeRequest(namespace),
            ).result.awaitCompletion()
            SdkResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            SdkResult.Failure(SdkErrors.unknown(exception))
        }
    }

    /** Java callback form of [schedule]. */
    public fun schedule(namespace: String, callback: ResultCallback<Unit>): Cancellable =
        launchCallback(environment.dispatchers, callback) { schedule(namespace) }

    /**
     * Cancels pending and periodic delivery for [namespace]. Stop the matching logging session
     * before calling this; a live session may schedule delivery again.
     */
    public suspend fun cancel(namespace: String): SdkResult<Unit> {
        if (!isSafeNamespace(namespace)) return SdkResult.Failure(
            SdkErrors.invalidConfig("namespace must be a short URL-safe opaque identifier"),
        )
        return try {
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(uniqueWorkName(namespace)).result.awaitCompletion()
            workManager.cancelUniqueWork(periodicWorkName(namespace)).result.awaitCompletion()
            SdkResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            SdkResult.Failure(SdkErrors.unknown(exception))
        }
    }

    /** Java callback form of [cancel]. */
    public fun cancel(namespace: String, callback: ResultCallback<Unit>): Cancellable =
        launchCallback(environment.dispatchers, callback) { cancel(namespace) }

    private fun periodicRequest(namespace: String): PeriodicWorkRequest =
        PeriodicWorkRequest.Builder(
            EventLoggingWorker::class.java,
            PERIODIC_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
        )
            .setInputData(workerInput(namespace))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                initialBackoffMillis,
                TimeUnit.MILLISECONDS,
            )
            .build()

    private fun oneTimeRequest(namespace: String): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(EventLoggingWorker::class.java)
            .setInputData(workerInput(namespace))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                initialBackoffMillis,
                TimeUnit.MILLISECONDS,
            )
            .build()

    internal companion object {
        const val INPUT_NAMESPACE = "io.github.thanhng224.sdkbase.eventlogging.work.namespace"
        const val MIN_BACKOFF_MILLIS = WorkRequest.MIN_BACKOFF_MILLIS
        const val MAX_BACKOFF_MILLIS = WorkRequest.MAX_BACKOFF_MILLIS
        const val DEFAULT_INITIAL_BACKOFF_MILLIS = WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS
        const val PERIODIC_INTERVAL_MILLIS = 15L * 60L * 1_000L
        private const val WORK_NAME_PREFIX = "sdkbase-event-logging-"
        private const val PERIODIC_WORK_NAME_PREFIX = "sdkbase-event-logging-periodic-"

        fun workerInput(namespace: String): Data = Data.Builder()
            .putString(INPUT_NAMESPACE, namespace)
            .build()

        fun uniqueWorkName(namespace: String): String = WORK_NAME_PREFIX + sha256(namespace)

        fun periodicWorkName(namespace: String): String = PERIODIC_WORK_NAME_PREFIX + sha256(namespace)

        fun isSafeNamespace(namespace: String): Boolean = SAFE_NAMESPACE.matches(namespace)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        private val SAFE_NAMESPACE = Regex("[A-Za-z0-9_-]{1,64}")
    }
}

private suspend fun ListenableFuture<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                get()
                continuation.resume(Unit)
            } catch (exception: ExecutionException) {
                continuation.resumeWithException(exception.cause ?: exception)
            } catch (exception: Exception) {
                continuation.resumeWithException(exception)
            }
        },
        DIRECT_EXECUTOR,
    )
}

private val DIRECT_EXECUTOR = Executor { command -> command.run() }
