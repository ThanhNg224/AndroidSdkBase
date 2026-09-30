package sdkbase.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sdkbase.topology.isOwnUiModule
import sdkbase.topology.isUiToolkitModule

class ZoneRulesTest {

    private val allTasks = setOf(
        "checkDependencyPolicy",
        "checkSourceRules",
        "apiCheck",
        "publishAllPublicationsToLocalTestRepository",
    )
    private val policed = setOf("core", "testing", "ui", "feature", "composition")
    private val sourceRuled = policed + "adapter"

    private fun module(path: String, vararg edges: String, tasks: Set<String> = allTasks): ZoneModule =
        ZoneModule(path, path.substringAfterLast(':'), edges.toSet(), tasks)

    private fun check(
        zones: Map<String, List<String>>,
        modules: List<ZoneModule>,
        published: Set<String> = emptySet(),
        included: Set<String> = zones.values.flatten().toSet() + modules.map { it.path },
    ): List<String> = findZoneViolations(zones, modules, included, published, policed, sourceRuled)

    @Test
    fun unknownZoneAndMissingIncludedModuleKeepTheirMessages() {
        assertEquals(
            listOf(
                "unknown zone 'rogue' in the registry",
                ":missing is registered but not included (no build.gradle.kts at its path)",
            ),
            check(mapOf("rogue" to listOf(":missing")), emptyList(), included = emptySet()),
        )
    }

    @Test
    fun aLeafOutsideTheRegistryIsRejected() {
        assertEquals(
            listOf(":rogue is not registered in gradle/module-topology.gradle.kts"),
            check(emptyMap(), listOf(module(":rogue"))),
        )
    }

    @Test
    fun forbiddenZonesAndUnregisteredTargetsKeepTheirMessages() {
        val zones = mapOf("core" to listOf(":core"), "feature" to listOf(":otp"))
        assertEquals(
            listOf(
                ":core [core] -> :otp [feature] is not allowed",
                ":core [core] -> :unknown [unregistered] is not allowed",
            ),
            check(zones, listOf(module(":core", ":otp", ":unknown"), module(":otp"))),
        )
    }

    @Test
    fun ownUiMayUseItsFeatureButAPeerMayNot() {
        val zones = mapOf("feature" to listOf(":otp", ":otp-ui-compose", ":otp-extra"))
        assertEquals(emptyList<String>(), check(zones, listOf(module(":otp-ui-compose", ":otp"))))
        assertEquals(
            listOf(
                ":otp-extra [feature] -> :otp [feature] is not allowed: a feature " +
                    "depends on another feature only as its UI module (<name>-ui-<toolkit> -> <name>); " +
                    "wire different features together in a composition module",
            ),
            check(zones, listOf(module(":otp-extra", ":otp"))),
        )
        assertTrue(check(zones, listOf(module(":otp", ":otp-ui-compose"))).isNotEmpty())
    }

    @Test
    fun headlessFeatureCannotReachTheSharedUiToolkit() {
        val zones = mapOf("feature" to listOf(":otp", ":otp-ui-compose"), "ui" to listOf(":ui"))
        assertEquals(emptyList<String>(), check(zones, listOf(module(":otp-ui-compose", ":ui"))))
        assertEquals(
            listOf(
                ":otp [feature] -> :ui [ui] is not allowed: only " +
                    "<name>-ui-<toolkit> modules may use the shared UI toolkit",
            ),
            check(zones, listOf(module(":otp", ":ui"))),
        )
    }

    @Test
    fun compositionCanReachSeveralFeaturesAndAdaptersCanReachVendor() {
        val zones = mapOf(
            "feature" to listOf(":otp", ":profile"),
            "composition" to listOf(":flow"),
            "adapter" to listOf(":bridge"),
            "vendor" to listOf(":binary"),
        )
        assertEquals(
            emptyList<String>(),
            check(zones, listOf(module(":flow", ":otp", ":profile"), module(":bridge", ":otp", ":binary"))),
        )
        assertEquals(
            listOf(":otp [feature] -> :binary [vendor] is not allowed"),
            check(zones, listOf(module(":otp", ":binary"))),
        )
    }

    @Test
    fun mainDependenciesOnTestingAreLimitedToBomAndApp() {
        val zones = mapOf(
            "testing" to listOf(":testing"),
            "feature" to listOf(":otp"),
            "bom" to listOf(":bom"),
            "app" to listOf(":demo"),
        )
        assertEquals(
            emptyList<String>(),
            check(zones, listOf(module(":bom", ":testing"), module(":demo", ":testing"))),
        )
        assertEquals(
            listOf(":otp [feature] -> :testing [testing] is not allowed"),
            check(zones, listOf(module(":otp", ":testing"))),
        )
    }

    @Test
    fun missingEnforcementTasksAreReportedInTheExistingOrder() {
        assertEquals(
            listOf(
                ":otp [feature] has no checkDependencyPolicy (apply sdkbase.android.library)",
                ":otp [feature] has no checkSourceRules (apply sdkbase.android.library)",
                ":otp is published but has no apiCheck (apply sdkbase.abi)",
                ":otp is published but has no localTest publication",
            ),
            check(mapOf("feature" to listOf(":otp")), listOf(module(":otp", tasks = emptySet())), setOf(":otp")),
        )
    }

    @Test
    fun bomDoesNotRequireAnAbiTaskButDoesRequirePublication() {
        val zones = mapOf("bom" to listOf(":bom"))
        assertEquals(
            emptyList<String>(),
            check(zones, listOf(module(":bom", tasks = allTasks - "apiCheck")), setOf(":bom")),
        )
        assertEquals(
            listOf(":bom is published but has no localTest publication"),
            check(zones, listOf(module(":bom", tasks = emptySet())), setOf(":bom")),
        )
    }

    @Test
    fun aPublishedModuleCannotReachAnUnpublishedDependency() {
        assertEquals(
            listOf(":bridge is published but depends on unpublished :binary"),
            check(
                mapOf("adapter" to listOf(":bridge"), "vendor" to listOf(":binary")),
                listOf(module(":bridge", ":binary")),
                setOf(":bridge"),
            ),
        )
    }

    @Test
    fun namingChecksUseTheExactOwnerAndToolkitConvention() {
        assertTrue(isOwnUiModule("face-match-ui-compose", "face-match"))
        assertFalse(isOwnUiModule("otp-extra-ui-compose", "otp"))
        assertFalse(isOwnUiModule("otpX-ui-compose", "otp."))
        assertTrue(isUiToolkitModule("face-match-ui-compose"))
        assertFalse(isUiToolkitModule("otp-ui"))
        assertFalse(isUiToolkitModule("otp-ui-Compose"))
    }
}
