plugins {
    id("sdkbase.android.library")
    id("sdkbase.android.compose")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase shared Compose UI toolkit: theme tokens, contrast, error text, locale"

android {
    namespace = "io.github.thanhng224.sdkbase.ui"
}

dependencies {
    api(project(":sdk:core"))
}
