# WorkManager loads Worker subclasses reflectively from the class name stored in its database.
-keep public class io.github.thanhng224.sdkbase.eventlogging.work.internal.EventLoggingWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
