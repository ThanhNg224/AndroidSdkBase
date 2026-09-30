package {{pkg}}.config

import {{ns}}.core.testing.assertSuccess
import {{pkg}}.gateway.{{pascal}}Gateway
import org.junit.Assert.assertNotNull
import org.junit.Test

class {{pascal}}SdkConfigTest {

    private val gateway = object : {{pascal}}Gateway {}

    @Test
    fun buildSucceedsWithDefaults() {
        val config = {{pascal}}SdkConfig.Builder(gateway).build().assertSuccess()

        assertNotNull(config)
    }
}
