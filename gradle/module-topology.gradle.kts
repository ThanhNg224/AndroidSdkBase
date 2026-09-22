// The single place that declares what each module IS. Two registries:
//   zones              — which layer a module belongs to; drives the dependency rule.
//   publishedArtifacts — what ships to Maven Central; drives publishing and ABI baselines.
// Adding a module without registering it here fails the zone guard as `unregistered`.

extra["zones"] = mapOf(
    "core" to listOf(":sdk:core"),
    "platform" to listOf(":sdk:platform"),
    "capability" to listOf(
        ":sdk:capabilities:otp-engine",
        ":sdk:capabilities:otp-ui-compose",
    ),
    "facade" to listOf(":sdk:facades:otp-sdk"),
    "app" to listOf(":apps:demo"),
)

extra["publishedArtifacts"] = listOf(
    ":sdk:core",
    ":sdk:platform",
    ":sdk:capabilities:otp-engine",
    ":sdk:capabilities:otp-ui-compose",
    ":sdk:facades:otp-sdk",
)
