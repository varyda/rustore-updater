package ru.app.rustoreupdater.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ru.app.rustoreupdater.R
import ru.app.rustoreupdater.data.network.SearchItemDto
import ru.app.rustoreupdater.ui.components.EmptyState
import ru.app.rustoreupdater.ui.components.ErrorState
import ru.app.rustoreupdater.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenDetail: (String, String?) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: SearchViewModel = viewModel(),
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val tracked by vm.trackedIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.readOnly) query.ifBlank { stringResource(R.string.nav_search) } else stringResource(R.string.nav_search)) },
                navigationIcon = {
                    if (vm.readOnly && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Hide the editable field in read-only (feed "All") mode.
            if (!vm.readOnly) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            when {
                loading -> LoadingState(label = stringResource(R.string.search_loading))
                error != null -> ErrorState(message = stringResource(R.string.search_error) + ": " + error)
                query.isBlank() -> EmptyState(title = stringResource(R.string.search_empty))
                results.isEmpty() -> EmptyState(title = stringResource(R.string.search_no_results))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.appId }) { item ->
                        SearchRow(
                            item = item,
                            isTracked = item.appIdString in tracked,
                            onTrack = { vm.track(item.appIdString, item.packageName ?: item.appIdString) },
                            onClick = { onOpenDetail(item.appIdString, item.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    item: SearchItemDto,
    isTracked: Boolean,
    onTrack: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.iconUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.appName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.packageName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.shortDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onTrack, enabled = !isTracked) {
                Icon(
                    if (isTracked) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isTracked) stringResource(R.string.search_btn_tracked)
                    else stringResource(R.string.search_btn_track),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
