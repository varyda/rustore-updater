package ru.app.rustoreupdater.ui.screens

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.data.db.TrackedAppEntity
import ru.app.rustoreupdater.download.ApkDownloader
import ru.app.rustoreupdater.ui.BaseVm

class TrackedViewModel(app: Application) : BaseVm(app) {

    val apps: StateFlow<List<TrackedAppEntity>> =
        repo.trackedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checking = MutableStateFlow(false)
    val checking = _checking.asStateFlow()

    fun checkNow() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            try {
                repo.checkForUpdates()
            } finally {
                _checking.value = false
            }
        }
    }

    fun download(appId: String) {
        viewModelScope.launch {
            val downloader = ApkDownloader(getApplication(), repo)
            downloader.download(appId)
        }
    }

    fun untrack(appId: String) {
        viewModelScope.launch { repo.untrack(appId) }
    }

    fun reconcile() {
        viewModelScope.launch { repo.reconcileAllInstalled() }
    }
}
