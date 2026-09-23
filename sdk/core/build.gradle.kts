plugins {
    id("sdkbase.android.library")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase contracts shared by every feature: results, errors, logging, telemetry"

android {
    namespace = "io.github.thanhng224.sdkbase.core"
}

dependencies {
    // `api`, not `implementation`: CoroutineDispatcher appears in DispatcherProvider's public surface.
    api(libs.coroutines.core)
    // The real (Android-backed) dispatchers now live here, alongside the DispatcherProvider contract.
    implementation(libs.coroutines.android)
}
