plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "Integration fixture profile feature"

android { namespace = "{{SDK_NAMESPACE}}.profile" }

dependencies {
    api(project(":sdk:core"))
    testImplementation(project(":sdk:core-testing"))
}
