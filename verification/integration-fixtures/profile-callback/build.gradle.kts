plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "Optional callback backend bridge integration fixture"

android { namespace = "{{SDK_NAMESPACE}}.profilecallback" }

dependencies {
    api(project(":sdk:core"))
    api(project(":sdk:features:profile"))
}
