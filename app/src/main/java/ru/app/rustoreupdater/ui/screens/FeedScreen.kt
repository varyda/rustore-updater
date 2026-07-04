package ru.app.rustoreupdater.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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

    // Full-screen loader only on the very first load, when no section has content yet.
    val allEmpty = sections.all { it.items.isEmpty() }
    val anyLoading = sections.any { it.loading }
    val showInitialLoader = allEmpty && anyLoading && !refreshing

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.feed_title)) })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showInitialLoader) {
                LoadingState(label = stringResource(R.string.feed_loading))
            } else {
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
