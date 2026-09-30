import com.android.build.api.variant.LibraryAndroidComponentsExtension
import sdkbase.dependencies.CheckDependencyPolicyTask

// checkDependencyPolicy: the release classpaths of a core/feature/composition module must not reach
// an HTTP client or DI framework (sdkbase/dependencies/DependencyPolicy.kt). Adapter-zone modules
// are exempt by design: bridging to a concrete client is their job. The root zone guard fails any
// policed module that does not run this check.
@Suppress("UNCHECKED_CAST")
val zones = rootProject.extra["zones"] as Map<String, List<String>>

@Suppress("UNCHECKED_CAST")
val policedZones = (rootProject.extra["dependencyPolicedZones"] as List<String>).toSet()

val zone = zones.entries.firstOrNull { project.path in it.value }?.key

if (zone in policedZones) {
    plugins.withId("com.android.library") {
        val checkDependencyPolicy = tasks.register<CheckDependencyPolicyTask>("checkDependencyPolicy") {
            group = "verification"
            description = "Fails if a release classpath reaches an HTTP client or DI framework."
            projectPath.set(project.path)
            result.set(layout.buildDirectory.file("dependency-policy/ok"))
        }
        tasks.named("check") { dependsOn(checkDependencyPolicy) }

        val components = extensions.getByType(LibraryAndroidComponentsExtension::class.java)
        components.onVariants(components.selector().withBuildType("release")) { variant ->
            checkDependencyPolicy.configure {
                // Compile as well as runtime: a compileOnly client is still a hard requirement on the host.
                graphs.put(
                    "releaseCompileClasspath",
                    variant.compileConfiguration.incoming.resolutionResult.rootComponent,
                )
                graphs.put(
                    "releaseRuntimeClasspath",
                    variant.runtimeConfiguration.incoming.resolutionResult.rootComponent,
                )
            }
        }
    }
}
