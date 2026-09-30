plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "{{project_name}} {{name}} composition"

android {
    namespace = "{{namespace}}"
}

dependencies {
    api(project(":sdk:core"))
    testImplementation(project(":sdk:core-testing"))
}
