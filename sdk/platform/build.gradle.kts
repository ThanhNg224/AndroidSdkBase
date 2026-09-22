plugins {
    id("sdkbase.android.library")
}

android {
    namespace = "io.github.thanhng224.sdkbase.platform"
}

dependencies {
    api(project(":sdk:core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
}
