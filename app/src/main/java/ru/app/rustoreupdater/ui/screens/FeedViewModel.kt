package ru.app.rustoreupdater.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.R
import ru.app.rustoreupdater.data.network.SearchItemDto
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.selfupdate.SelfUpdateState
import ru.app.rustoreupdater.ui.BaseVm

/**
 * A single category row in the feed.
 *
 * Uses [ImmutableList] for [items] because the Compose compiler treats plain `List` as unstable
 * (it can't prove the backing implementation isn't mutable). An unstable field would make the
 * whole section non-skippable and force recomposition when the section scrolls into view — the
 * root cause of the "micro-lag when a new section appears".
 */
@Immutable
data class FeedSection(
    val id: String,
    val titleRes: Int,
    val query: String,
    val items: ImmutableList<SearchItemDto> = persistentListOf(),
    val loading: Boolean = false,
    val error: Boolean = false,
)

class FeedViewModel(app: Application) : BaseVm(app) {

    private companion object {
        /** Max apps kept per feed row. A row shows ~6–7 at once; more just adds first-appearance cost. */
        const val SECTION_ITEM_CAP = 10
        /** How many leading icons to prefetch into Coil's cache right after a section loads. */
        const val PREFETCH_ICONS = 6
    }

    private val categories = listOf(
        FeedSection(id = "messengers", titleRes = R.string.feed_section_messengers, query = "мессенджер"),
        FeedSection(id = "banking", titleRes = R.string.feed_section_banking, query = "банк"),
        FeedSection(id = "marketplaces", titleRes = R.string.feed_section_marketplaces, query = "маркетплейс"),
        FeedSection(id = "games", titleRes = R.string.feed_section_games, query = "игры"),
        FeedSection(id = "social", titleRes = R.string.feed_section_social, query = "социальные сети"),
        FeedSection(id = "browsers", titleRes = R.string.feed_section_browsers, query = "браузер"),
    )

    private val _sections = MutableStateFlow(categories.map { it.copy(loading = true) })
    val sections: StateFlow<List<FeedSection>> = _sections.asStateFlow()

    private val _tracked = MutableStateFlow<Set<String>>(emptySet())
    val trackedIds: StateFlow<Set<String>> = _tracked.asStateFlow()

    /** True while a (re)load triggered by pull-to-refresh is in flight. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val selfUpdater get() = ServiceLocator.selfUpdater()

    /** Reactive view of the self-update flow, surfaced to the Feed banner. */
    val selfUpdateState: StateFlow<SelfUpdateState> =
        selfUpdater.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SelfUpdateState.Idle)

    /** Query GitHub for a newer build (manual check from the feed toolbar). */
    fun checkForSelfUpdate() {
        viewModelScope.launch { selfUpdater.runCheck() }
    }

    /** Start downloading the APK for an available update. */
    fun downloadSelfUpdate() {
        viewModelScope.launch {
            val info = (selfUpdater.state.value as? SelfUpdateState.Available)?.info ?: return@launch
            val id = selfUpdater.downloadApk(info)
            if (id < 0) selfUpdater.setError("Не удалось начать загрузку")
        }
    }

    init {
        viewModelScope.launch {
            repo.trackedApps.collect { list -> _tracked.value = list.map { it.appId }.toSet() }
        }
        loadAll()
    }

    /** Pull-to-refresh handler: reloads all sections and drives the indicator. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    categories.map { async { loadSection(it) } }.awaitAll()
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Load every category section in parallel; a failure in one section doesn't affect others. */
    fun loadAll() {
        // Reset all sections to loading state.
        _sections.value = categories.map { it.copy(loading = true, error = false, items = persistentListOf()) }
        viewModelScope.launch {
            coroutineScope {
                categories.map { section ->
                    async { loadSection(section) }
                }.awaitAll()
            }
        }
    }

    /** Reload a single section (used by per-section "retry"). */
    fun reloadSection(sectionId: String) {
        val section = categories.firstOrNull { it.id == sectionId } ?: return
        updateSection(sectionId) { it.copy(loading = true, error = false, items = persistentListOf()) }
        viewModelScope.launch { loadSection(section) }
    }

    private suspend fun loadSection(section: FeedSection) {
        try {
            val body = repo.search(section.query)
            // Cap each row: a horizontal row only shows ~6–7 cards at once, so fetching/holding
            // 20+ items only adds composition + icon-loading cost the moment the section appears.
            val items: ImmutableList<SearchItemDto> = body.content
                .filter { it.appId != 0L && it.packageName != null }
                .take(SECTION_ITEM_CAP)
                .toImmutableList()
            updateSection(section.id) { it.copy(items = items, loading = false, error = false) }
            // Warm the icon cache for the first visible cards so that when the user scrolls to
            // this section, icons render instantly instead of triggering a burst of loads on the
            // first frame the section becomes visible (the "micro-lag on new section" symptom).
            prefetchIcons(items.take(PREFETCH_ICONS).mapNotNull { it.iconUrl })
        } catch (e: Exception) {
            Log.e("FeedVM", "section ${section.id} failed", e)
            updateSection(section.id) { it.copy(items = persistentListOf(), loading = false, error = true) }
        }
    }

    /** Fire-and-forget background prefetch of icon URLs into Coil's memory/disk cache. */
    private fun prefetchIcons(urls: List<String>) {
        if (urls.isEmpty()) return
        val loader: ImageLoader = coil.Coil.imageLoader(getApplication())
        val ctx = getApplication<Application>()
        urls.forEach { url ->
            val req = ImageRequest.Builder(ctx).data(url).size(128).build()
            loader.enqueue(req)
        }
    }

    private fun updateSection(sectionId: String, transform: (FeedSection) -> FeedSection) {
        _sections.value = _sections.value.map {
            if (it.id == sectionId) transform(it) else it
        }
    }

    fun track(appId: String, packageName: String) {
        viewModelScope.launch {
            try {
                repo.track(appId, packageName)
            } catch (e: Exception) {
                Log.e("FeedVM", "track failed for $appId / $packageName", e)
            }
        }
    }
}
