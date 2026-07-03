package ru.app.rustoreupdater.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.app.rustoreupdater.R
import ru.app.rustoreupdater.ui.components.AppIcon
import ru.app.rustoreupdater.ui.components.ErrorState
import ru.app.rustoreupdater.ui.components.InfoRow
import ru.app.rustoreupdater.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    val tracked by vm.app.collectAsState()
    val info by vm.info.collectAsState()
    val loading by vm.loadingInfo.collectAsState()
    val error by vm.error.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(info?.appName ?: tracked?.appName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding)) { LoadingState() }
            error != null -> Box(Modifier.padding(padding)) { ErrorState(error!!) }
            info == null -> Box(Modifier.padding(padding)) { ErrorState("Нет данных") }
            else -> DetailContent(
                info = info!!,
                tracked = tracked,
                appId = vm.appId,
                modifier = Modifier.padding(padding),
                onDownload = {
                    if (tracked == null) vm.trackThenDownload() else vm.download()
                },
                onTrack = { vm.track() },
                onUntrack = { vm.untrack(); onBack() },
                onUninstall = {
                    // On Samsung One UI the direct ACTION_DELETE uninstaller is instantly
                    // killed by the foreground-check (SGM), so open the app's info screen
                    // in system settings where the user taps "Uninstall". This is the
                    // reliable cross-device path.
                    val detailsIntent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {
                        data = android.net.Uri.parse("package:${info!!.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(detailsIntent) }
                },
                onOpenApp = {
                    val launch = context.packageManager.getLaunchIntentForPackage(info!!.packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launch) }
                    }
                },
            )
        }
    }
}

@Composable
private fun DetailContent(
    info: DetailInfo,
    tracked: ru.app.rustoreupdater.data.db.TrackedAppEntity?,
    appId: String,
    modifier: Modifier,
    onDownload: () -> Unit,
    onTrack: () -> Unit,
    onUntrack: () -> Unit,
    onUninstall: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val installed = tracked?.installedVersionCode?.let { if (it < 0) null else it }
    val isTracked = tracked != null
    val updateAvailable = tracked?.updateAvailable ?: false
    val isInstalledAndActual = isTracked && !updateAvailable
    // Check real installed state live, re-checking whenever the screen resumes
    // (e.g. after returning from the system uninstall settings).
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val liveInstalled = remember(info.packageName, resumeTick) {
        runCatching {
            context.packageManager.getPackageInfo(info.packageName, 0)
            true
        }.getOrDefault(false)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(info.iconUrl, Modifier.size(72.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(info.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    info.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            !isTracked -> Text(
                "Не отслеживается",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            updateAvailable -> AssistChip(
                onClick = onDownload,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                label = {
                    Text(
                        if (installed != null)
                            stringResource(R.string.tracked_update_format, installed.toString(), info.latestVersionCode.toString())
                        else stringResource(R.string.tracked_not_installed),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            )
            else -> Text(
                stringResource(R.string.tracked_up_to_date) + " · v" + (info.latestVersionName ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        val downloading by ru.app.rustoreupdater.download.ActiveDownloads.downloadingApps.collectAsState()
        val installing by ru.app.rustoreupdater.download.ActiveDownloads.installingApps.collectAsState()
        val isDownloading = appId in downloading
        // "Installing" only counts while the app is actually present on the device.
        // If it was uninstalled, any stuck installing flag is ignored so the UI resets.
        val isInstalling = appId in installing && liveInstalled
        val busy = isDownloading || isInstalling

        // Primary action button depends on state.
        when {
            isInstalledAndActual -> Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.detail_btn_open_app))
            }
            !isTracked -> Button(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.search_btn_track))
            }
            else -> Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (isInstalling) R.string.tracked_btn_installing
                            else R.string.tracked_btn_downloading
                        )
                    )
                } else {
                    Text(
                        if (installed == null) stringResource(R.string.detail_btn_download_apk)
                        else stringResource(R.string.detail_btn_update_apk)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Secondary action — only shown for tracked AND installed apps (uninstall).
        if (isTracked && (liveInstalled || installed != null)) {
            OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.detail_btn_uninstall_settings))
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        InfoRow(stringResource(R.string.detail_developer), info.developerName ?: "—")
        InfoRow(stringResource(R.string.detail_version), info.latestVersionName ?: "—")
        InfoRow(stringResource(R.string.detail_size), formatBytes(info.fileSize))
        InfoRow(stringResource(R.string.detail_category), info.category ?: "—")
        info.appVerUpdatedAt?.let { InfoRow(stringResource(R.string.detail_updated), it.take(10)) }

        info.whatsNew?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.detail_whats_new), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        info.fullDescription?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.detail_description), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f МБ".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f КБ".format(bytes / 1_000.0)
    else -> "$bytes Б"
}
