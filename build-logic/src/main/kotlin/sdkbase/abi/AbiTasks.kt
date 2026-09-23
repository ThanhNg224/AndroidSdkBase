package sdkbase.abi

import java.io.File
import java.util.ServiceLoader
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.abi.tools.AbiFilters
import org.jetbrains.kotlin.abi.tools.AbiToolsFactory

/** Dumps the public ABI of a release `.aar` or `.jar` with the abi-tools engine, in BCV format. */
@CacheableTask
public abstract class DumpAbiTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val artifact: RegularFileProperty

    @get:Classpath
    public abstract val abiToolsClasspath: ConfigurableFileCollection

    @get:OutputFile
    public abstract val dumpFile: RegularFileProperty

    @get:Inject
    protected abstract val workers: WorkerExecutor

    @TaskAction
    public fun dump() {
        // Isolated classloader: abi-tools brings its own kotlin-stdlib, which must not meet Gradle's.
        workers.classLoaderIsolation { classpath.from(abiToolsClasspath) }
            .submit(DumpAbiAction::class.java) {
                artifact.set(this@DumpAbiTask.artifact)
                dumpFile.set(this@DumpAbiTask.dumpFile)
                scratchDir.set(temporaryDir)
            }
    }
}

public interface DumpAbiParameters : WorkParameters {
    public val artifact: RegularFileProperty
    public val dumpFile: RegularFileProperty
    public val scratchDir: DirectoryProperty
}

// Compose's ComposableSingletons hold lambdas named by source hash: not host-callable, and they would
// churn the baseline on every UI edit. Nested classes are written Outer.Inner in abi-tools filters.
private val EXCLUDE_COMPILER_SYNTHETICS =
    AbiFilters(emptySet(), setOf("**.ComposableSingletons.*"), emptySet(), emptySet())

public abstract class DumpAbiAction : WorkAction<DumpAbiParameters> {
    override fun execute() {
        val input = parameters.artifact.get().asFile
        val classes = if (input.extension == "aar") extractClassesJar(input) else input
        val tools = ServiceLoader.load(AbiToolsFactory::class.java, javaClass.classLoader).first().get()
        val dump = StringBuilder()
        tools.printJvmDump(dump, listOf(classes), EXCLUDE_COMPILER_SYNTHETICS)
        if (dump.isBlank()) throw GradleException("abi-tools found no public API in ${input.name}")
        parameters.dumpFile.get().asFile.apply { parentFile.mkdirs() }.writeText(dump.toString())
    }

    private fun extractClassesJar(aar: File): File {
        val target = File(parameters.scratchDir.get().asFile, "classes.jar")
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("classes.jar") ?: throw GradleException("${aar.name} has no classes.jar")
            zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        return target
    }
}

/** Fails on ANY difference between the committed baseline and the current dump. */
public abstract class CheckAbiTask : DefaultTask() {
    // InputFiles, not InputFile: a missing baseline must reach our message, not Gradle's validation error.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baseline: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val current: RegularFileProperty

    @get:Input
    public abstract val projectPath: Property<String>

    @get:Input
    public abstract val baselineName: Property<String>

    @get:OutputFile
    public abstract val result: RegularFileProperty

    @TaskAction
    public fun check() {
        val baselineFile = baseline.singleFile
        val path = projectPath.get()
        if (!baselineFile.exists()) {
            throw GradleException("Missing ABI baseline api/${baselineName.get()}. Run ./gradlew $path:apiDump and commit it.")
        }
        val expected = baselineFile.readText().replace("\r\n", "\n").trimEnd().lines()
        val actual = current.get().asFile.readText().trimEnd().lines()
        if (expected != actual) {
            throw GradleException(
                "Public ABI of $path differs from api/${baselineName.get()}:\n" +
                    lineDiff(expected, actual).joinToString("\n") { "  $it" } + "\n\n" +
                    "Removing or changing a line is a breaking change; so is adding an abstract member to an " +
                    "interface hosts implement, or a subtype to a sealed type (docs/COMPATIBILITY.md).\n" +
                    "If the change is intended, run ./gradlew $path:apiDump and commit the new baseline with it."
            )
        }
        result.get().asFile.writeText("ok\n")
    }
}
