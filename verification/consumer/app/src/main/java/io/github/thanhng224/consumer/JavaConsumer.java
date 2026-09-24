package io.github.thanhng224.consumer;

import io.github.thanhng224.sdkbase.core.gateway.CompletionCallback;
import io.github.thanhng224.sdkbase.core.gateway.GatewayCallback;
import io.github.thanhng224.sdkbase.core.logging.LogLevel;
import io.github.thanhng224.sdkbase.core.logging.SdkLogger;
import io.github.thanhng224.sdkbase.core.result.SdkResult;
import io.github.thanhng224.sdkbase.otp.config.OtpSdkConfig;
import io.github.thanhng224.sdkbase.otp.gateway.OtpCallbackGateway;
import io.github.thanhng224.sdkbase.otp.gateway.OtpChallenge;
import io.github.thanhng224.sdkbase.otp.gateway.OtpGateway;

import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compiles against the published AAR from Java. A Kotlin-only construct in the public surface —
 * a default argument, a `suspend` fun with no Java-friendly alternative — would stop this file
 * compiling, which is exactly the signal wanted.
 */
public final class JavaConsumer {

    private JavaConsumer() {
    }

    /**
     * A host gateway written in Java. Compiles only because it never actually suspends: it returns
     * a fully-formed {@link SdkResult} synchronously instead of invoking the continuation. A host
     * needing genuine async should implement {@link OtpCallbackGateway} instead (see below).
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
                              @NotNull CompletionCallback callback) {
            new Thread(callback::onSuccess).start();
        }
    }

    /** Builds a config without the Java caller ever naming a Continuation. */
    public static SdkResult<OtpSdkConfig> buildConfigFromCallbackGateway() {
        return new OtpSdkConfig.Builder("0900000000", new JavaCallbackGateway())
                .maxAttempts(3)
                .build();
    }

    /**
     * Builds an {@link SdkLogger} entirely from Java: no Kotlin default argument, and the sink is a
     * plain Java lambda ({@link io.github.thanhng224.sdkbase.core.logging.LogSink} is a
     * {@code fun interface}, so it is SAM-convertible from Java too).
     */
    public static SdkLogger buildLogger() {
        return new SdkLogger.Builder()
                .minLevel(LogLevel.DEBUG)
                .sink(record -> { })
                .build();
    }

    /** Proves the config builder accepts a Java-built logger with no Kotlin-only entry point. */
    public static SdkResult<OtpSdkConfig> buildConfigWithLogger() {
        return new OtpSdkConfig.Builder("0900000000", new JavaGateway())
                .logger(buildLogger())
                .build();
    }

    /**
     * Note: there is deliberately no {@code OtpSdk.VERSION} — AGP's generated {@code BuildConfig}
     * would sit in the module's namespace root, where the ABI dump cannot exclude it, making any
     * field there permanent published contract. A host reads the resolved Maven coordinate instead.
     */
}
