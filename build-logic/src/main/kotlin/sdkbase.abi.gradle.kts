import org.gradle.api.tasks.PathSensitivity
import sdkbase.abi.ApiCheckTask
import sdkbase.abi.GenerateApiDumpTask
import java.io.File

// ---------------------------------------------------------------------------------------------
// This plugin only wires plain values into two real task types (sdkbase.abi.GenerateApiDumpTask
// and sdkbase.abi.ApiCheckTask, in build-logic/src/main/kotlin/sdkbase/abi/AbiTasks.kt).
//
// The actual dump/check logic does NOT live here as a `doLast { }` closure, and is NOT declared
// as a class nested inside this `.gradle.kts` file either — both were tried while building this
// plugin and both broke the configuration cache. See the KDoc on GenerateApiDumpTask for the two
// verified failures. What remains here is safe: assigning to a task's `Property`/`Provider`
// inputs at configuration time is plain, natively-supported Gradle API, never a stored closure.
// ---------------------------------------------------------------------------------------------

val moduleNameValue = project.name
val aarFileProvider = layout.buildDirectory.file("outputs/aar/$moduleNameValue-release.aar")
val generatedApiFileProvider = layout.buildDirectory.file("api-compat/$moduleNameValue.api")
val extractDirProvider = layout.buildDirectory.dir("api-compat/classes")
val committedApiFile = layout.projectDirectory.file("api/$moduleNameValue.api")
val javapExecutablePath = File(System.getProperty("java.home"), "bin/javap").absolutePath

val generateApiDump = tasks.register<GenerateApiDumpTask>("generateApiDump") {
    description = "Writes the release ABI of $moduleNameValue to a build-local .api file."
    group = "verification"
    dependsOn("assembleRelease")
    aarFile.set(aarFileProvider)
    generatedApiFile.set(generatedApiFileProvider)
    extractDir.set(extractDirProvider)
    javapExecutable.set(javapExecutablePath)
    moduleName.set(moduleNameValue)
}

tasks.register<Copy>("apiDump") {
    description = "Records the current release ABI of $moduleNameValue into api/$moduleNameValue.api."
    group = "verification"
    from(generateApiDump)
    into(committedApiFile.asFile.parentFile)
}

val apiCheck = tasks.register<ApiCheckTask>("apiCheck") {
    description = "Fails if a recorded public signature of $moduleNameValue was removed or changed."
    group = "verification"
    currentFile.set(generatedApiFileProvider)
    baselineFile.set(committedApiFile)
    moduleName.set(moduleNameValue)
    inputs.files(generateApiDump)
    inputs.file(committedApiFile).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { true }
}

tasks.named("check") { dependsOn(apiCheck) }
