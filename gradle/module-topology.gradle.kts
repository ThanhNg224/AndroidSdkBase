// The module registry. Every included module must appear in `zones`; what ships is `publishedArtifacts`.
// An empty zone is deliberate: it reserves the slot and its rules before the first module needs it.
extra["zones"] = mapOf(
    "core" to listOf(":sdk:core"),
    // The published test kit: fakes and assertions for SDK and host tests. Depends on `core`
    // only; features/compositions consume it via `testImplementation` (the zone guard ignores
    // test configurations), so only `bom` and `app` may target it in a non-test configuration.
    "testing" to listOf(":sdk:core-testing"),
    // Shared Compose UI toolkit: theme tokens, contrast maths, error text, locale. Depends on `core`
    // only. Only `<name>-ui-<toolkit>` feature modules (and bom/app) may depend on it, so Compose
    // never reaches a headless feature.
    "ui" to listOf(":sdk:core-ui-compose"),
    "feature" to listOf(
        ":sdk:features:otp",
        ":sdk:features:otp-ui-compose",
        ":sdk:features:event-logging",
        ":sdk:features:logging-file",
    ),
    // Wires several features into one flow (e.g. OTP + KYC + payment). The only SDK zone that may
    // see more than one feature.
    "composition" to listOf<String>(),
    // Optional host-side bridges (a gateway backed by a specific HTTP client, a vendor device SDK).
    // The only SDK zone allowed third-party network/DI libraries; nothing in the SDK depends on it.
    "adapter" to listOf(":sdk:adapters:event-logging-work", ":sdk:adapters:otp-fake-sms"),
    // Wraps a local vendor binary that has no Maven coordinate (a device or SMS SDK a vendor hands
    // you). Never published; only an adapter may depend on it, so it cannot reach a published graph.
    "vendor" to listOf(":sdk:vendor:fake-sms-vendor"),
    "bom" to listOf(":sdk:bom"),
    "app" to listOf(":apps:demo"),
)

// Zones whose modules must not resolve an HTTP client or DI framework (checkDependencyPolicy).
extra["dependencyPolicedZones"] = listOf("core", "testing", "ui", "feature", "composition")

// Zones whose modules run checkSourceRules (GlobalScope, android.util.Log, an owned
// CoroutineScope in feature/composition, a public multi-property data class, and a colour literal
// in a UI module).
extra["sourceRuledZones"] = listOf("core", "testing", "ui", "feature", "composition", "adapter")

extra["publishedArtifacts"] = listOf(
    ":sdk:core",
    ":sdk:core-testing",
    ":sdk:core-ui-compose",
    ":sdk:features:otp",
    ":sdk:features:otp-ui-compose",
    ":sdk:features:event-logging",
    ":sdk:features:logging-file",
    ":sdk:adapters:event-logging-work",
    ":sdk:bom",
)
