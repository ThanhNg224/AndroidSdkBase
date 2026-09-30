package io.github.thanhng224.sdkbase.logging.file

import io.github.thanhng224.sdkbase.logging.file.error.FileLoggingErrors

import io.github.thanhng224.sdkbase.core.call.ResultCallback
import io.github.thanhng224.sdkbase.core.environment.SdkEnvironment
import io.github.thanhng224.sdkbase.core.error.SdkError
import io.github.thanhng224.sdkbase.core.error.SdkErrors
import io.github.thanhng224.sdkbase.core.logging.LogLevel
import io.github.thanhng224.sdkbase.core.logging.LogRecord
import io.github.thanhng224.sdkbase.core.logging.Redactor
import io.github.thanhng224.sdkbase.core.result.SdkResult
import io.github.thanhng224.sdkbase.core.testing.FakeClock
import io.github.thanhng224.sdkbase.core.testing.TestDispatcherProvider
import io.github.thanhng224.sdkbase.logging.file.config.FileLoggingConfig
import io.github.thanhng224.sdkbase.logging.file.session.FileLoggingSession
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FileLoggingTest {
    @get:Rule val temp = TemporaryFolder()
    private fun TestScope.environment(clock: FakeClock = FakeClock(System.currentTimeMillis())): SdkEnvironment =
        SdkEnvironment.Builder().dispatchers(TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
            .clock(clock).build()
    private suspend fun start(config: FileLoggingConfig.Builder, env: SdkEnvironment): SdkResult<FileLoggingSession> =
        when (val built = config.environment(env).build()) {
            is SdkResult.Failure -> built
            is SdkResult.Success -> FileLoggingSdk.start(built.value)
        }
    private fun record(text: String) = LogRecord(LogLevel.INFO, "sdk", text, null, 1, "test-session")
    private fun success(result: SdkResult<FileLoggingSession>) = (result as SdkResult.Success).value
    private fun logs(dir: File) = dir.listFiles()!!.filter { it.name.endsWith(".log") }

    @Test fun redactsBeforeDiskAndRotatesWithinByteLimit() = runTest {
        val dir = temp.newFolder()
        val config = FileLoggingConfig.Builder(dir).maxRecordBytes(256).maxFileBytes(512).maxFiles(2)
        val session = success(start(config, environment()))
        repeat(12) { session.write(record("token=supersecret person@example.com " + "x".repeat(200))) }
        assertTrue(session.flush() is SdkResult.Success)
        val files = logs(dir)
        assertEquals(2, files.size)
        assertTrue(files.all { it.length() <= 512 })
        val text = files.joinToString { it.readText() }
        assertFalse(text.contains("supersecret"))
        assertFalse(text.contains("person@example.com"))
        session.close()
        runCurrent()
    }

    @Test fun boundedAdmissionDropsWithoutLaunchingPerRecord() = runTest {
        val dir = temp.newFolder()
        val session =
            success(start(FileLoggingConfig.Builder(dir).queueCapacity(2), environment()))
        repeat(100) { session.write(record("event=$it")) }
        assertTrue(session.flush() is SdkResult.Success)
        assertEquals(2L, session.state.value.written)
        assertEquals(98L, session.state.value.dropped)
        session.close()
        runCurrent()
    }

    @Test fun redactorFailureDropsAndNeverWritesRawInput() = runTest {
        val dir = temp.newFolder()
        val config = FileLoggingConfig.Builder(dir).redactor(Redactor { throw IllegalStateException("bad") })
        val session = success(start(config, environment()))
        session.write(record("password=secret"))
        session.flush()
        assertEquals(1L, session.state.value.dropped)
        assertTrue(logs(dir).isEmpty())
        session.close()
        runCurrent()
    }

    @Test fun storageFailureIsObservableThroughFlush() = runTest {
        val dir = temp.newFolder()
        File(dir, "active.log").mkdir()
        val session = success(start(FileLoggingConfig.Builder(dir), environment()))
        session.write(record("data"))
        val result = session.flush() as SdkResult.Failure
        assertEquals(FileLoggingErrors.STORAGE_FAILURE, result.error.code)
        assertEquals(1L, session.state.value.storageFailures)
        session.close()
        runCurrent()
    }

    @Test fun exclusiveDirectoryLeaseReleasesEvenBeforeWriterExecutes() = runTest {
        val dir = temp.newFolder()
        val config = FileLoggingConfig.Builder(dir)
        val env = environment()
        val first = success(start(config, env))
        val busy = start(config, env) as SdkResult.Failure
        assertEquals(FileLoggingErrors.DIRECTORY_IN_USE, busy.error.code)
        first.close()
        runCurrent()
        val next = success(start(config, env))
        next.close()
        runCurrent()
    }

    @Test fun closeStopsWritesAndCallbackReturnsClosed() = runTest {
        val dir = temp.newFolder()
        val session = success(start(FileLoggingConfig.Builder(dir), environment()))
        session.close()
        runCurrent()
        session.write(record("late"))
        var failure: SdkError? = null
        session.flush(object : ResultCallback<Unit> {
            override fun onSuccess(value: Unit) {
                fail("closed session flushed")
            }
            override fun onFailure(error: SdkError) {
                failure = error
            }
        })
        runCurrent()
        assertEquals(SdkErrors.SESSION_CLOSED, failure!!.code)
        assertTrue(logs(dir).isEmpty())
    }

    @Test fun retentionRemovesExpiredOwnedFilesOnly() = runTest {
        val dir = temp.newFolder()
        val old = File(dir, "1.log").apply {
            writeText("old")
            setLastModified(1000)
        }
        val host = File(dir, "host.txt").apply {
            writeText("keep")
            setLastModified(1000)
        }
        val session =
            success(start(FileLoggingConfig.Builder(dir).retentionMillis(1000), environment()))
        session.write(record("new"))
        session.flush()
        assertFalse(old.exists())
        assertTrue(host.exists())
        session.close()
        runCurrent()
    }

    @Test fun unicodeRecordsAreByteBounded() = runTest {
        val dir = temp.newFolder()
        val session = success(
            start(
                FileLoggingConfig.Builder(dir).maxRecordBytes(256)
                    .maxFileBytes(256),
                environment(),
            ),
        )
        session.write(record("漢字🙂".repeat(5000)))
        session.flush()
        assertTrue(File(dir, "active.log").length() <= 256)
        session.close()
        runCurrent()
    }

    @Test fun optInCrashCaptureRedactsBoundsDelegatesAndRecoversAfterRestart() = runTest {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        val dir = temp.newFolder()
        var delegated: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, error -> delegated = error }
        val config = FileLoggingConfig.Builder(dir).captureCrashes(true).maxCrashFiles(2).maxRecordBytes(512)
        val env = environment()
        var session: FileLoggingSession? = null
        try {
            session = success(start(config, env))
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            repeat(4) {
                val crash = IllegalStateException("password=supersecret person@example.com")
                crash.stackTrace = arrayOf(StackTraceElement("io.github.thanhng224.sdkbase.Demo", "go", "Demo.kt", 1))
                handler.uncaughtException(Thread.currentThread(), crash)
                assertSame(crash, delegated)
            }
            val reports = (session.pendingCrashes() as SdkResult.Success).value
            assertEquals(2, reports.size)
            assertTrue(reports.all { !it.text.contains("supersecret") && !it.text.contains("person@example.com") })
            assertEquals(
                FileLoggingErrors.INVALID_CRASH_ID,
                (session.acknowledgeCrash("../host.txt") as SdkResult.Failure).error.code,
            )
            session.close()
            runCurrent()
            session = success(start(FileLoggingConfig.Builder(dir), env))
            val recovered = (session.pendingCrashes() as SdkResult.Success).value
            assertEquals(2, recovered.size)
            session.acknowledgeCrash(recovered.first().id)
            assertEquals(1, (session.pendingCrashes() as SdkResult.Success).value.size)
        } finally {
            session?.close()
            runCurrent()
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun crashCaptureFiltersHostErrorsAndPreservesReplacementHandler() = runTest {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        val dir = temp.newFolder()
        var calls = 0
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> calls++ }
        var session: FileLoggingSession? = null
        try {
            session =
                success(
                    start(FileLoggingConfig.Builder(dir).captureCrashes(true), environment()),
                )
            val handler = Thread.getDefaultUncaughtExceptionHandler()!!
            val hostError = IllegalArgumentException("host")
            hostError.stackTrace = arrayOf(StackTraceElement("com.host.Application", "go", "App.kt", 1))
            handler.uncaughtException(Thread.currentThread(), hostError)
            assertEquals(1, calls)
            assertTrue((session.pendingCrashes() as SdkResult.Success).value.isEmpty())
            val replacement = Thread.UncaughtExceptionHandler { _, _ -> }
            Thread.setDefaultUncaughtExceptionHandler(replacement)
            session.close()
            runCurrent()
            assertSame(replacement, Thread.getDefaultUncaughtExceptionHandler())
            val sdkCrash = IllegalStateException("late")
            sdkCrash.stackTrace = arrayOf(StackTraceElement("io.github.thanhng224.sdkbase.Late", "go", "Late.kt", 1))
            handler.uncaughtException(Thread.currentThread(), sdkCrash)
            assertTrue(dir.listFiles()!!.none { it.name.startsWith("crash-") })
        } finally {
            session?.close()
            runCurrent()
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun crashRedactorFailureStillDelegates() = runTest {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        var calls = 0
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> calls++ }
        var session: FileLoggingSession? = null
        try {
            val dir = temp.newFolder()
            val config = FileLoggingConfig.Builder(dir).captureCrashes(true)
                .redactor(Redactor { throw IllegalStateException("failed") })
            session = success(start(config, environment()))
            val crash = IllegalStateException("token=secret")
            crash.stackTrace = arrayOf(StackTraceElement("io.github.thanhng224.sdkbase.Demo", "go", "Demo.kt", 1))
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)
            assertEquals(1, calls)
            assertTrue((session.pendingCrashes() as SdkResult.Success).value.isEmpty())
        } finally {
            session?.close()
            runCurrent()
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun restartEnforcesReducedBoundsAndRecoversCommittedPendingCrash() = runTest {
        val dir = temp.newFolder()
        File(dir, "9.log").writeText("old")
        File(dir, "active.log").writeText("x".repeat(600))
        File(dir, "crash.pending").writeText("redacted crash")
        val session = success(
            start(
                FileLoggingConfig.Builder(dir).maxFiles(1)
                    .maxFileBytes(512).maxRecordBytes(256).maxCrashFiles(1),
                environment(),
            ),
        )
        assertFalse(File(dir, "9.log").exists())
        assertFalse(File(dir, "active.log").exists())
        assertFalse(File(dir, "crash.pending").exists())
        assertEquals("redacted crash", (session.pendingCrashes() as SdkResult.Success).value.single().text)
        session.close()
        runCurrent()
    }

    @Test fun throwableCauseAndSuppressedAreRedactedBeforeDisk() = runTest {
        val dir = temp.newFolder()
        val session = success(start(FileLoggingConfig.Builder(dir), environment()))
        val error = IllegalStateException("outer", IllegalArgumentException("password=cause-secret"))
        error.addSuppressed(IllegalArgumentException("token=suppressed-secret"))
        session.write(LogRecord(LogLevel.ERROR, "sdk", "failure", error, 1, null))
        session.flush()
        val text = logs(dir).single().readText()
        assertTrue(text.contains("password="))
        assertTrue(text.contains("token="))
        assertFalse(text.contains("cause-secret"))
        assertFalse(text.contains("suppressed-secret"))
        session.close()
        runCurrent()
    }

    @Test fun customIdentityRedactorCannotDisableBaselinePrivacy() = runTest {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        val dir = temp.newFolder()
        var session: FileLoggingSession? = null
        try {
            session = success(
                start(
                    FileLoggingConfig.Builder(dir).captureCrashes(true)
                        .redactor(Redactor { it }),
                    environment(),
                ),
            )
            session.write(record("password=supersecret person@example.com"))
            session.flush()
            val crash = IllegalStateException("token=crashsecret person@example.com")
            crash.stackTrace = arrayOf(StackTraceElement("io.github.thanhng224.sdkbase.Demo", "go", "Demo.kt", 1))
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)
            val text =
                logs(dir).single().readText() + (session.pendingCrashes() as SdkResult.Success).value.single().text
            assertFalse(text.contains("supersecret"))
            assertFalse(text.contains("crashsecret"))
            assertFalse(text.contains("person@example.com"))
        } finally {
            session?.close()
            runCurrent()
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun invalidConfigDoesNotCreateDirectory() = runTest {
        val dir = File(temp.root, "absent")
        val result = start(FileLoggingConfig.Builder(dir).maxFiles(0), environment())
        assertEquals(SdkErrors.INVALID_CONFIG, (result as SdkResult.Failure).error.code)
        assertFalse(dir.exists())
    }
}
