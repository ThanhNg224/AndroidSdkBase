package io.github.thanhng224.consumer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Entry point that makes the SDK reachable, so the release build's R8 run actually processes it. */
public class ConsumerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* SDK_CALLS_BEGIN */
        setContent { ConsumerScreen() }
        lifecycleScope.launch { Log.i(TAG, KotlinConsumer.run()) }
        Log.i(TAG, "java: ${JavaConsumer.buildConfig()} / ${JavaConsumer.buildConfigFromCallbackGateway()}")
        /* SDK_CALLS_END */
    }

    private companion object {
        const val TAG = "SdkConsumer"
    }
}
