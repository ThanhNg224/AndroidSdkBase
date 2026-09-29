package io.github.thanhng224.sdkbase.ui.locale

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/**
 * Renders [content] with SDK strings resolved for [locale], regardless of the device language.
 * `null` leaves the host's own locale untouched. Wrap an SDK screen in it to force a language for
 * that screen only; a host that wants its own strings changed overrides the resource, as usual.
 *
 * Inside [content] the context is a configuration wrapper, not the Activity itself, so code there
 * must not cast `LocalContext.current` to an `Activity`.
 *
 * Host requirement: an app published as an Android App Bundle must not split by language
 * (`android.bundle.language.enableSplit = false`), or a language other than the device's may be
 * missing at runtime. Lint reports this in the app, which is why it is suppressed here.
 */
@SuppressLint("AppBundleLocaleChanges")
@Composable
public fun ProvideSdkLocale(locale: Locale?, content: @Composable () -> Unit) {
    if (locale == null) {
        content()
        return
    }
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localized = remember(base, baseConfiguration, locale) {
        val configuration = Configuration(baseConfiguration).apply { setLocale(locale) }
        base.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalResources provides localized.resources,
        content = content,
    )
}
