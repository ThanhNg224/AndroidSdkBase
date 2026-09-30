plugins {
    id("sdkbase.android.library")
    id("sdkbase.android.compose")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "{{project_name}} Compose UI for the {{owner}} feature"

android {
    namespace = "{{namespace}}"
}

dependencies {
    api(project(":sdk:features:{{owner}}"))
    implementation(project(":sdk:core-ui-compose"))
}
