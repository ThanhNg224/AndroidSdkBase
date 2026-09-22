package io.github.thanhng224.consumer;

import io.github.thanhng224.sdkbase.core.SdkResult;
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback;
import io.github.thanhng224.sdkbase.core.gateway.OtpCallbackGateway;
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
 * JavaGateway below. The Java-facing answer for a genuinely asynchronous host is
 * {@link OtpCallbackGateway} / {@link JavaCallbackGateway} below, not this interface.
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
     * implementation is possible. A Java host that genuinely needs to call an async API (a
     * callback-based HTTP client, for instance) should implement {@link OtpCallbackGateway}
     * instead — see {@link JavaCallbackGateway} below — rather than hand-driving the raw
     * `Continuation.resumeWith` protocol, which is undocumented for Java callers and not a
     * supported public API.
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

    /** What a Java host with a callback-based HTTP client can now actually write. */
    public static final class JavaCallbackGateway implements OtpCallbackGateway {

        @Override
        public void requestOtp(@NotNull String destination,
                               @NotNull GatewayCallback<OtpChallenge> callback) {
            new Thread(() -> callback.onSuccess(new OtpChallenge("java-async", 6, 60, 30))).start();
        }

        @Override
        public void verifyOtp(@NotNull String challengeId, @NotNull String code,
                              @NotNull GatewayCallback<kotlin.Unit> callback) {
            new Thread(() -> callback.onSuccess(kotlin.Unit.INSTANCE)).start();
        }
    }

    /** Builds a config without the Java caller ever naming a Continuation. */
    public static SdkResult<OtpSdkConfig> buildConfigFromCallbackGateway() {
        return new OtpSdkConfig.Builder("0900000000", new JavaCallbackGateway())
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
