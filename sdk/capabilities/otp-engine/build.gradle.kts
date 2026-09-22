plugins {
    id("sdkbase.android.library")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otp"
}

dependencies {
    api(project(":sdk:core"))
    implementation(project(":sdk:platform"))
    implementation(libs.coroutines.core)
}
