package io.github.thanhng224.sdkbase.eventlogging.work

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.result.getOrNull
import io.github.thanhng224.sdkbase.eventlogging.EventLoggingSdk
import io.github.thanhng224.sdkbase.eventlogging.config.EventLoggingConfig
import io.github.thanhng224.sdkbase.eventlogging.event.EventLoggingEvent
import io.github.thanhng224.sdkbase.eventlogging.gateway.EventLoggingGateway
import io.github.thanhng224.sdkbase.eventlogging.work.internal.EventLoggingWorker
import io.github.thanhng224.sdkbase.eventlogging.work.provider.EventLoggingWorkConfiguration
import io.github.thanhng224.sdkbase.eventlogging.work.provider.EventLoggingWorkProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EventLoggingWorkerDeviceTest {
    private val app: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun config(root: File, namespace: String = "device-queue") =
        EventLoggingConfig.Builder(namespace, root).build().getOrNull()!!
    private class RestoredHost(base: Context, private val resolver: (String) -> EventLoggingWorkConfiguration?) :
        ContextWrapper(base), EventLoggingWorkProvider {
        override fun getApplicationContext(): Context = this
        override fun resolve(namespace: String): EventLoggingWorkConfiguration? = resolver(namespace)
    }
    private fun worker(host: Context) = TestListenableWorkerBuilder<EventLoggingWorker>(host)
        .setInputData(EventLoggingWorkScheduler.workerInput("device-queue")).build()

    private suspend fun restoredDelivery(host: Context): ListenableWorker.Result = withTimeout(10_000) {
        var result = worker(host).doWork()
        while (result == ListenableWorker.Result.retry()) {
            delay(10)
            result = worker(host).doWork()
        }
        result
    }

    @Test fun freshHostProviderDrainsPersistedQueueInOrder() = runBlocking {
        val root = File(app.cacheDir, "logging-proof-${UUID.randomUUID()}")
        try {
            val config = config(root)
            val failed = EventLoggingGateway { SdkResult.Failure(SdkError.Common(1001, "hold")) }
            val environment = SdkEnvironment.Default
            val session = EventLoggingSdk.start(config, failed, environment).getOrNull()!!
            assertTrue(session.track(EventLoggingEvent.Builder("first").build()) is SdkResult.Success)
            assertTrue(session.track(EventLoggingEvent.Builder("second").build()) is SdkResult.Success)
            assertTrue(session.flush() is SdkResult.Failure)
            session.close()
            val delivered = mutableListOf<String>()
            var resolutions = 0
            val freshHost = RestoredHost(app) { namespace ->
                resolutions++
                EventLoggingWorkConfiguration(
                    config(root, namespace),
                    EventLoggingGateway {
                        delivered += it.action
                        SdkResult.Success(Unit)
                    },
                    environment,
                )
            }
            assertEquals(ListenableWorker.Result.success(), restoredDelivery(freshHost))
            assertEquals(listOf("first", "second"), delivered)
            assertEquals(ListenableWorker.Result.success(), restoredDelivery(freshHost))
            assertEquals(2, resolutions)
            assertEquals(2, delivered.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun wrongNamespaceMappingNeverDrainsAnotherQueue() = runBlocking {
        val root = File(app.cacheDir, "logging-proof-${UUID.randomUUID()}")
        try {
            var delivered = false
            val host = RestoredHost(app) {
                EventLoggingWorkConfiguration(
                    config(root, "another-queue"),
                    EventLoggingGateway {
                        delivered = true
                        SdkResult.Success(Unit)
                    },
                    SdkEnvironment.Default,
                )
            }
            assertEquals(ListenableWorker.Result.failure(), worker(host).doWork())
            assertFalse(delivered)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun transientRestorationFailureRequestsRetry() = runBlocking {
        val host = RestoredHost(app) { throw IOException("temporary restore failure") }
        assertEquals(ListenableWorker.Result.retry(), worker(host).doWork())
    }

    @Test fun restorationCancellationPropagates() = runBlocking {
        val host = RestoredHost(app) { throw CancellationException("cancel restoration") }
        try {
            worker(host).doWork()
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
}
