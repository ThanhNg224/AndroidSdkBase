// The module registry. Every included module must appear in `zones`; what ships is `publishedArtifacts`.
extra["zones"] = mapOf(
    "core" to listOf(":sdk:core"),
    "feature" to listOf(
        ":sdk:features:otp",
        ":sdk:features:otp-ui-compose",
    ),
    "bom" to listOf(":sdk:bom"),
    "app" to listOf(":apps:demo"),
)

extra["publishedArtifacts"] = listOf(
    ":sdk:core",
    ":sdk:features:otp",
    ":sdk:features:otp-ui-compose",
    ":sdk:bom",
)
