package com.noscroll.redirect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noscroll.R

/** Always visible, including with an empty PDF library; opening NoScroll itself still opens its library. */
@Composable
fun BlockerDestinationSettingsHost(
    destinationName: String,
    onChange: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        Surface {
            Column {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.blocker_opens_label), style = MaterialTheme.typography.labelMedium)
                        Text(destinationName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onChange) { Text(stringResource(R.string.blocker_change_app)) }
                }
            }
        }
    }
}

@Composable
fun BlockerDestinationScreen(
    apps: List<DestinationApp>,
    selected: BlockerDestination,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSelect: (BlockerDestination) -> Unit,
    onRefresh: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleApps = destinationApps(apps, ownPackage = "", query = query)
    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.blocker_back)) }
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.blocker_refresh)) }
                }
                Text(stringResource(R.string.blocker_choose_title), style = MaterialTheme.typography.headlineSmall)
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding().selectableGroup()) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.blocker_choose_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp)
                )
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.blocker_search_apps)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }
            if (errorMessage != null) {
                item(key = "error") {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                }
            }
            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            item(key = "reader") {
                DestinationRow(
                    label = stringResource(R.string.blocker_reader_name),
                    detail = stringResource(R.string.blocker_reader_description),
                    selected = selected == BlockerDestination.Reader,
                    onClick = { onSelect(BlockerDestination.Reader) }
                )
                HorizontalDivider()
            }
            items(visibleApps, key = { "app:${it.packageName}" }) { app ->
                DestinationRow(
                    label = app.label,
                    detail = app.packageName,
                    selected = selected == BlockerDestination.App(app.packageName),
                    onClick = { onSelect(BlockerDestination.App(app.packageName)) }
                )
            }
            if (!loading && visibleApps.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.blocker_no_apps), modifier = Modifier.padding(20.dp))
                }
            }
            item(key = "privacy") {
                Text(
                    stringResource(R.string.blocker_apps_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DestinationRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
