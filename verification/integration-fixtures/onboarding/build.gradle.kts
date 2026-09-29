plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "Integration fixture multi-feature composition"

android { namespace = "{{SDK_NAMESPACE}}.onboarding" }

dependencies {
    api(project(":sdk:core"))
    api(project(":sdk:features:otp"))
    api(project(":sdk:features:profile"))
    testImplementation(project(":sdk:core-testing"))
}
