package io.github.thanhng224.sdkbase.demo

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.thanhng224.sdkbase.demo.settings.DemoLanguage
import io.github.thanhng224.sdkbase.demo.settings.DemoPreferences
import io.github.thanhng224.sdkbase.demo.settings.DemoSettings
import io.github.thanhng224.sdkbase.demo.settings.DemoThemeMode
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoPreferencesTest {
    @Test
    fun readersObserveChangesAndNewReaderRestoresPersistedAppearance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "demo_settings_test_${UUID.randomUUID()}"
        val store = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            DemoPreferences(store).use { writer ->
                DemoPreferences(store).use { observer ->
                    assertEquals(DemoSettings(), writer.state.value)
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        writer.setTheme(DemoThemeMode.Dark)
                        writer.setLanguage(DemoLanguage.Vietnamese)
                    }
                    val expected = DemoSettings(DemoThemeMode.Dark, DemoLanguage.Vietnamese)
                    assertEquals(expected, observer.state.value)
                    DemoPreferences(store).use { reopened -> assertEquals(expected, reopened.state.value) }
                }
            }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun unknownStoredChoicesUseSystemDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "demo_settings_test_${UUID.randomUUID()}"
        val store = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            store.edit(commit = true) {
                putString("theme", "unknown")
                putString("language", "unknown")
            }
            DemoPreferences(store).use { assertEquals(DemoSettings(), it.state.value) }
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
