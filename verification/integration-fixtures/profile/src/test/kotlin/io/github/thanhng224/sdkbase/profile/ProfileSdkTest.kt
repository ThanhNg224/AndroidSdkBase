package {{SDK_NAMESPACE}}.profile

import {{SDK_NAMESPACE}}.core.environment.SdkEnvironment
import {{SDK_NAMESPACE}}.core.result.SdkResult
import {{SDK_NAMESPACE}}.profile.config.ProfileSdkConfig
import {{SDK_NAMESPACE}}.profile.gateway.ProfileGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSdkTest {
    @Test fun `loads profile into session`() = runTest {
        val config = ProfileSdkConfig.Builder(ProfileGateway { SdkResult.Success("Ada") })
            .environment(SdkEnvironment.Default)
            .build()
        val result = ProfileSdk.start(config)
        assertTrue(result is SdkResult.Success)
        val session = (result as SdkResult.Success).value
        assertEquals("Ada", session.state.value.displayName)
        session.close()
    }

    @Test fun `contains host exception as failure`() = runTest {
        val config = ProfileSdkConfig.Builder(ProfileGateway { error("fixture host failure") }).build()
        val result = ProfileSdk.start(config)
        assertTrue(result is SdkResult.Failure)
    }
}
