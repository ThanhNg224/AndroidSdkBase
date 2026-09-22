package io.github.thanhng224.consumer;

import io.github.thanhng224.sdkbase.core.SdkResult;
import io.github.thanhng224.sdkbase.core.gateway.OtpChallenge;
import io.github.thanhng224.sdkbase.core.gateway.OtpGateway;
import io.github.thanhng224.sdkbase.otpsdk.OtpSdkConfig;

import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compiles against the published AAR from Java. If a Kotlin-only construct leaked into the public
 * surface — a default argument, an inline class, a `suspend` function with no Java-friendly
 * alternative — this file stops compiling, which is exactly the signal we want.
 *
 * Confirmed against the real compiled bytecode of the published `otp-sdk`/`core` AARs with:
 *   javap -p io/github/thanhng224/sdkbase/core/gateway/OtpGateway.class
 * The `OtpGateway` methods erase to `Object requestOtp(String, Continuation)` and
 * `Object verifyOtp(String, String, Continuation)` — Kotlin's suspend-function calling convention.
 * A Java implementation CAN satisfy this interface, but only by hand-writing the Continuation
 * parameter and returning the result directly instead of ever suspending — see the class doc on
 * JavaGateway below for why this is a real finding, not just a workaround.
 */
public final class JavaConsumer {

    private JavaConsumer() {
    }

    /**
     * A host gateway written in Java.
     *
     * This compiles and behaves correctly ONLY because it never actually needs to suspend: it
     * returns a fully-formed {@link SdkResult} synchronously instead of invoking the continuation.
     * Kotlin's suspend calling convention lets a non-suspending JVM caller/implementor return the
     * real result directly in place of the `COROUTINE_SUSPENDED` sentinel, so a synchronous Java
     * implementation is possible. But a Java host that genuinely needs to call an async API (a
     * callback-based HTTP client, for instance) has no reasonable way to bridge that callback into
     * this method: it would have to either block a thread waiting for the callback, or manually
     * drive the raw `Continuation.resumeWith` protocol, which is undocumented for Java callers and
     * not a supported public API. This is a finding about the API surface, not this test file.
     */
    public static final class JavaGateway implements OtpGateway {

        @Nullable
        @Override
        public Object requestOtp(
                @NotNull String destination,
                @NotNull Continuation<? super SdkResult<OtpChallenge>> continuation) {
            return new SdkResult.Success<>(new OtpChallenge("java-ch", 6, 60, 30));
        }

        @Nullable
        @Override
        public Object verifyOtp(
                @NotNull String challengeId,
                @NotNull String code,
                @NotNull Continuation<? super SdkResult<kotlin.Unit>> continuation) {
            return new SdkResult.Success<>(kotlin.Unit.INSTANCE);
        }
    }

    /** Builds a config with no Kotlin default arguments available. */
    public static SdkResult<OtpSdkConfig> buildConfig() {
        return new OtpSdkConfig.Builder("0900000000", new JavaGateway())
                .maxAttempts(3)
                .build();
    }

    /**
     * Note: there is deliberately no `OtpSdk.VERSION`. Task 8 removed it — wiring a version constant
     * would have meant enabling `buildConfig`, and AGP generates `BuildConfig` in the module's
     * namespace root where the ABI dump cannot exclude it, making it permanent published contract.
     * A host reads the resolved Maven coordinate instead.
     */
}
