package sdkbase.abi

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

// A class is public API unless it is Kotlin-internal by package convention or a compiler synthetic.
private fun isPublicApiClass(binaryName: String): Boolean {
    if (binaryName.contains("/internal/")) return false
    val simpleName = binaryName.substringAfterLast('/')
    val nestedSegments = simpleName.split('$').drop(1)
    return nestedSegments.none { segment ->
        segment.matches(Regex("[0-9]+")) ||
            segment in setOf("WhenMappings", "DefaultImpls") ||
            segment.contains("ExternalSyntheticLambda") ||
            segment.startsWith("sam\$")
    }
}

// The fully-qualified class name a javap header line declares, e.g. "io.foo.Bar$Companion" out of
// "public final class io.foo.Bar$Companion {" — used to key kotlinPublicApiFilter's per-line check.
private val CLASS_NAME_IN_HEADER = Regex("""(?:class|interface)\s+([\w$.]+)""")

private fun isMangledMember(line: String): Boolean =
    line.contains(Regex("\\baccess\\$[A-Za-z0-9_$]*\\(")) ||
        line.contains("\$default(") ||
        line.contains("\$annotations(") ||
        line.contains(Regex("\\s[A-Za-z_][A-Za-z0-9_]*\\$[A-Za-z0-9_]+\\("))

/**
 * Extracts the release AAR's `classes.jar`, `javap`-dumps every public API class and writes a
 * normalized `.api` file.
 *
 * This lives in a plain `.kt` file — not inline as a `doLast { }` closure in the `sdkbase.abi`
 * precompiled script plugin, and not even as a nested class declared *inside* that `.gradle.kts`
 * file. Both were tried and both failed:
 *  - A closure written directly inside `tasks.register("x") { doLast { ... } }` in the script
 *    retains a `this$0` reference to its enclosing script object. The configuration cache refuses
 *    to serialize that ("cannot serialize Gradle script object references") — verified by running
 *    `:sdk:platform:apiDump`, which failed configuration-cache storage tracing through a `this$0`
 *    field on the generated `...$generateApiDump$1$1` class.
 *  - A task class declared as a top-level `class` *inside* the `.gradle.kts` file still compiles
 *    as a non-static JVM inner class of the script class (Gradle's script compiler nests every
 *    declaration in the file under the generated script class) — verified: `tasks.register<...>`
 *    then failed with "Class Sdkbase_abi_gradle.GenerateApiDumpTask is a non-static inner class."
 *
 * A real top-level class in an ordinary `.kt` source file has no enclosing instance at all: its
 * only state is the plain `Property`/`Provider` inputs declared below, which the configuration
 * cache supports natively.
 */
