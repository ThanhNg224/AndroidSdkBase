plugins {
    id("sdkbase.android.library")
}

android {
    namespace = "io.github.thanhng224.sdkbase.otpsdk"
}

dependencies {
    api(project(":sdk:core"))
    implementation(project(":sdk:platform"))
    implementation(project(":sdk:capabilities:otp-engine"))
    implementation(libs.coroutines.core)
}
