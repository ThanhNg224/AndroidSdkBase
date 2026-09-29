package {{SDK_NAMESPACE}}.profilecallback

import {{SDK_NAMESPACE}}.core.error.SdkErrors
import {{SDK_NAMESPACE}}.core.gateway.GatewayCallback
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.profile.ProfileSdk
import {{SDK_NAMESPACE}}.profile.config.ProfileSdkConfig
import {{SDK_NAMESPACE}}.profile.gateway.ProfileCallbackGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileCallbackAdapterTest {
    @Test fun `first callback wins and duplicate is ignored`() = runTest {
        lateinit var callback: GatewayCallback<String>
        val adapter = ProfileCallbackAdapter(ProfileCallbackGateway { callback = it })
        val pending = async { adapter.loadProfile() }
        runCurrent()
        callback.onSuccess("Ada")
        callback.onFailure(SdkErrors.unknown())
        assertEquals(SdkResult.Success("Ada"), pending.await())
    }

    @Test fun `host registration throw is contained by feature safe call`() = runTest {
        val adapter = ProfileCallbackAdapter(ProfileCallbackGateway { error("host registration failed") })
        val result = ProfileSdk.start(ProfileSdkConfig.Builder(adapter).build())
        assertTrue(result is SdkResult.Failure)
    }

    @Test fun `late callback after cancellation is ignored`() = runTest {
        lateinit var callback: GatewayCallback<String>
        val adapter = ProfileCallbackAdapter(ProfileCallbackGateway { callback = it })
        val pending = async { adapter.loadProfile() }
        runCurrent()
        pending.cancelAndJoin()
        callback.onSuccess("late")
        callback.onFailure(SdkErrors.unknown())
        assertTrue(pending.isCancelled)
    }
}
