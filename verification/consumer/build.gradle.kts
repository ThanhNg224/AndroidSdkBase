// Builds on AGP 9.4.1's bundled Kotlin (2.2.10) on purpose: that is the consumer Kotlin floor.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
