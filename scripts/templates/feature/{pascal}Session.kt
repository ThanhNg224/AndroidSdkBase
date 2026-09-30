package {{pkg}}.session

import {{ns}}.core.session.SdkSession

/**
 * A running {{pascal}} flow. The host holds this, renders [state], and closes it when done. `state`,
 * `observeState` and `close` come from [SdkSession] — every feature session extends it instead of
 * redeclaring the three members every session needs. Add operations here as a suspend
 * `fun x(): SdkResult<T>` plus its Java-callable twin `fun x(callback: ResultCallback<T>):
 * Cancellable` — see `:sdk:features:otp`'s OtpSession for the pattern to follow once this feature
 * has calls to make.
 */
public interface {{pascal}}Session : SdkSession<{{pascal}}State>
