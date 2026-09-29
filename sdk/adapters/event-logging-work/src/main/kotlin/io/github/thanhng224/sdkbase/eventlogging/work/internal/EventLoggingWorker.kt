package io.github.thanhng224.sdkbase.eventlogging.work.internal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.github.thanhng224.sdkbase.core.call.safeCall
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.eventlogging.EventLoggingSdk
import io.github.thanhng224.sdkbase.eventlogging.work.EventLoggingWorkScheduler
import io.github.thanhng224.sdkbase.eventlogging.work.provider.EventLoggingWorkProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reopens and drains one durable event queue after WorkManager starts or restores the app process. */
internal class EventLoggingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): ListenableWorker.Result {
        val namespace = inputData.getString(EventLoggingWorkScheduler.INPUT_NAMESPACE)
            ?.takeIf(EventLoggingWorkScheduler::isSafeNamespace)
            ?: return ListenableWorker.Result.failure()

        val provider = applicationContext as? EventLoggingWorkProvider
            ?: return ListenableWorker.Result.failure()

        val dependencies = when (
            val resolved = safeCall(
                operation = "restore event logging configuration",
                timeoutMillis = PROVIDER_TIMEOUT_MILLIS,
            ) {
                val configuration = withContext(Dispatchers.IO) { provider.resolve(namespace) }
                if (configuration == null) {
                    SdkResult.Failure(SdkErrors.invalidConfig("No event logging configuration for this queue"))
                } else {
                    SdkResult.Success(configuration)
                }
            }
        ) {
            is SdkResult.Success -> resolved.value
            is SdkResult.Failure -> return if (resolved.error.isRetryable) {
                ListenableWorker.Result.retry()
            } else ListenableWorker.Result.failure()
        }

        // Never let an incorrect host mapping drain another namespace's persisted events.
        if (dependencies.config.namespace != namespace) return ListenableWorker.Result.failure()

        val delivery = EventLoggingSdk.deliverPending(
            dependencies.config,
            dependencies.gateway,
            dependencies.environment,
        )
        return when (deliveryDecision(delivery)) {
            WorkDeliveryDecision.SUCCESS -> ListenableWorker.Result.success()
            WorkDeliveryDecision.RETRY -> ListenableWorker.Result.retry()
            WorkDeliveryDecision.FAILURE -> ListenableWorker.Result.failure()
        }
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MILLIS = 10_000L
    }
}
