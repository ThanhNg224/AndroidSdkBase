package {{pkg}}.internal

import {{ns}}.core.logging.SdkLogger
import {{ns}}.core.session.SdkSessionBase
import {{ns}}.core.session.SessionScope
import {{ns}}.core.session.StateStore
import {{pkg}}.config.{{pascal}}SdkConfig
import {{pkg}}.session.{{pascal}}Session
import {{pkg}}.session.{{pascal}}State

/**
 * Wires this feature's own work to the public [{{pascal}}Session] contract, on top of
 * [SdkSessionBase]. Kept Kotlin `internal`. Add operations as `scope.ifOpen { ... }` (suspend),
 * their Java twins as `scope.call(callback) { ... }`, and fire-and-forget work as
 * `scope.launch { ... }` — every one of those routes `close()` racing an operation through
 * [SessionScope] instead of this class re-implementing the guard — see `:sdk:features:otp`'s
 * OtpSdkRuntime for the pattern.
 */
internal class {{pascal}}SdkRuntime(
    scope: SessionScope,
    store: StateStore<{{pascal}}State>,
    private val config: {{pascal}}SdkConfig,
    logger: SdkLogger,
) : SdkSessionBase<{{pascal}}State>(scope, store), {{pascal}}Session {

    private val log = logger.tagged(TAG)

    /** Runs exactly once, the first time [close] actually closes the session (see
     * [SdkSessionBase.close]'s idempotency guarantee) — a legitimate diagnostic, not a
     * workaround: lets a host (or a test) confirm a session actually got released instead of
     * leaked. */
    override fun onClose() {
        log.d { "session closed" }
    }

    private companion object {
        const val TAG = "{{pascal}}Session"
    }
}
