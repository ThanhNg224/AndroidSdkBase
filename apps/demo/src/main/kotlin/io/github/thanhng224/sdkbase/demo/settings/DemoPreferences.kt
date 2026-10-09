package io.github.thanhng224.sdkbase.demo.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class DemoThemeMode { System, Light, Dark }

internal enum class DemoLanguage { System, Vietnamese, English }

internal data class DemoSettings(
    val theme: DemoThemeMode = DemoThemeMode.System,
    val language: DemoLanguage = DemoLanguage.System,
) {
    val locale: Locale?
        get() = when (language) {
            DemoLanguage.System -> null
            DemoLanguage.Vietnamese -> Locale.forLanguageTag("vi")
            DemoLanguage.English -> Locale.ENGLISH
        }
}

/** Each Activity owns a reader; Android's named preferences share persisted values between them. */
internal class DemoPreferences(private val preferences: SharedPreferences) : AutoCloseable {
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<DemoSettings> = mutableState.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutableState.value = read() }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setTheme(theme: DemoThemeMode) {
        preferences.edit { putString("theme", theme.name) }
    }

    fun setLanguage(language: DemoLanguage) {
        preferences.edit { putString("language", language.name) }
    }

    private fun read(): DemoSettings = DemoSettings(
        theme = DemoThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) }
            ?: DemoThemeMode.System,
        language = DemoLanguage.entries.firstOrNull { it.name == preferences.getString("language", null) }
            ?: DemoLanguage.System,
    )

    override fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

internal class DemoSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = DemoPreferences(application.getSharedPreferences("demo_settings", Context.MODE_PRIVATE))
    val state: StateFlow<DemoSettings> = preferences.state

    fun setTheme(theme: DemoThemeMode) = preferences.setTheme(theme)

    fun setLanguage(language: DemoLanguage) = preferences.setLanguage(language)

    override fun onCleared() {
        preferences.close()
    }
}
