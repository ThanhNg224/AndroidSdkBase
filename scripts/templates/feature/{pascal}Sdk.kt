package {{pkg}}

import {{ns}}.core.call.Cancellable
import {{ns}}.core.call.ResultCallback
import {{ns}}.core.call.launchCallback
import {{ns}}.core.result.SdkResult
import {{ns}}.core.session.SessionScope
import {{ns}}.core.session.StateStore
import {{pkg}}.config.{{pascal}}SdkConfig
import {{pkg}}.internal.{{pascal}}SdkRuntime
import {{pkg}}.session.{{pascal}}Session
import {{pkg}}.session.{{pascal}}State
import kotlinx.coroutines.CancellationException

/**
 * The only class a host needs to know about. It validates, wires and delegates — and nothing
 * else. No networking, no storage, no global mutable state, no DI container.
 */
public object {{pascal}}Sdk {

    @JvmStatic
    public suspend fun start(config: {{pascal}}SdkConfig): SdkResult<{{pascal}}Session> {
        val environment = config.environment
        val sessionLogger = environment.logger.withSession(environment.idGenerator.newId())
        // The one SessionScope the whole session lives on: every coroutine this feature ever
        // starts runs on it — there is no other CoroutineScope anywhere in this module.
        val scope = SessionScope(environment.dispatchers, sessionLogger.tagged("{{pascal}}Session"))
        val store = StateStore({{pascal}}State.initial())
        return try {
            // TODO: replace with real work — call config.gateway through safeCall, drive
            // `store.withLock { ... }` from its result, and on a failure `scope.close()` and return
            // it. Left as a stub so the module is green from the first commit — see
            // `:sdk:features:otp`'s OtpSdk.start for the pattern.
            SdkResult.Success({{pascal}}SdkRuntime(scope, store, config, sessionLogger))
        } catch (e: CancellationException) {
            // The caller was cancelled mid-start: no session is returned to close it, so close the
            // scope here or whatever it already started outlives the call.
            scope.close()
            throw e
        }
    }

    /**
     * The Java-callable twin of [start]: no `Continuation`, delivered on the config's
     * [SdkEnvironment][{{ns}}.core.environment.SdkEnvironment]'s main dispatcher. A session that
     * finishes starting but never gets delivered — the caller cancelled first — is closed instead
     * of leaked.
     */
    @JvmStatic
    public fun start(config: {{pascal}}SdkConfig, callback: ResultCallback<{{pascal}}Session>): Cancellable =
        launchCallback(
            dispatchers = config.environment.dispatchers,
            callback = callback,
            onUndelivered = { it.close() },
        ) { start(config) }
}
