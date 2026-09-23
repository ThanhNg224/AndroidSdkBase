plugins {
    id("sdkbase.kotlin.jvm")
    id("sdkbase.abi")
    id("sdkbase.publishing")
}

description = "AndroidSdkBase contracts shared by every feature: results, errors, logging, telemetry"

dependencies {
    // `api`, not `implementation`: CoroutineDispatcher appears in DispatcherProvider's public surface.
    api(libs.coroutines.core)
}
