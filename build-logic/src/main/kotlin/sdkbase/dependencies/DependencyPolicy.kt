package sdkbase.dependencies

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Maven groups an SDK module must never resolve, directly or transitively. The host owns networking
 * and object wiring (AGENTS.md "SDK rules"): a bundled client or container forces its version and
 * transitive graph onto every consumer. Matching is on the group and its sub-groups
 * (`io.ktor` also covers `io.ktor.plugin`).
 */
public val FORBIDDEN_GROUPS: Map<String, String> = mapOf(
    "com.squareup.okhttp3" to "HTTP client",
    "com.squareup.okhttp" to "HTTP client",
    "com.squareup.retrofit2" to "HTTP client",
    "com.squareup.retrofit" to "HTTP client",
    "io.ktor" to "HTTP client",
    "com.android.volley" to "HTTP client",
    "com.github.kittinunf.fuel" to "HTTP client",
    "com.google.dagger" to "DI framework",
    "io.insert-koin" to "DI framework",
    "org.kodein.di" to "DI framework",
    "com.github.stephanenicolas.toothpick" to "DI framework",
)

/** Fails when a release classpath of this module reaches a [FORBIDDEN_GROUPS] library. */
public abstract class CheckDependencyPolicyTask : DefaultTask() {
    /** Classpath name (e.g. `releaseRuntimeClasspath`) to the root of its resolved graph. */
    @get:Input
    public abstract val graphs: MapProperty<String, ResolvedComponentResult>

    @get:Input
    public abstract val projectPath: Property<String>

    @get:OutputFile
    public abstract val result: RegularFileProperty

    @TaskAction
    public fun check() {
        val violations = graphs.get().flatMap { (classpath, root) ->
            forbiddenPaths(root).map { "$classpath: $it" }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "${projectPath.get()} reaches a forbidden dependency:\n" +
                    violations.joinToString("\n") { "  - $it" } + "\n\n" +
                    "The host owns networking and DI: declare a gateway interface instead, or move the " +
                    "bridge into an adapter-zone module (docs/ARCHITECTURE.md)."
            )
        }
        result.get().asFile.writeText("ok\n")
    }

    /** Breadth-first, so each violation is reported with its shortest path from the module. */
    private fun forbiddenPaths(root: ResolvedComponentResult): List<String> {
        val via = mutableMapOf<ResolvedComponentResult, ResolvedComponentResult?>(root to null)
        val queue = ArrayDeque(listOf(root))
        val found = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            val id = component.id
            if (id is ModuleComponentIdentifier) {
                val reason = FORBIDDEN_GROUPS.entries
                    .firstOrNull { id.group == it.key || id.group.startsWith(it.key + ".") }?.value
                if (reason != null) {
                    found += "${id.group}:${id.module}:${id.version} ($reason) via ${chain(component, via)}"
                    continue
                }
            }
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { edge ->
                val next = edge.selected
                if (next !in via) {
                    via[next] = component
                    queue.addLast(next)
                }
            }
        }
        return found
    }

    private fun chain(
        component: ResolvedComponentResult,
        via: Map<ResolvedComponentResult, ResolvedComponentResult?>,
    ): String = generateSequence(component) { via[it] }
        .toList()
        .asReversed()
        .joinToString(" -> ") { it.id.displayName }
}
