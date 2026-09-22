import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Jar
import sdkbase.abi.ApiCheckTask
import sdkbase.abi.GenerateJvmApiDumpTask
import java.io.File

// ---------------------------------------------------------------------------------------------
// The JAR twin of sdkbase.abi.gradle.kts, for pure-Kotlin (`org.jetbrains.kotlin.jvm`) modules
// such as :sdk:core that produce a plain `.jar` via the `jar` task instead of an AAR via
// `assembleRelease`. Same task names (apiDump / apiCheck), same baseline format, same
// additive-only rule. The actual dump/check logic does not live here — see the KDoc on
// GenerateJvmApiDumpTask and ApiCheckTask in build-logic/src/main/kotlin/sdkbase/abi/AbiTasks.kt
// for the configuration-cache reasoning this plugin inherits unchanged from sdkbase.abi.
//
// Verified: the `jar` task's default output is NOT `${project.name}.jar`. Gradle names it
// `${archiveBaseName}-${archiveVersion}.jar` (:sdk:core produced
// build/libs/core-0.1.0-SNAPSHOT.jar). Reading the `jar` task's own `archiveFile` provider avoids
// hardcoding that filename and also lets Gradle infer the task dependency from the provider,
// rather than guessing a build-directory path the way sdkbase.abi guesses the AAR's path.
// ---------------------------------------------------------------------------------------------

val moduleNameValue = project.name
val jarTask = tasks.named<Jar>("jar")
val jarFileProvider = jarTask.flatMap { it.archiveFile }
val generatedApiFileProvider = layout.buildDirectory.file("api-compat/$moduleNameValue.api")
val extractDirProvider = layout.buildDirectory.dir("api-compat/classes")
val committedApiFile = layout.projectDirectory.file("api/$moduleNameValue.api")
val javapExecutablePath = File(System.getProperty("java.home"), "bin/javap").absolutePath

val generateApiDump = tasks.register<GenerateJvmApiDumpTask>("generateApiDump") {
    description = "Writes the release ABI of $moduleNameValue to a build-local .api file."
    group = "verification"
    dependsOn("jar")
    jarFile.set(jarFileProvider)
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
