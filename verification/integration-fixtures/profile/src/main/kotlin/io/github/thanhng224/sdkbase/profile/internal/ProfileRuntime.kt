package {{SDK_NAMESPACE}}.profile.internal

import {{SDK_NAMESPACE}}.core.session.SdkSessionBase
import {{SDK_NAMESPACE}}.core.session.SessionScope
import {{SDK_NAMESPACE}}.core.session.StateStore
import {{SDK_NAMESPACE}}.core.logging.SdkLogger
import {{SDK_NAMESPACE}}.profile.session.ProfileSession
import {{SDK_NAMESPACE}}.profile.session.ProfileState

internal class ProfileRuntime(scope: SessionScope, state: ProfileState, logger: SdkLogger) :
    SdkSessionBase<ProfileState>(scope, StateStore(state)), ProfileSession {
    private val log = logger.tagged("ProfileSession")

    override fun onClose() {
        log.d { "session closed" }
    }
}
