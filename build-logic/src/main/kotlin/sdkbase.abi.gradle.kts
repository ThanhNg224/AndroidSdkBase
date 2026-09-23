import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.accessors.dm.LibrariesForLibs
import sdkbase.abi.CheckAbiTask
import sdkbase.abi.DumpAbiTask

// apiDump / apiCheck for Android (release AAR) modules. Exact match: every change to
// the public surface shows up as a reviewed diff of api/<name>.api.
val libs = the<LibrariesForLibs>()

val abiTools = configurations.create("sdkbaseAbiTools") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies { add(abiTools.name, libs.abi.tools) }

// Named apiFileName, not baselineName: CheckAbiTask has its own `baselineName` property, which
// would otherwise shadow this local val inside the `tasks.register<CheckAbiTask>` lambda below and
// turn `baselineName.set(baselineName)` into a circular self-reference ("Circular evaluation
// detected... property 'baselineName' -> ... property 'baselineName'").
val apiFileName = "${project.name}.api"
val baselineFile = layout.projectDirectory.file("api/$apiFileName")

val dumpAbi = tasks.register<DumpAbiTask>("dumpAbi") {
    abiToolsClasspath.from(abiTools)
    dumpFile.set(layout.buildDirectory.file("abi/$apiFileName"))
}

tasks.register<Copy>("apiDump") {
    group = "verification"
    description = "Records the public ABI into api/$apiFileName."
    from(dumpAbi.flatMap { it.dumpFile })
    into(layout.projectDirectory.dir("api"))
}

val apiCheck = tasks.register<CheckAbiTask>("apiCheck") {
    group = "verification"
    description = "Fails if the public ABI differs from api/$apiFileName."
    baseline.from(baselineFile)
    current.set(dumpAbi.flatMap { it.dumpFile })
    projectPath.set(project.path)
    baselineName.set(apiFileName)
    result.set(layout.buildDirectory.file("abi/apiCheck.ok"))
}

tasks.named("check") { dependsOn(apiCheck) }

// AndroidComponentsExtension#onVariants only overloads on a VariantSelector, not a lambda
// predicate: `.onVariants({ it.buildType == "release" })` does not compile against AGP 9.4.1.
plugins.withId("com.android.library") {
    val components = extensions.getByType(LibraryAndroidComponentsExtension::class.java)
    components.onVariants(components.selector().withBuildType("release")) { variant ->
        dumpAbi.configure { artifact.set(variant.artifacts.get(SingleArtifact.AAR)) }
    }
}
