package io.github.thanhng224.consumer.headless

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConfigConsumerTest {
    @Test
    fun kotlinSuspendFetchUsesPublishedCoordinates() = runBlocking {
        val result = RemoteConfigKotlinConsumer.fetch()
        assertEquals("kotlin-v1", result.revision)
        assertEquals("dark", result.values["theme"])
    }

    @Test
    fun javaBuilderAndCallbackUsePublishedCoordinates() {
        val result = RemoteConfigJavaConsumer.fetch()
        assertEquals("java-v1", result.revision)
        assertEquals("light", result.values["theme"])
    }
}
