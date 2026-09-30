package io.github.thanhng224.sdkbase.eventlogging.work.provider

import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.work.EventLoggingWorkScheduler

/**
 * Implement on the host application's [android.app.Application] so WorkManager can restore the
 * host-owned event logging dependencies after Android recreates the process.
 *
 * [namespace] is the opaque storage key supplied to [EventLoggingWorkScheduler]. Do not use a
 * customer identifier, session ID, URL, or credential as a namespace.
 *
 * Keep resolution local and bounded: reconstruct the configuration here (it carries the gateway and
 * environment), and leave all network calls to [EventLoggingGateway] when the worker drains the queue.
 */
public fun interface EventLoggingWorkProvider {
    /** Returns the configuration, including its host gateway, for [namespace], or null if unknown. */
    public fun resolve(namespace: String): EventLoggingConfig?
}
