package {{SDK_NAMESPACE}}.fixture;

import {{SDK_NAMESPACE}}.core.call.Cancellable;
import {{SDK_NAMESPACE}}.core.call.ResultCallback;
import {{SDK_NAMESPACE}}.core.error.SdkError;
import {{SDK_NAMESPACE}}.onboarding.OnboardingSdk;
import {{SDK_NAMESPACE}}.onboarding.config.OnboardingSdkConfig;

/** Compile-time Java proof for published coordinate calls and the callback twin. */
public final class FixtureJavaConsumer {
    private FixtureJavaConsumer() { }

    public static Cancellable start() {
        OnboardingSdkConfig config = KotlinConsumer.createConfig();
        return OnboardingSdk.start(config, "1234", new ResultCallback<String>() {
            @Override public void onSuccess(String profileName) { }
            @Override public void onFailure(SdkError error) { }
        });
    }
}
