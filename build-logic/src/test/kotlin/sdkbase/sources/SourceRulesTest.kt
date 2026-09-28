package sdkbase.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRulesTest {

    @Test
    fun globalScopeIsFlagged() {
        val text = """
            |package test
            |
            |fun run() {
            |    GlobalScope.launch { }
            |}
            |
        """.trimMargin()

        val violation = findViolations("Foo.kt", text, "feature").single()

        assertEquals("global-scope", violation.ruleId)
        assertEquals(4, violation.line)
    }

    @Test
    fun androidLogIsFlagged() {
        val text = """
            |package test
            |
            |import android.util.Log
            |
            |fun run() {
            |}
            |
        """.trimMargin()

        val violation = findViolations("Foo.kt", text, "feature").single()

        assertEquals("android-log", violation.ruleId)
        assertEquals(3, violation.line)
    }

    @Test
    fun ownCoroutineScopeIsFlagged() {
        val text = """
            |package test
            |
            |class Runner {
            |    val scope = CoroutineScope(Job())
            |}
            |
        """.trimMargin()

        val violation = findViolations("Runner.kt", text, "feature").single()

        assertEquals("own-coroutine-scope", violation.ruleId)
        assertEquals(4, violation.line)
    }

    @Test
    fun publicDataClassIsFlagged() {
        val text = """
            |package test
            |
            |public data class Point(public val x: Int, public val y: Int)
            |
        """.trimMargin()

        val violation = findViolations("Point.kt", text, "core").single()

        assertEquals("public-data-class", violation.ruleId)
        assertEquals(3, violation.line)
    }

    @Test
    fun multiLineDataClassIsCounted() {
        val text = """
            |package test
            |
            |public data class Config(
            |    public val host: String,
            |    public val port: Int,
            |    public val timeoutMillis: Long,
            |)
            |
        """.trimMargin()

        val violation = findViolations("Config.kt", text, "core").single()

        assertEquals("public-data-class", violation.ruleId)
        assertEquals(3, violation.line)
        assertTrue(violation.message.contains("3 properties"))
    }

    @Test
    fun mentionsInCommentsAndStringsAreIgnored() {
        val text = """
            |package test
            |
            |/**
            | * GlobalScope should never be used; see [GlobalScope].
            | */
            |fun run() {
            |    // GlobalScope.launch { }
            |    val message = "GlobalScope is banned, android.util.Log too, CoroutineScope(Job())"
            |}
            |
        """.trimMargin()

        assertTrue(findViolations("Foo.kt", text, "feature").isEmpty())
    }

    @Test
    fun logcatSinkInCoreIsAllowed() {
        val text = """
            |package test
            |
            |import android.util.Log
            |
            |fun run() { Log.d("x") }
            |
        """.trimMargin()

        assertTrue(findViolations("LogcatSink.kt", text, "core").isEmpty())
    }

    @Test
    fun coroutineScopeInCoreIsAllowed() {
        val text = """
            |package test
            |
            |class Runner {
            |    val scope = CoroutineScope(Job())
            |}
            |
        """.trimMargin()

        assertTrue(findViolations("Runner.kt", text, "core").isEmpty())
    }

    @Test
    fun singlePropertyDataClassIsAllowed() {
        val text = """
            |package test
            |
            |public data class Success<out T>(public val value: T)
            |
        """.trimMargin()

        assertTrue(findViolations("SdkResult.kt", text, "core").isEmpty())
    }

    @Test
    fun internalDataClassIsAllowed() {
        val text = """
            |package test
            |
            |internal data class Config(val host: String, val port: Int)
            |
        """.trimMargin()

        assertTrue(findViolations("Config.kt", text, "feature").isEmpty())
    }
}
