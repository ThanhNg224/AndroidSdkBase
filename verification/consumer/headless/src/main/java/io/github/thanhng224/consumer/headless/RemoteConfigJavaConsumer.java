package io.github.thanhng224.consumer.headless;

import io.github.thanhng224.sdkbase.core.call.ResultCallback;
import io.github.thanhng224.sdkbase.core.error.SdkError;
import io.github.thanhng224.sdkbase.core.result.SdkResult;
import io.github.thanhng224.sdkbase.remoteconfig.RemoteConfigSdk;
import io.github.thanhng224.sdkbase.remoteconfig.config.RemoteConfigSdkConfig;
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigCallbackGateway;
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/** No Continuation: Java supplies a callback gateway through the config builder. */
public final class RemoteConfigJavaConsumer {
    private RemoteConfigJavaConsumer() { }

    public static RemoteConfig fetch() {
        RemoteConfigCallbackGateway gateway = callback ->
                callback.onSuccess(new RemoteConfig("java-v1", Collections.singletonMap("theme", "light")));
        SdkResult<RemoteConfigSdkConfig> built = new RemoteConfigSdkConfig.Builder(gateway)
                .environment(RemoteConfigKotlinConsumer.environment())
                .gatewayTimeoutMillis(1000L)
                .build();
        if (!(built instanceof SdkResult.Success)) throw new AssertionError("Config rejected");
        RemoteConfigSdkConfig config = ((SdkResult.Success<RemoteConfigSdkConfig>) built).getValue();
        AtomicReference<RemoteConfig> value = new AtomicReference<>();
        AtomicReference<SdkError> failure = new AtomicReference<>();
        RemoteConfigSdk.fetch(config, new ResultCallback<RemoteConfig>() {
            @Override public void onSuccess(RemoteConfig result) { value.set(result); }
            @Override public void onFailure(SdkError error) { failure.set(error); }
        });
        // The fixture environment uses immediate dispatchers and a synchronous host callback.
        if (failure.get() != null || value.get() == null) throw new AssertionError("Fetch failed");
        return value.get();
    }
}
