package io.github.thanhng224.sdkbase.core.annotation

/**
 * Marks a declaration meant only for SDK modules (`core`, a feature, a composition) — a helper a
 * host builds on indirectly (e.g. through `OtpSdk.start(config, callback)`) but should never call,
 * construct or reference directly. `sdkbase.android.library` opts every SDK module in via
 * `compilerOptions.optIn`, so nothing inside `sdk/` needs an explicit `@OptIn`; `apps/demo` and
 * `verification/consumer` are not opted in, so a host reaching for a `@SdkInternalApi` declaration
 * gets a compile error naming it, exactly like any other unstable API.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class SdkInternalApi
