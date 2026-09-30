package io.github.thanhng224.sdkbase.remoteconfig.snapshot

import io.github.thanhng224.sdkbase.core.testing.assertValueSemantics
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteConfigTest {
    @Test
    fun comparesEveryProperty() {
        assertValueSemantics(
            { RemoteConfig("v1", mapOf("theme" to "dark")) },
            RemoteConfig("v2", mapOf("theme" to "dark")),
            RemoteConfig("v1", mapOf("theme" to "light")),
        )
    }

    @Test
    fun copiesHostValues() {
        val source = mutableMapOf("theme" to "dark")
        val snapshot = RemoteConfig("v1", source)
        source["theme"] = "light"
        assertEquals("dark", snapshot.values["theme"])
    }

    @Test(expected = UnsupportedOperationException::class)
    fun javaCannotMutateSnapshotValues() {
        val snapshot = RemoteConfig("v1", mapOf("theme" to "dark"))
        (snapshot.values as MutableMap)["theme"] = "light"
    }
}
