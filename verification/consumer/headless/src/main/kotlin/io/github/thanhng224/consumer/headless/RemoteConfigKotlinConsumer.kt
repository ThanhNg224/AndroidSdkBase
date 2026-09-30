package io.github.thanhng224.consumer.headless

import io.github.thanhng224.sdkbase.core.concurrency.DispatcherProvider
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.remoteconfig.RemoteConfigSdk
import io.github.thanhng224.sdkbase.remoteconfig.config.RemoteConfigSdkConfig
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigGateway
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Coordinate-only one-shot call sites, shared by the release build and local runtime proof. */
object RemoteConfigKotlinConsumer {
    @JvmStatic
    fun environment(): SdkEnvironment = SdkEnvironment.Builder().dispatchers(object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
    }).build()

    suspend fun fetch(): RemoteConfig {
        val gateway = RemoteConfigGateway { SdkResult.Success(RemoteConfig("kotlin-v1", mapOf("theme" to "dark"))) }
        val config = RemoteConfigSdkConfig.Builder(gateway).environment(environment()).build().getOrNull()
            ?: error("Config rejected")
        return RemoteConfigSdk.fetch(config).getOrNull() ?: error("Fetch failed")
    }
}
