// The module registry. Every included module must appear in `zones`; what ships is `publishedArtifacts`.
// An empty zone is deliberate: it reserves the slot and its rules before the first module needs it.
extra["zones"] = mapOf(
    "core" to listOf(":sdk:core"),
    // The published test kit: fakes and assertions for SDK and host tests. Depends on `core`
    // only; features/compositions consume it via `testImplementation` (the zone guard ignores
    // test configurations), so only `bom` and `app` may target it in a non-test configuration.
    "testing" to listOf(":sdk:core-testing"),
    "feature" to listOf(
        ":sdk:features:otp",
        ":sdk:features:otp-ui-compose",
    ),
    // Wires several features into one flow (e.g. OTP + KYC + payment). The only SDK zone that may
    // see more than one feature.
    "composition" to listOf<String>(),
    // Optional host-side bridges (a gateway backed by a specific HTTP client, a vendor device SDK).
    // The only SDK zone allowed third-party network/DI libraries; nothing in the SDK depends on it.
    "adapter" to listOf<String>(),
    "bom" to listOf(":sdk:bom"),
    "app" to listOf(":apps:demo"),
)

// Zones whose modules must not resolve an HTTP client or DI framework (checkDependencyPolicy).
extra["dependencyPolicedZones"] = listOf("core", "testing", "feature", "composition")

// Zones whose modules run checkSourceRules (GlobalScope, android.util.Log, an owned
// CoroutineScope in feature/composition, a public multi-property data class).
extra["sourceRuledZones"] = listOf("core", "testing", "feature", "composition", "adapter")

extra["publishedArtifacts"] = listOf(
    ":sdk:core",
    ":sdk:core-testing",
    ":sdk:features:otp",
    ":sdk:features:otp-ui-compose",
    ":sdk:bom",
)
