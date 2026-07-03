package ru.app.rustoreupdater.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.data.network.SearchItemDto
import ru.app.rustoreupdater.ui.BaseVm

class SearchViewModel(app: Application) : BaseVm(app) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchItemDto>>(emptyList())
    val results: StateFlow<List<SearchItemDto>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tracked = MutableStateFlow<Set<String>>(emptySet())
    val trackedIds: StateFlow<Set<String>> = _tracked.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repo.trackedApps.collect { list -> _tracked.value = list.map { it.appId }.toSet() }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _results.value = emptyList()
            _error.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce
            runSearch(q)
        }
    }

    private suspend fun runSearch(q: String) {
        _loading.value = true
        _error.value = null
        try {
            val body = repo.search(q)
            _results.value = body.content.filter { it.appId != 0L && it.packageName != null }
        } catch (e: Exception) {
            _results.value = emptyList()
            _error.value = e.message ?: "error"
        } finally {
            _loading.value = false
        }
    }

    fun track(appId: String, packageName: String) {
        viewModelScope.launch {
            try {
                repo.track(appId, packageName)
            } catch (e: Exception) {
                Log.e("SearchVM", "track failed for $appId / $packageName", e)
            }
        }
    }
}
