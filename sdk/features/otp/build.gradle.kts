plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.android.publishing")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp"
}

dependencies {
    api(project(":sdk:core"))
    implementation(libs.coroutines.android)
}
