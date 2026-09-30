package io.github.thanhng224.sdkbase.eventlogging

import io.github.thanhng224.sdkbase.core.call.Cancellable
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.call.launchCallback
import io.github.thanhng224.sdkbase.core.logging.TaggedLogger
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.session.SessionScope
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.error.EventLoggingErrors
import io.github.thanhng224.sdkbase.eventlogging.internal.runtime.DefaultEventLoggingSession
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
    public suspend fun start(config: EventLoggingConfig): SdkResult<EventLoggingSession> =
        startInternal(config, scheduleEnabled = true, bootstrap = true)

    /** Java-callable form of [start]; a session that cannot be delivered is closed, not leaked. */
    @JvmStatic
    public fun start(config: EventLoggingConfig, callback: ResultCallback<EventLoggingSession>): Cancellable =
        launchCallback(
            dispatchers = config.environment.dispatchers,
            callback = callback,
            onUndelivered = { it.close() },
        ) { start(config) }

    /**
     * One-shot worker entry point. It bypasses scheduler callbacks and leaves retry timing to the
     * worker system; every failure retains the failed record, and the private session always closes.
     */
    @JvmStatic
    public suspend fun deliverPending(config: EventLoggingConfig): SdkResult<Unit> {
        val started = startInternal(config, scheduleEnabled = false, bootstrap = false)
        if (started is SdkResult.Failure) return started
        val session = (started as SdkResult.Success).value
        return try {
            session.flush()
        } finally {
            session.close()
        }
    }

    /** Java-callable form of [deliverPending]. */
    @JvmStatic
    public fun deliverPending(config: EventLoggingConfig, callback: ResultCallback<Unit>): Cancellable =
        launchCallback(config.environment.dispatchers, callback) { deliverPending(config) }

    private suspend fun startInternal(
        config: EventLoggingConfig,
        scheduleEnabled: Boolean,
        bootstrap: Boolean,
    ): SdkResult<EventLoggingSession> {
        val environment = config.environment
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
            gateway = config.gateway,
            environment = environment,
            sessionScope = scope,
            sessionId = sessionId,
            sessionStartedAtMillis = sessionStartedAtMillis,
            initialQueue = (initial as SdkResult.Success).value,
            schedulingEnabled = scheduleEnabled,
        )
        val session = DefaultEventLoggingSession(scope, runtime)
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
