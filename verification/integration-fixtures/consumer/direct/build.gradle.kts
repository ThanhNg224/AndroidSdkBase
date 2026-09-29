plugins {
    id("com.android.application")
}

android {
    namespace = "{{SDK_NAMESPACE}}.fixture.consumer.direct"
    compileSdk = {{COMPILE_SDK}}
    defaultConfig { applicationId = "{{SDK_NAMESPACE}}.fixture.consumer.direct"; minSdk = {{MIN_SDK}}; targetSdk = {{COMPILE_SDK}} }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:{{KOTLIN_STDLIB_FLOOR}}")
    implementation(platform("{{SDK_GROUP}}:bom:{{SDK_VERSION}}"))
    implementation("{{SDK_GROUP}}:core")
    implementation("{{SDK_GROUP}}:otp")
    implementation("{{SDK_GROUP}}:profile")
    implementation("{{SDK_GROUP}}:onboarding")
}
