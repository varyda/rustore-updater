package ru.app.rustoreupdater.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.app.rustoreupdater.R
import ru.app.rustoreupdater.selfupdate.SelfUpdateState
import ru.app.rustoreupdater.ui.components.FeedAppCard
import ru.app.rustoreupdater.ui.components.LoadingState
import ru.app.rustoreupdater.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenDetail: (String, String?) -> Unit,
    onOpenAll: (String) -> Unit,
    vm: FeedViewModel = viewModel(),
) {
    val sections by vm.sections.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val selfUpdateState by vm.selfUpdateState.collectAsState()

    // Full-screen loader only on the very first load, when no section has content yet.
    val allEmpty = sections.all { it.items.isEmpty() }
    val anyLoading = sections.any { it.loading }
    val showInitialLoader = allEmpty && anyLoading && !refreshing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_title)) },
                actions = {
                    // Always-available manual self-update check.
                    val isChecking = selfUpdateState is SelfUpdateState.Checking
                    IconButton(onClick = vm::checkForSelfUpdate, enabled = !isChecking) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Проверить обновления")
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showInitialLoader) {
                LoadingState(label = stringResource(R.string.feed_loading))
            } else {
                Column(Modifier.fillMaxSize()) {
                    // Self-update banner: shown only when there is something to act on
                    // (an update available or a download in progress). Hidden when idle,
                    // up-to-date, checking, or errored to keep the feed clean.
                    SelfUpdateBanner(
                        state = selfUpdateState,
                        onDownload = vm::downloadSelfUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = vm::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(
                                items = sections,
                                key = { it.id },
                                contentType = { "section" },
                            ) { section ->
                                FeedSectionItem(
                                    section = section,
                                    onOpenAll = onOpenAll,
                                    onOpenDetail = onOpenDetail,
                                    onRetry = { vm.reloadSection(section.id) },
                                )
                            }
                            // Trailing space so the last row isn't flush against the bottom bar.
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact banner above the feed that announces a new self-update and lets the
 * user start the download without leaving the main screen. Only visible for the
 * [SelfUpdateState.Available] and [SelfUpdateState.Downloading] states.
 */
@Composable
private fun SelfUpdateBanner(
    state: SelfUpdateState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state is SelfUpdateState.Available || state is SelfUpdateState.Downloading,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        when (state) {
            is SelfUpdateState.Available -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Доступно обновление ${state.info.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            state.info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = onDownload) {
                            Text(stringResource(R.string.self_update_download))
                        }
                    }
                }
            }
            is SelfUpdateState.Downloading -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            stringResource(R.string.self_update_downloading, state.info.versionName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            else -> {}
        }
    }
}

/**
 * A whole feed section (header + body), isolated as a composable so the LazyColumn item lambda
 * doesn't inline two composables. With [FeedSection] being @Immutable, Compose can skip
 * recomposing this when the section hasn't changed during scroll.
 */
@Composable
private fun FeedSectionItem(
    section: FeedSection,
    onOpenAll: (String) -> Unit,
    onOpenDetail: (String, String?) -> Unit,
    onRetry: () -> Unit,
) {
    SectionHeader(
        title = stringResource(section.titleRes),
        onAll = { onOpenAll(section.query) },
    )
    SectionBody(
        section = section,
        onOpenDetail = onOpenDetail,
        onRetry = onRetry,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionBody(
    section: FeedSection,
    onOpenDetail: (String, String?) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        section.loading -> {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        section.error -> {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.feed_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        section.items.isEmpty() -> {
            Text(
                stringResource(R.string.feed_section_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        else -> {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                items(
                    items = section.items,
                    key = { it.appId },
                    contentType = { "appCard" },
                ) { item ->
                    FeedAppCard(
                        iconUrl = item.iconUrl,
                        appName = item.appName,
                        companyName = item.companyName,
                        rating = item.averageUserRating,
                        onClick = { onOpenDetail(item.appIdString, item.packageName) },
                    )
                }
            }
        }
    }
}
