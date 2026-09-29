package io.github.thanhng224.consumer.logging

import android.app.Application
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.work.provider.EventLoggingWorkConfiguration
import io.github.thanhng224.sdkbase.eventlogging.work.provider.EventLoggingWorkProvider
import io.github.thanhng224.sdkbase.eventlogging.work.EventLoggingWorkScheduler

/** Resolvable from a fresh Application, without a process-local SDK singleton or payload in Work Data. */
class LoggingApplication : Application(), EventLoggingWorkProvider {
    override fun resolve(namespace: String): EventLoggingWorkConfiguration? {
        if (namespace != "consumer-logging") return null
        val config = EventLoggingConfig.Builder(namespace, noBackupFilesDir)
            .allowedAttributeKeys(setOf("sdk_version", "outcome"))
            .commonAttributes(mapOf("sdk_version" to "consumer"))
            .scheduler(EventLoggingWorkScheduler(this))
            .build().getOrNull() ?: return null
        val gateway = EventLoggingGateway { SdkResult.Success(Unit) }
        return EventLoggingWorkConfiguration(config, gateway, SdkEnvironment.Builder().build())
    }
}
