package io.github.thanhng224.sdkbase.demo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.thanhng224.sdkbase.demo.R
import io.github.thanhng224.sdkbase.demo.ui.theme.DemoDimens
import io.github.thanhng224.sdkbase.ui.theme.SdkDimens
import io.github.thanhng224.sdkbase.ui.theme.SdkSpacing

@Composable
internal fun DemoHeader(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = SdkDimens.MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdkSpacing.One),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(ImageVector.vectorResource(R.drawable.demo_ic_back), stringResource(R.string.demo_back))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

/** The caller provides bottom/rail space; safeDrawing consumes only the remaining system insets. */
@Composable
internal fun DemoScreen(
    title: String,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = DemoDimens.maxContentWidth).fillMaxSize(),
        ) {
            DemoHeader(title, Modifier.padding(horizontal = SdkSpacing.Three, vertical = SdkSpacing.Two))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = SdkSpacing.Three, vertical = SdkSpacing.Two),
                verticalArrangement = Arrangement.spacedBy(SdkSpacing.Two),
                content = content,
            )
            if (bottomContent != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = SdkSpacing.Three, vertical = SdkSpacing.Two)) {
                    bottomContent()
                }
            }
        }
    }
}

@Composable
internal fun DemoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(SdkSpacing.Two),
            verticalArrangement = Arrangement.spacedBy(SdkSpacing.Two),
            content = content,
        )
    }
}

@Composable
internal fun DemoStatus(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(SdkSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(SdkSpacing.Two),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(SdkSpacing.Three))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
internal fun DemoChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = SdkDimens.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(SdkSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdkSpacing.One),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supportingContent?.invoke()
        }
    }
}
