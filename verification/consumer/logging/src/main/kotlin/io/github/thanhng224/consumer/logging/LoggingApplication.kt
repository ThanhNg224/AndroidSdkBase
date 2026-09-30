package io.github.thanhng224.consumer.logging

import android.app.Application
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.work.EventLoggingWorkScheduler
import io.github.thanhng224.sdkbase.eventlogging.work.provider.EventLoggingWorkProvider

/** Resolvable from a fresh Application, without a process-local SDK singleton or payload in Work Data. */
class LoggingApplication : Application(), EventLoggingWorkProvider {
    override fun resolve(namespace: String): EventLoggingConfig? {
        if (namespace != "consumer-logging") return null
        val gateway = EventLoggingGateway { SdkResult.Success(Unit) }
        return EventLoggingConfig.Builder(namespace, noBackupFilesDir, gateway)
            .allowedAttributeKeys(setOf("sdk_version", "outcome"))
            .commonAttributes(mapOf("sdk_version" to "consumer"))
            .scheduler(EventLoggingWorkScheduler(this))
            .build().getOrNull()
    }
}
