package io.github.thanhng224.sdkbase.demo.host

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.thanhng224.sdkbase.demo.R
import io.github.thanhng224.sdkbase.demo.settings.DemoLanguage
import io.github.thanhng224.sdkbase.demo.settings.DemoSettings
import io.github.thanhng224.sdkbase.demo.settings.DemoThemeMode
import io.github.thanhng224.sdkbase.demo.ui.component.DemoCard
import io.github.thanhng224.sdkbase.demo.ui.component.DemoChoiceRow
import io.github.thanhng224.sdkbase.demo.ui.component.DemoScreen
import io.github.thanhng224.sdkbase.demo.ui.component.DemoStatus
import io.github.thanhng224.sdkbase.ui.theme.SdkDimens
import io.github.thanhng224.sdkbase.ui.theme.SdkSpacing

@Composable
internal fun DemoFeaturesScreen(onOpenOtp: () -> Unit, modifier: Modifier = Modifier) {
    DemoScreen(
        title = stringResource(R.string.demo_features),
        modifier = modifier,
        bottomContent = {
            Button(onOpenOtp, Modifier.fillMaxWidth().heightIn(min = SdkDimens.MinTouchTarget)) {
                Text(stringResource(R.string.demo_open_otp))
            }
        },
    ) {
        Text(stringResource(R.string.demo_features_intro), style = MaterialTheme.typography.bodyLarge)
        DemoCard {
            Text(stringResource(R.string.demo_otp), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.demo_otp_description))
            Text(stringResource(R.string.demo_otp_test_code), color = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(R.string.demo_otp_session_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DemoGalleryScreen(modifier: Modifier = Modifier) {
    var clicks by rememberSaveable { mutableIntStateOf(0) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var choice by rememberSaveable { mutableIntStateOf(0) }
    DemoScreen(stringResource(R.string.demo_ui), modifier) {
        Text(stringResource(R.string.demo_ui_intro), style = MaterialTheme.typography.bodyLarge)
        DemoCard {
            Text(stringResource(R.string.demo_actions), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { clicks++ }, modifier = Modifier.fillMaxWidth().testTag("gallery_action")) {
                Text(stringResource(R.string.demo_action))
            }
            Text(stringResource(R.string.demo_click_count, clicks), Modifier.testTag("gallery_count"))
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.demo_disabled))
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SdkSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        Modifier.size(SdkSpacing.Two),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.demo_loading))
                }
            }
        }
        DemoCard {
            Text(stringResource(R.string.demo_choices), style = MaterialTheme.typography.titleMedium)
            val notificationsLabel = stringResource(R.string.demo_notifications)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(notificationsLabel, Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    modifier = Modifier.testTag("gallery_switch").semantics { contentDescription = notificationsLabel },
                )
            }
            Column(Modifier.selectableGroup()) {
                for (index in 0..1) {
                    DemoChoiceRow(
                        stringResource(if (index == 0) R.string.demo_choice_one else R.string.demo_choice_two),
                        selected = choice == index,
                        onClick = { choice = index },
                    )
                }
            }
        }
        DemoStatus(stringResource(R.string.demo_loading), stringResource(R.string.demo_loading_message), loading = true)
        DemoStatus(
            title = stringResource(R.string.demo_example_error),
            message = stringResource(R.string.demo_example_error_message),
            action = {
                OutlinedButton(onClick = { clicks = 0 }) { Text(stringResource(R.string.demo_reset_example)) }
            },
        )
    }
}

@Composable
internal fun DemoSettingsScreen(
    settings: DemoSettings,
    onThemeSelected: (DemoThemeMode) -> Unit,
    onLanguageSelected: (DemoLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    DemoScreen(stringResource(R.string.demo_settings), modifier) {
        Text(stringResource(R.string.demo_settings_intro), style = MaterialTheme.typography.bodyLarge)
        DemoCard {
            Text(stringResource(R.string.demo_theme), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                DemoThemeMode.entries.forEach { theme ->
                    DemoChoiceRow(
                        label = stringResource(theme.labelRes),
                        selected = settings.theme == theme,
                        onClick = { onThemeSelected(theme) },
                        modifier = Modifier.testTag("theme_${theme.name}"),
                    )
                }
            }
        }
        DemoCard {
            Text(stringResource(R.string.demo_language), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                DemoLanguage.entries.forEach { language ->
                    DemoChoiceRow(
                        label = stringResource(language.labelRes),
                        selected = settings.language == language,
                        onClick = { onLanguageSelected(language) },
                        modifier = Modifier.testTag("language_${language.name}"),
                    )
                }
            }
        }
    }
}

internal val DemoThemeMode.labelRes: Int
    get() = when (this) {
        DemoThemeMode.System -> R.string.demo_system
        DemoThemeMode.Light -> R.string.demo_light
        DemoThemeMode.Dark -> R.string.demo_dark
    }

internal val DemoLanguage.labelRes: Int
    get() = when (this) {
        DemoLanguage.System -> R.string.demo_system
        DemoLanguage.Vietnamese -> R.string.demo_vietnamese
        DemoLanguage.English -> R.string.demo_english
    }
