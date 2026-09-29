package {{SDK_NAMESPACE}}.fixture;

import android.app.Activity;
import android.os.Bundle;

/** Manifest entrypoint keeps the Java/Kotlin call sites reachable through release R8. */
public final class FixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FixtureJavaConsumer.start();
    }
}
