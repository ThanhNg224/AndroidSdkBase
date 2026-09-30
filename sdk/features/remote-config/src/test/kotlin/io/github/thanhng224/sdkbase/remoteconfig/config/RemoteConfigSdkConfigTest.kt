package io.github.thanhng224.sdkbase.remoteconfig.config

import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.testing.assertFailure
import io.github.thanhng224.sdkbase.core.testing.assertSuccess
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigGateway
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConfigSdkConfigTest {
    private val gateway = RemoteConfigGateway { SdkResult.Success(RemoteConfig("v1", emptyMap())) }

    @Test
    fun defaultsUseABoundedTimeout() {
        assertEquals(30_000L, RemoteConfigSdkConfig.Builder(gateway).build().assertSuccess().gatewayTimeoutMillis)
    }

    @Test
    fun nonPositiveTimeoutIsRejectedAtBuild() {
        for (timeout in listOf(0L, -1L)) {
            val error = RemoteConfigSdkConfig.Builder(gateway).gatewayTimeoutMillis(timeout).build().assertFailure()
            assertEquals(SdkErrors.INVALID_CONFIG, error.code)
        }
    }
}
