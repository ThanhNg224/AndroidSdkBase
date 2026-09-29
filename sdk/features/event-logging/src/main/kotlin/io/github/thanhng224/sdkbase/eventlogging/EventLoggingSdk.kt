package io.github.thanhng224.sdkbase.eventlogging

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.logging.TaggedLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingCallbackGateway
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.gateway.asGateway
import io.github.thanhng224.sdkbase.eventlogging.internal.runtime.EventLoggingRuntime
import io.github.thanhng224.sdkbase.eventlogging.internal.storage.DurableEventQueue
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private val SESSION_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")

/** Entry point for the optional durable business event logger. */
public object EventLoggingSdk {

    /** Starts a session and confirms the durable delivery wake-up before returning it. */
    @JvmStatic
    public suspend fun start(
        config: EventLoggingConfig,
        gateway: EventLoggingGateway,
        environment: SdkEnvironment,
    ): SdkResult<EventLoggingSession> = startInternal(config, gateway, environment, scheduleEnabled = true, bootstrap = true)

    /** Starts using a Java-friendly callback gateway. */
    @JvmStatic
    public suspend fun start(
        config: EventLoggingConfig,
        gateway: EventLoggingCallbackGateway,
        environment: SdkEnvironment,
    ): SdkResult<EventLoggingSession> = start(config, gateway.asGateway(), environment)

    /** Java-callable form of [start] for a suspend gateway. */
    @JvmStatic
    public fun start(
        config: EventLoggingConfig,
        gateway: EventLoggingGateway,
        environment: SdkEnvironment,
        callback: ResultCallback<EventLoggingSession>,
    ): Cancellable = launchCallback(
        dispatchers = environment.dispatchers,
        callback = callback,
        onUndelivered = { it.close() },
    ) { start(config, gateway, environment) }

    /** Java-callable form of [start] for a callback gateway. */
    @JvmStatic
    public fun start(
        config: EventLoggingConfig,
        gateway: EventLoggingCallbackGateway,
        environment: SdkEnvironment,
        callback: ResultCallback<EventLoggingSession>,
    ): Cancellable = start(config, gateway.asGateway(), environment, callback)

    /**
     * One-shot worker entry point. It bypasses scheduler callbacks and leaves retry timing to the
     * worker system; every failure retains the failed record, and the private session always closes.
     */
    @JvmStatic
    public suspend fun deliverPending(
        config: EventLoggingConfig,
        gateway: EventLoggingGateway,
        environment: SdkEnvironment,
    ): SdkResult<Unit> {
        val started = startInternal(config, gateway, environment, scheduleEnabled = false, bootstrap = false)
        if (started is SdkResult.Failure) return started
        val session = (started as SdkResult.Success).value
        return try {
            session.flush()
        } finally {
            session.close()
        }
    }

    /** One-shot worker entry point for a callback gateway. */
    @JvmStatic
    public suspend fun deliverPending(
        config: EventLoggingConfig,
        gateway: EventLoggingCallbackGateway,
        environment: SdkEnvironment,
    ): SdkResult<Unit> = deliverPending(config, gateway.asGateway(), environment)

    /** Java-callable one-shot worker entry point. */
    @JvmStatic
    public fun deliverPending(
        config: EventLoggingConfig,
        gateway: EventLoggingGateway,
        environment: SdkEnvironment,
        callback: ResultCallback<Unit>,
    ): Cancellable = launchCallback(environment.dispatchers, callback) {
        deliverPending(config, gateway, environment)
    }

    /** Java-callable one-shot worker entry point for a callback gateway. */
    @JvmStatic
    public fun deliverPending(
        config: EventLoggingConfig,
        gateway: EventLoggingCallbackGateway,
        environment: SdkEnvironment,
        callback: ResultCallback<Unit>,
    ): Cancellable = deliverPending(config, gateway.asGateway(), environment, callback)

    private suspend fun startInternal(
        config: EventLoggingConfig,
        gateway: EventLoggingGateway,
        environment: SdkEnvironment,
        scheduleEnabled: Boolean,
        bootstrap: Boolean,
    ): SdkResult<EventLoggingSession> {
        val queue = DurableEventQueue(config, environment.dispatchers) { environment.clock.nowMillis() }
        val initial = queue.load()
        if (initial is SdkResult.Failure) return initial
        val sessionId = try {
            environment.idGenerator.newId()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SdkResult.Failure(EventLoggingErrors.invalidEvent("session identifier could not be created"))
        }
        if (!SESSION_ID_PATTERN.matches(sessionId)) {
            return SdkResult.Failure(EventLoggingErrors.invalidEvent("session identifier is invalid"))
        }
        val sessionStartedAtMillis = try {
            environment.clock.nowMillis().coerceAtLeast(0L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SdkResult.Failure(EventLoggingErrors.invalidEvent("session clock failed"))
        }
        val logger: TaggedLogger = environment.logger.withSession(sessionId).tagged("EventLogging")
        val scope = SessionScope(environment.dispatchers, logger)
        val runtime = EventLoggingRuntime(
            config = config,
            gateway = gateway,
            environment = environment,
            sessionScope = scope,
            sessionId = sessionId,
            sessionStartedAtMillis = sessionStartedAtMillis,
            initialQueue = (initial as SdkResult.Success).value,
            schedulingEnabled = scheduleEnabled,
        )
        val session = EventLoggingSession(scope, runtime)
        if (bootstrap) {
            try {
                when (val scheduled = runtime.bootstrap()) {
                    is SdkResult.Success -> Unit
                    is SdkResult.Failure -> {
                        session.close()
                        return scheduled
                    }
                }
                currentCoroutineContext().ensureActive()
                runtime.start()
            } catch (e: CancellationException) {
                session.close()
                throw e
            }
        } else {
            runtime.start()
        }
        return SdkResult.Success(session)
    }
}
