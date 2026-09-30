package io.github.thanhng224.sdkbase.core.testing

import org.junit.Assert.assertThrows
import org.junit.Test

class ValueSemanticsAssertionsTest {

    private class Good(val a: Int, val b: String) {
        override fun equals(other: Any?): Boolean = other is Good && a == other.a && b == other.b
        override fun hashCode(): Int = 31 * a + b.hashCode()
        override fun toString(): String = "Good($a, $b)"
    }

    private class ForgotsBInEquals(val a: Int, val b: String) {
        override fun equals(other: Any?): Boolean = other is ForgotsBInEquals && a == other.a
        override fun hashCode(): Int = 31 * a + b.hashCode()
        override fun toString(): String = "F($a, $b)"
    }

    private class ForgotsBInHashCode(val a: Int, val b: String) {
        override fun equals(other: Any?): Boolean = other is ForgotsBInHashCode && a == other.a && b == other.b
        override fun hashCode(): Int = a
        override fun toString(): String = "F($a, $b)"
    }

    private class Unstable(val a: Int) {
        override fun equals(other: Any?): Boolean = other is Unstable
        override fun hashCode(): Int = System.nanoTime().toInt()
        override fun toString(): String = "U"
    }

    @Test
    fun acceptsAWellFormedValueClass() {
        assertValueSemantics({ Good(1, "x") }, Good(2, "x"), Good(1, "y"))
    }

    @Test
    fun rejectsAPropertyMissingFromEquals() {
        assertThrows(AssertionError::class.java) {
            assertValueSemantics({ ForgotsBInEquals(1, "x") }, ForgotsBInEquals(1, "y"))
        }
    }

    @Test
    fun rejectsAPropertyMissingFromHashCode() {
        assertThrows(AssertionError::class.java) {
            assertValueSemantics({ ForgotsBInHashCode(1, "x") }, ForgotsBInHashCode(1, "y"))
        }
    }

    @Test
    fun rejectsEqualInstancesWithDifferentHashCodes() {
        assertThrows(AssertionError::class.java) { assertValueSemantics({ Unstable(1) }) }
    }
}
