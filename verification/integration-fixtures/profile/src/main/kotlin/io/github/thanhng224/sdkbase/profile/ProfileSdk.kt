package {{SDK_NAMESPACE}}.profile

import {{SDK_NAMESPACE}}.core.call.Cancellable
import {{SDK_NAMESPACE}}.core.call.ResultCallback
import {{SDK_NAMESPACE}}.core.call.launchCallback
import {{SDK_NAMESPACE}}.core.call.safeCall
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.core.session.SessionScope
import {{SDK_NAMESPACE}}.profile.config.ProfileSdkConfig
import {{SDK_NAMESPACE}}.profile.internal.ProfileRuntime
import {{SDK_NAMESPACE}}.profile.session.ProfileSession
import {{SDK_NAMESPACE}}.profile.session.ProfileState
import kotlinx.coroutines.CancellationException

public object ProfileSdk {
    private const val GATEWAY_TIMEOUT_MILLIS: Long = 30_000L

    @JvmStatic
    public suspend fun start(config: ProfileSdkConfig): SdkResult<ProfileSession> {
        val environment = config.environment
        val logger = environment.logger.withSession(environment.idGenerator.newId())
        val scope = SessionScope(environment.dispatchers, logger.tagged("ProfileSession"))
        return try {
            when (
                val loaded = safeCall("loadProfile", timeoutMillis = GATEWAY_TIMEOUT_MILLIS) {
                    config.gateway.loadProfile()
                }
            ) {
                is SdkResult.Success -> SdkResult.Success(ProfileRuntime(scope, ProfileState(loaded.value), logger))
                is SdkResult.Failure -> {
                    scope.close()
                    SdkResult.Failure(loaded.error)
                }
            }
        } catch (cancelled: CancellationException) {
            scope.close()
            throw cancelled
        }
    }

    /** Java-callable twin; a session that loses delivery is closed by [launchCallback]. */
    @JvmStatic
    public fun start(config: ProfileSdkConfig, callback: ResultCallback<ProfileSession>): Cancellable =
        launchCallback(
            dispatchers = config.environment.dispatchers,
            callback = callback,
            onUndelivered = { it.close() },
        ) { start(config) }
}
