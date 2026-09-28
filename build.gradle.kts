// AGP 9.4.1 ships KGP 2.2.10. This classpath entry is the documented, supported way to move to a
// newer Kotlin: Gradle resolves the conflict upward (verified: 2.2.10 -> 2.4.20).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dependency.analysis)
}

apply(from = "gradle/module-topology.gradle.kts")

subprojects {
    group = providers.gradleProperty("sdkbase.group").get()
    version = providers.gradleProperty("sdkbase.version").get()
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// ---------------------------------------------------------------------------------------------
// Zone guard: module boundaries are enforced here, at configuration time, not by review.
// Rules: (1) every included module is registered; (2) dependency and constraint edges only go to
// allowed zones, across every non-test configuration (compileOnly, runtimeOnly and variant-specific
// ones included); (3) a feature depends on another feature only as that feature's UI module;
// (4) a published module depends only on published modules; (5) a published module has an ABI check
// and a local publication; (6) every core/testing/feature/composition module runs
// checkDependencyPolicy.
// ---------------------------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
val zones = rootProject.extra["zones"] as Map<String, List<String>>

@Suppress("UNCHECKED_CAST")
val publishedArtifacts = (rootProject.extra["publishedArtifacts"] as List<String>).toSet()

val zoneByPath: Map<String, String> =
    zones.flatMap { (zone, paths) -> paths.map { it to zone } }.toMap()

val allowedTargets: Map<String, Set<String>> = mapOf(
    "core" to emptySet(),
    "testing" to setOf("core"),
    // feature -> feature is narrowed further by isOwnUiModule below.
    "feature" to setOf("core", "feature"),
    "composition" to setOf("core", "feature"),
    "adapter" to setOf("core", "feature"),
    "bom" to setOf("core", "feature", "composition", "adapter", "testing"),
    "app" to setOf("core", "feature", "composition", "adapter", "app", "testing"),
)

@Suppress("UNCHECKED_CAST")
val policedZones = (rootProject.extra["dependencyPolicedZones"] as List<String>).toSet()

// `otp-ui-compose` -> `otp` is the one allowed feature -> feature edge: a UI module on top of the
// feature it renders. Two different features are wired together only in a composition module.
fun isOwnUiModule(sourceName: String, targetName: String): Boolean =
    Regex("${Regex.escape(targetName)}-ui(-[a-z0-9]+)*").matches(sourceName)

fun isTestConfiguration(name: String): Boolean = name.contains("test", ignoreCase = true)

fun projectEdges(project: Project): Set<String> {
    val projectsByCoordinates = rootProject.subprojects.associateBy {
        it.group.toString() to it.name
    }
    return project.configurations
        .filterNot { isTestConfiguration(it.name) }
        .flatMap { configuration ->
            val dependencyPaths = configuration.dependencies
                .withType(ProjectDependency::class.java)
                .map { it.path }
            val constrainedProjectPaths = configuration.dependencyConstraints
                .mapNotNull { constraint ->
                    projectsByCoordinates[constraint.group to constraint.name]?.path
                }
            dependencyPaths + constrainedProjectPaths
        }
        .toSet()
}

gradle.projectsEvaluated {
    val violations = mutableListOf<String>()

    (zones.keys - allowedTargets.keys).forEach { violations += "unknown zone '$it' in the registry" }
    zoneByPath.keys.filter { rootProject.findProject(it) == null }
        .forEach { violations += "$it is registered but not included in settings.gradle.kts" }

    // Parent projects implied by nested paths (`:sdk`, `:sdk:features`) have no build file.
    rootProject.subprojects.filter { it.buildFile.exists() }.forEach { source ->
        val sourceZone = zoneByPath[source.path]
        if (sourceZone == null) {
            violations += "${source.path} is not registered in gradle/module-topology.gradle.kts"
            return@forEach
        }
        val allowed = allowedTargets[sourceZone].orEmpty()
        val published = source.path in publishedArtifacts
        projectEdges(source).forEach { target ->
            val targetZone = zoneByPath[target] ?: "unregistered"
            if (targetZone !in allowed) {
                violations += "${source.path} [$sourceZone] -> $target [$targetZone] is not allowed"
            } else if (sourceZone == "feature" && targetZone == "feature" &&
                !isOwnUiModule(source.name, target.substringAfterLast(':'))
            ) {
                violations += "${source.path} [feature] -> $target [feature] is not allowed: a feature " +
                    "depends on another feature only as its UI module (<name>-ui-<toolkit> -> <name>); " +
                    "wire different features together in a composition module"
            }
            if (published && target !in publishedArtifacts) {
                violations += "${source.path} is published but depends on unpublished $target"
            }
        }
        if (sourceZone in policedZones && source.tasks.findByName("checkDependencyPolicy") == null) {
            violations += "${source.path} [$sourceZone] has no checkDependencyPolicy (apply sdkbase.android.library)"
        }
        if (published) {
            if (sourceZone != "bom" && source.tasks.findByName("apiCheck") == null) {
                violations += "${source.path} is published but has no apiCheck (apply sdkbase.abi)"
            }
            if (source.tasks.findByName("publishAllPublicationsToLocalTestRepository") == null) {
                violations += "${source.path} is published but has no localTest publication"
            }
        }
    }

    if (violations.isNotEmpty()) {
        throw GradleException(
            "Module zone violation(s) — see docs/ARCHITECTURE.md:\n" +
                violations.joinToString("\n") { " - $it" }
        )
    }
}
