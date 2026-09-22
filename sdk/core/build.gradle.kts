plugins {
    id("sdkbase.kotlin.jvm")
    id("sdkbase.abi.jvm")
}

dependencies {
    // `api`, not `implementation`: CoroutineDispatcher appears in DispatcherProvider's public surface.
    api(libs.coroutines.core)
}
