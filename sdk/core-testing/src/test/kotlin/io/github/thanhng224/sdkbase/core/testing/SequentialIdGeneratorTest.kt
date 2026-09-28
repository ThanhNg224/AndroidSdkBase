package io.github.thanhng224.sdkbase.core.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class SequentialIdGeneratorTest {

    @Test
    fun `ids are sequential and prefixed`() {
        val generator = SequentialIdGenerator("s-")

        assertEquals("s-1", generator.newId())
        assertEquals("s-2", generator.newId())
    }

    @Test
    fun `the no-arg constructor uses the default prefix`() {
        assertEquals("id-1", SequentialIdGenerator().newId())
    }
}
