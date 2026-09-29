package io.github.thanhng224.sdkbase.eventlogging.work.provider

import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingCallbackGateway
import io.github.thanhng224.sdkbase.eventlogging.gateway.asGateway
import io.github.thanhng224.sdkbase.eventlogging.work.EventLoggingWorkScheduler

/**
 * Implement on the host application's [android.app.Application] so WorkManager can restore the
 * host-owned event logging dependencies after Android recreates the process.
 *
 * [namespace] is the opaque storage key supplied to [EventLoggingWorkScheduler]. Do not use a
 * customer identifier, session ID, URL, or credential as a namespace.
 *
 * Keep resolution local and bounded: reconstruct configuration and gateway objects here, and leave
 * all network calls to [EventLoggingGateway] when the worker drains the queue.
 */
public fun interface EventLoggingWorkProvider {
    /** Returns the configuration and host gateway for [namespace], or null if it is unknown. */
    public fun resolve(namespace: String): EventLoggingWorkConfiguration?
}

/** The host-owned inputs required to reopen a durable event logging queue. */
public class EventLoggingWorkConfiguration(
    public val config: EventLoggingConfig,
    public val gateway: EventLoggingGateway,
    public val environment: SdkEnvironment,
) {
    /** Java-friendly inputs for a callback transport. */
    public constructor(
        config: EventLoggingConfig,
        gateway: EventLoggingCallbackGateway,
        environment: SdkEnvironment,
    ) : this(config, gateway.asGateway(), environment)
}
