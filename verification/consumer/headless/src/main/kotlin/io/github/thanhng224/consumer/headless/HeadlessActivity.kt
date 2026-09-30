package io.github.thanhng224.consumer.headless

import android.app.Activity
import android.os.Bundle
import io.github.thanhng224.consumer.JavaConsumer
import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.otp.OtpSdk
import io.github.thanhng224.sdkbase.otp.session.OtpSession

/** Reachable Java and Kotlin calls, with no UI toolkit or host coroutine dependency. */
class HeadlessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* SDK_CALLS_BEGIN */
        check(RemoteConfigJavaConsumer.fetch().revision == "java-v1")
        val config = JavaConsumer.buildConfigWithLogger().getOrNull() ?: return
        OtpSdk.start(config, object : ResultCallback<OtpSession> {
            override fun onSuccess(value: OtpSession) {
                value.close()
            }
            override fun onFailure(error: SdkError) { }
        })
        JavaConsumer.startFromJava(config)
        /* SDK_CALLS_END */
    }
}
