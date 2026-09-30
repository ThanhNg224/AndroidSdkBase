package io.github.thanhng224.sdkbase.remoteconfig

import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.core.testing.assertFailure
import io.github.thanhng224.sdkbase.core.testing.assertSuccess
import io.github.thanhng224.sdkbase.remoteconfig.config.RemoteConfigSdkConfig
import io.github.thanhng224.sdkbase.remoteconfig.error.RemoteConfigErrors
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigCallbackGateway
import io.github.thanhng224.sdkbase.remoteconfig.gateway.RemoteConfigGateway
import io.github.thanhng224.sdkbase.remoteconfig.snapshot.RemoteConfig
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RemoteConfigSdkTest {
    private val snapshot = RemoteConfig("v1", mapOf("theme" to "dark"))

    @Test
    fun returnsHostSnapshot() = runTest {
        val config = config(
            RemoteConfigGateway { SdkResult.Success(snapshot) },
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
        assertEquals(snapshot, RemoteConfigSdk.fetch(config).assertSuccess())
    }

    @Test
    fun preservesHostFailure() = runTest {
        val error = SdkErrors.networkUnavailable()
        val config = config(
            RemoteConfigGateway { SdkResult.Failure(error) },
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
        assertEquals(error, RemoteConfigSdk.fetch(config).assertFailure())
    }

    @Test
    fun mapsHostExceptions() = runTest {
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        for ((throwable, expected) in listOf(
            IOException("offline") to SdkErrors.NETWORK_UNAVAILABLE,
            IllegalStateException("host") to SdkErrors.GATEWAY_FAILURE,
        )) {
            val config = config(RemoteConfigGateway { throw throwable }, dispatchers)
            assertEquals(expected, RemoteConfigSdk.fetch(config).assertFailure().code)
        }
    }

    @Test
    fun timesOutSuspendingHost() = runTest {
        val config = config(
            RemoteConfigGateway { awaitCancellation() },
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            timeoutMillis = 10L,
        )
        assertEquals(SdkErrors.TIMEOUT, RemoteConfigSdk.fetch(config).assertFailure().code)
        assertEquals(10L, testScheduler.currentTime)
    }

    @Test
    fun rejectsBlankRevision() = runTest {
        val config = config(
            RemoteConfigGateway { SdkResult.Success(RemoteConfig(" ", emptyMap())) },
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
        assertEquals(RemoteConfigErrors.INVALID_RESPONSE, RemoteConfigSdk.fetch(config).assertFailure().code)
    }

    @Test(expected = CancellationException::class)
    fun propagatesCallerCancellation() = runTest {
        val config = config(
            RemoteConfigGateway { throw CancellationException("caller") },
            TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
        RemoteConfigSdk.fetch(config)
    }

    @Test
    fun callbackGatewayUsesFirstTerminalResult() = runTest {
        val gateway = RemoteConfigCallbackGateway { callback ->
            callback.onSuccess(snapshot)
            callback.onFailure(SdkErrors.gatewayFailure("late"))
        }
        val config = RemoteConfigSdkConfig.Builder(gateway)
            .environment(
                SdkEnvironment.Builder()
                    .dispatchers(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
                    .build(),
            )
            .build().assertSuccess()
        assertEquals(snapshot, RemoteConfigSdk.fetch(config).assertSuccess())
    }

    @Test
    fun callbackTwinDeliversOnMainDispatcher() {
        val mainExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "remote-config-main") }
        val backgroundExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "remote-config-background") }
        mainExecutor.asCoroutineDispatcher().use { main ->
            backgroundExecutor.asCoroutineDispatcher().use { background ->
                val ready = CountDownLatch(1)
                var gatewayThread = ""
                var callbackThread = ""
                var result: RemoteConfig? = null
                var failure: SdkError? = null
                val config = config(
                    RemoteConfigGateway {
                        gatewayThread = Thread.currentThread().name
                        SdkResult.Success(snapshot)
                    },
                    TestDispatcherProvider(main, background),
                )
                RemoteConfigSdk.fetch(
                    config,
                    object : ResultCallback<RemoteConfig> {
                        override fun onSuccess(value: RemoteConfig) {
                            result = value
                            callbackThread = Thread.currentThread().name
                            ready.countDown()
                        }
                        override fun onFailure(error: SdkError) {
                            failure = error
                            ready.countDown()
                        }
                    },
                )
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                assertEquals(null, failure)
                assertEquals(snapshot, result)
                assertTrue(gatewayThread.startsWith("remote-config-background"))
                assertTrue(callbackThread.startsWith("remote-config-main"))
            }
        }
    }

    private fun config(
        gateway: RemoteConfigGateway,
        dispatchers: TestDispatcherProvider,
        timeoutMillis: Long = 30_000L,
    ): RemoteConfigSdkConfig =
        RemoteConfigSdkConfig.Builder(gateway)
            .environment(SdkEnvironment.Builder().dispatchers(dispatchers).build())
            .gatewayTimeoutMillis(timeoutMillis)
            .build().assertSuccess()
}
