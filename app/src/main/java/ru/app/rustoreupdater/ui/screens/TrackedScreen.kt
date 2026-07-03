package ru.app.rustoreupdater.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.app.rustoreupdater.R
import ru.app.rustoreupdater.data.db.TrackedAppEntity
import ru.app.rustoreupdater.ui.components.AppCard
import ru.app.rustoreupdater.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedScreen(
    onOpenDetail: (String, String?) -> Unit,
    vm: TrackedViewModel = viewModel(),
) {
    val apps by vm.apps.collectAsState()
    val checking by vm.checking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tracked)) },
                actions = {
                    IconButton(onClick = { vm.checkNow() }, enabled = !checking) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.settings_check_now))
                    }
                }
            )
        }
    ) { padding ->
        if (apps.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.tracked_empty_title),
                    subtitle = stringResource(R.string.tracked_empty_subtitle),
                )
            }
        } else {
            var pendingDelete by remember { mutableStateOf<TrackedAppEntity?>(null) }

            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(apps, key = { it.appId }) { app ->
                    AppCard(
                        app = app,
                        onClick = { onOpenDetail(app.appId, app.packageName) },
                        trailing = {
                            TrailingStatus(app = app, onUpdate = { vm.download(app.appId) })
                        },
                        trailingIcon = {
                            DeleteIconButton { pendingDelete = app }
                        },
                    )
                }
            }

            // Confirmation dialog before removing from tracked apps.
            pendingDelete?.let { app ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("Удалить из отслеживаемых?") },
                    text = { Text("Вы точно хотите удалить «${app.appName}» из отслеживаемых?") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.untrack(app.appId)
                            pendingDelete = null
                        }) { Text("Удалить") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
                    },
                )
            }
        }
    }
}

@Composable
private fun DeleteIconButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.detail_btn_remove),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TrailingStatus(
    app: TrackedAppEntity,
    onUpdate: () -> Unit,
) {
    val downloading by ru.app.rustoreupdater.download.ActiveDownloads.downloadingApps.collectAsState()
    val installing by ru.app.rustoreupdater.download.ActiveDownloads.installingApps.collectAsState()
    val isDownloading = app.appId in downloading
    val installed = app.installedVersionCode?.let { if (it < 0) null else it }
    // "Installing" only counts while the app is actually present; ignore stuck flags
    // after the app was uninstalled.
    val isInstalling = app.appId in installing && installed != null
    val busy = isDownloading || isInstalling
    when {
        app.updateAvailable && installed != null -> {
            AssistChip(
                onClick = onUpdate,
                enabled = !busy,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (isInstalling) R.string.tracked_btn_installing
                                    else R.string.tracked_btn_downloading
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Text(
                                stringResource(R.string.tracked_update_format, installed.toString(), app.latestVersionCode.toString()),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            )
        }
        app.updateAvailable -> {
            Button(onClick = onUpdate, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (isInstalling) R.string.tracked_btn_installing
                            else R.string.tracked_btn_downloading
                        )
                    )
                } else {
                    Text(stringResource(R.string.tracked_btn_install))
                }
            }
        }
        else -> {
            Text(
                stringResource(R.string.tracked_up_to_date) + " · v" + (app.latestVersionName ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
