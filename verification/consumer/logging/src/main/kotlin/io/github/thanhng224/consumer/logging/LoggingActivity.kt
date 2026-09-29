package io.github.thanhng224.consumer.logging

import android.app.Activity
import android.os.Bundle
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.eventlogging.EventLoggingSdk
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingSession

class LoggingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JavaLoggingConsumer.startFiles(noBackupFilesDir)
        val dependencies = (application as LoggingApplication).resolve("consumer-logging") ?: return
        EventLoggingSdk.start(dependencies.config, dependencies.gateway, dependencies.environment,
            object : ResultCallback<EventLoggingSession> {
                override fun onSuccess(value: EventLoggingSession) {
                    JavaLoggingConsumer.track(value, EventLoggingEvent.Builder("consumer_started")
                        .attributes(mapOf("outcome" to "success", "token" to "must-be-filtered")).build())
                }
                override fun onFailure(error: SdkError) { }
            })
        JavaLoggingConsumer.startEvents(noBackupFilesDir)
    }
}
