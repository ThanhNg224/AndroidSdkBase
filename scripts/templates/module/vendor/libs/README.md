# Local vendor binaries

Drop the vendor's `.jar` or `.aar` file in this directory. The module's `build.gradle.kts`
includes both file types from `libs/`. Vendor modules are always unpublished and may be reached
only by an adapter.
