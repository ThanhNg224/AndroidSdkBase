plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "WorkManager delivery adapter for AndroidSdkBase event logging"

android {
    namespace = "io.github.thanhng224.sdkbase.eventlogging.work"
}

dependencies {
    api(project(":sdk:features:event-logging"))
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
