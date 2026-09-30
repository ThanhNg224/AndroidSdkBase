package sdkbase.topology

/** A feature's own UI is the sole allowed feature-to-feature dependency. */
public fun isOwnUiModule(sourceName: String, targetName: String): Boolean =
    Regex("${Regex.escape(targetName)}-ui(-[a-z0-9]+)*").matches(sourceName)

/** Only named feature UI modules may use the shared UI toolkit. */
public fun isUiToolkitModule(name: String): Boolean = Regex(".+-ui(-[a-z0-9]+)+").matches(name)
