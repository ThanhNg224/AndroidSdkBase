package io.github.thanhng224.consumer.logging;

import java.io.File;
import java.util.Collections;
import kotlin.Unit;
import io.github.thanhng224.sdkbase.core.call.ResultCallback;
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment;
import io.github.thanhng224.sdkbase.core.error.SdkError;
import io.github.thanhng224.sdkbase.core.logging.SdkLogger;
import io.github.thanhng224.sdkbase.core.result.SdkResult;
import io.github.thanhng224.sdkbase.eventlogging.EventLoggingSdk;
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig;
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent;
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingCallbackGateway;
import io.github.thanhng224.sdkbase.eventlogging.session.EventLoggingSession;
import io.github.thanhng224.sdkbase.eventlogging.work.EventLoggingWorkScheduler;
import io.github.thanhng224.sdkbase.logging.file.FileLoggingSdk;
import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig;
import io.github.thanhng224.sdkbase.logging.file.session.FileLoggingSession;

/** All callbacks and host transport compile from Java without Continuation. */
public final class JavaLoggingConsumer {
    private JavaLoggingConsumer() { }
    public static void startFiles(File root) {
        SdkResult<FileLoggingConfig> built = new FileLoggingConfig.Builder(new File(root, "technical"))
                .captureCrashes(false).maxFiles(2).maxFileBytes(65536)
                .environment(new SdkEnvironment.Builder().build()).build();
        if (!(built instanceof SdkResult.Success)) return;
        FileLoggingConfig config = ((SdkResult.Success<FileLoggingConfig>) built).getValue();
        FileLoggingSdk.start(config, new ResultCallback<FileLoggingSession>() {
            @Override public void onSuccess(FileLoggingSession value) {
                new SdkLogger.Builder().sink(value).build().tagged("consumer").i(() -> "file log from Java");
                value.flush(new ResultCallback<Unit>() {
                    @Override public void onSuccess(Unit unit) { value.close(); }
                    @Override public void onFailure(SdkError error) { value.close(); }
                });
            }
            @Override public void onFailure(SdkError error) { }
        });
    }
    public static void track(EventLoggingSession session, EventLoggingEvent event) {
        session.track(event, new ResultCallback<Unit>() {
            @Override public void onSuccess(Unit value) {
                session.flush(new ResultCallback<Unit>() {
                    @Override public void onSuccess(Unit unit) { session.close(); }
                    @Override public void onFailure(SdkError error) { session.close(); }
                });
            }
            @Override public void onFailure(SdkError error) { session.close(); }
        });
    }
    public static EventLoggingConfig restore(String namespace, File root) {
        EventLoggingCallbackGateway gateway = (record, callback) -> callback.onSuccess();
        SdkResult<EventLoggingConfig> built = new EventLoggingConfig.Builder(namespace, root, gateway).build();
        return built instanceof SdkResult.Success ? ((SdkResult.Success<EventLoggingConfig>) built).getValue() : null;
    }
    public static void schedule(EventLoggingWorkScheduler scheduler, String namespace, ResultCallback<Unit> callback) {
        scheduler.schedule(namespace, callback);
    }
    public static void startEvents(File root) {
        EventLoggingCallbackGateway gateway = (record, callback) -> callback.onSuccess();
        SdkResult<EventLoggingConfig> built = new EventLoggingConfig.Builder("java-logging", root, gateway)
                .allowedAttributeKeys(Collections.singleton("outcome"))
                .environment(new SdkEnvironment.Builder().build()).build();
        if (!(built instanceof SdkResult.Success)) return;
        EventLoggingConfig config = ((SdkResult.Success<EventLoggingConfig>) built).getValue();
        EventLoggingSdk.start(config, new ResultCallback<EventLoggingSession>() {
            @Override public void onSuccess(EventLoggingSession value) {
                track(value, new EventLoggingEvent.Builder("java_event").attributes(Collections.singletonMap("outcome", "ok")).build());
            }
            @Override public void onFailure(SdkError error) { }
        });
    }
}
