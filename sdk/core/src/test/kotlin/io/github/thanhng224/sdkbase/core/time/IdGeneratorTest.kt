package io.github.thanhng224.sdkbase.core.time

import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdGeneratorTest {

    @Test
    fun `Uuid generator produces distinct ids`() {
        val first = IdGenerator.Uuid.newId()
        val second = IdGenerator.Uuid.newId()
        assertNotEquals(first, second)
    }
}