public abstract class GenerateApiDumpTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val aarFile: RegularFileProperty

    @get:OutputFile
    public abstract val generatedApiFile: RegularFileProperty

    @get:Internal
    public abstract val extractDir: DirectoryProperty

    @get:Internal
    public abstract val javapExecutable: Property<String>

    @get:Internal
    public abstract val moduleName: Property<String>

    @TaskAction
    public fun generate() {
        val aarAsFile = aarFile.get().asFile
        val root = extractDir.get().asFile
        val javap = javapExecutable.get()
        val name = moduleName.get()

        root.deleteRecursively()
        root.mkdirs()

        val classesJar = File(root, "classes.jar")
        ZipFile(aarAsFile).use { zip ->
            val entry = zip.getEntry("classes.jar")
                ?: throw GradleException("${aarAsFile.name} contains no classes.jar")
            zip.getInputStream(entry).use { input ->
                classesJar.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val classNames = mutableListOf<String>()
        ZipInputStream(classesJar.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.name.endsWith(".class")) continue
                val binaryName = entry.name.removeSuffix(".class")
                if (isPublicApiClass(binaryName)) {
                    classNames += binaryName.replace('/', '.')
                }
                val target = File(root, entry.name)
                target.parentFile.mkdirs()
                target.outputStream().use { output -> zip.copyTo(output) }
            }
        }
        classNames.sort()

        if (classNames.isEmpty()) {
            throw GradleException(
                "No public API classes found in ${aarAsFile.name}; refusing to write an empty baseline"
            )
        }

        val process = ProcessBuilder(
            listOf(javap, "-protected", "-classpath", root.absolutePath) + classNames
        ).redirectErrorStream(true).start()
        val javapOutput = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            throw GradleException("javap failed for $name:\n$javapOutput")
        }

        // javap emits a flat block per class: a header line ending in `{`, indented members, `}`.
        // Members are sorted so a compiler reordering never reads as an API change. In addition to
        // the package-convention/synthetic-name filters above, kotlinPublicApiFilter reads each
        // class's own kotlin.Metadata to drop a member javap reports as JVM-public but Kotlin
        // recorded as internal/private — see KotlinVisibility.kt.
        val isKotlinPublic = kotlinPublicApiFilter(root)
        val blocks = linkedMapOf<String, List<String>>()
        var header: String? = null
        var currentClassName: String? = null
        var members = mutableListOf<String>()
        javapOutput.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("Compiled from") -> Unit
                line.endsWith("{") -> {
                    header = line
                    currentClassName = CLASS_NAME_IN_HEADER.find(line)?.groupValues?.get(1)
                    members = mutableListOf()
                }
                line == "}" -> {
                    header?.let { blocks[it] = members.sorted() }
                    header = null
                    currentClassName = null
                }
                header != null && !isMangledMember(line) &&
                    (currentClassName == null || isKotlinPublic(currentClassName!!, line)) -> members += line
            }
        }

        val rendered = StringBuilder()
        rendered.append("# Public ABI baseline for :$name — see docs/COMPATIBILITY.md.\n")
        rendered.append("# Regenerate with ./gradlew :$name:apiDump after an ADDITIVE change.\n")
        rendered.append("# Removing or changing a recorded signature is breaking and fails apiCheck.\n")
        blocks.keys.sorted().forEach { key ->
            rendered.append("\n$key\n")
            blocks.getValue(key).forEach { member -> rendered.append("    $member\n") }
            rendered.append("}\n")
        }

        val outFile = generatedApiFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(rendered.toString())
    }
}

/**
 * The JAR twin of [GenerateApiDumpTask], for pure-Kotlin (`org.jetbrains.kotlin.jvm`) modules such
 * as `:sdk:core` that publish a plain `.jar` instead of an AAR. There is no `classes.jar` wrapper
 * to unzip first, so this reads the module's own jar directly with `ZipInputStream`; everything
 * else — the public-API filtering, the `javap` dump and the normalized rendering — is identical to
 * [GenerateApiDumpTask].
 *
 * Lives in this plain `.kt` file as a real top-level `abstract class ... : DefaultTask()` for the
 * same configuration-cache reason documented on [GenerateApiDumpTask]: a `doLast { }` closure or a
 * class nested inside a `.gradle.kts` script cannot be serialized by the configuration cache.
 */
public abstract class GenerateJvmApiDumpTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val jarFile: RegularFileProperty

    @get:OutputFile
    public abstract val generatedApiFile: RegularFileProperty

    @get:Internal
    public abstract val extractDir: DirectoryProperty

    @get:Internal
    public abstract val javapExecutable: Property<String>

    @get:Internal
    public abstract val moduleName: Property<String>

    @TaskAction
    public fun generate() {
        val jarAsFile = jarFile.get().asFile
        val root = extractDir.get().asFile
        val javap = javapExecutable.get()
        val name = moduleName.get()

        root.deleteRecursively()
        root.mkdirs()

        val classNames = mutableListOf<String>()
        ZipInputStream(jarAsFile.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.name.endsWith(".class")) continue
                val binaryName = entry.name.removeSuffix(".class")
                if (isPublicApiClass(binaryName)) {
                    classNames += binaryName.replace('/', '.')
                }
                val target = File(root, entry.name)
                target.parentFile.mkdirs()
                target.outputStream().use { output -> zip.copyTo(output) }
            }
        }
        classNames.sort()

        if (classNames.isEmpty()) {
            throw GradleException(
                "No public API classes found in ${jarAsFile.name}; refusing to write an empty baseline"
            )
        }

        val process = ProcessBuilder(
            listOf(javap, "-protected", "-classpath", root.absolutePath) + classNames
        ).redirectErrorStream(true).start()
        val javapOutput = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            throw GradleException("javap failed for $name:\n$javapOutput")
        }

        // javap emits a flat block per class: a header line ending in `{`, indented members, `}`.
        // Members are sorted so a compiler reordering never reads as an API change. In addition to
        // the package-convention/synthetic-name filters above, kotlinPublicApiFilter reads each
        // class's own kotlin.Metadata to drop a member javap reports as JVM-public but Kotlin
        // recorded as internal/private — see KotlinVisibility.kt.
        val isKotlinPublic = kotlinPublicApiFilter(root)
        val blocks = linkedMapOf<String, List<String>>()
        var header: String? = null
        var currentClassName: String? = null
        var members = mutableListOf<String>()
        javapOutput.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("Compiled from") -> Unit
                line.endsWith("{") -> {
                    header = line
                    currentClassName = CLASS_NAME_IN_HEADER.find(line)?.groupValues?.get(1)
                    members = mutableListOf()
                }
                line == "}" -> {
                    header?.let { blocks[it] = members.sorted() }
                    header = null
                    currentClassName = null
                }
                header != null && !isMangledMember(line) &&
                    (currentClassName == null || isKotlinPublic(currentClassName!!, line)) -> members += line
            }
        }

        val rendered = StringBuilder()
        rendered.append("# Public ABI baseline for :$name — see docs/COMPATIBILITY.md.\n")
        rendered.append("# Regenerate with ./gradlew :$name:apiDump after an ADDITIVE change.\n")
        rendered.append("# Removing or changing a recorded signature is breaking and fails apiCheck.\n")
        blocks.keys.sorted().forEach { key ->
            rendered.append("\n$key\n")
            blocks.getValue(key).forEach { member -> rendered.append("    $member\n") }
            rendered.append("}\n")
        }

        val outFile = generatedApiFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(rendered.toString())
    }
}

/**
 * Fails the build if a signature recorded in the committed baseline is missing from the freshly
 * generated dump. Additions are always allowed — the asymmetry is the policy. Lives in this plain
 * `.kt` file for the same configuration-cache reason as [GenerateApiDumpTask].
 *
 * Artifact-agnostic by construction — it only compares two `.api` text files — so it is reused
 * as-is by both [GenerateApiDumpTask] (AAR-backed modules) and [GenerateJvmApiDumpTask]
 * (JAR-backed modules) instead of being duplicated.
 */
public abstract class ApiCheckTask : DefaultTask() {

    @get:Internal
    public abstract val currentFile: RegularFileProperty

    @get:Internal
    public abstract val baselineFile: RegularFileProperty

    @get:Internal
    public abstract val moduleName: Property<String>

    @TaskAction
    public fun check() {
        val baseline = baselineFile.get().asFile
        val name = moduleName.get()
        if (!baseline.exists()) {
            throw GradleException(
                "Missing API baseline ${baseline.path}. Create it with ./gradlew :$name:apiDump"
            )
        }

        fun significantLines(file: File): List<String> =
            file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

        val recorded = significantLines(baseline)
        val current = significantLines(currentFile.get().asFile).toSet()
        val removed = recorded.filterNot { it in current }

        if (removed.isNotEmpty()) {
            throw GradleException(
                "Additive-only contract violation in :$name (docs/COMPATIBILITY.md).\n" +
                    "These published signatures were removed or changed:\n" +
                    removed.joinToString("\n") { "  - $it" } + "\n\n" +
                    "Adding API is always allowed. If this removal is a deliberate breaking change held " +
                    "for a planned major release, update api/$name.api in the same commit and say so " +
                    "in the commit message."
            )
        }
    }
}
