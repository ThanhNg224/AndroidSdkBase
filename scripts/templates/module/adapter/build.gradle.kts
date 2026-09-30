plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "{{project_name}} optional {{name}} adapter"

android {
    namespace = "{{namespace}}"
}

dependencies {
    api(project(":sdk:core"))
}
