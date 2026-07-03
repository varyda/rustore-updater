package ru.app.rustoreupdater.ui.screens

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.download.ApkDownloader
import ru.app.rustoreupdater.ui.BaseVm

class DetailViewModel(app: Application, private val handle: SavedStateHandle) : BaseVm(app) {

    val appId: String = handle["appId"] ?: ""
    private val packageNameArg: String? = handle["packageName"]

    /** Tracked-app state from the DB (null if not tracked yet). */
    val app = repo.observeApp(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Full info loaded on demand from RuStore; non-null once loaded. */
    private val _info = MutableStateFlow<DetailInfo?>(null)
    val info: StateFlow<DetailInfo?> = _info.asStateFlow()

    private val _loadingInfo = MutableStateFlow(true)
    val loadingInfo: StateFlow<Boolean> = _loadingInfo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadInfo()
    }

    private fun loadInfo() {
        viewModelScope.launch {
            _loadingInfo.value = true
            _error.value = null
            try {
                // Prefer the tracked record's packageName; fall back to the arg from search.
                val pkg = app.value?.packageName ?: packageNameArg ?: appId
                val data = repo.fetchOverallInfo(pkg)
                _info.value = DetailInfo(
                    appName = data.appName ?: pkg,
                    packageName = data.packageName ?: pkg,
                    developerName = data.companyName,
                    iconUrl = data.iconUrl,
                    latestVersionName = data.versionName,
                    latestVersionCode = data.versionCode,
                    fileSize = data.fileSize,
                    category = data.category,
                    appVerUpdatedAt = data.appVerUpdatedAt,
                    whatsNew = data.whatsNew,
                    fullDescription = data.fullDescription,
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Ошибка загрузки"
            } finally {
                _loadingInfo.value = false
            }
        }
    }

    fun download() {
        viewModelScope.launch {
            val downloader = ApkDownloader(getApplication(), repo)
            downloader.download(appId)
        }
    }

    fun trackThenDownload() {
        viewModelScope.launch {
            val pkg = app.value?.packageName ?: packageNameArg ?: return@launch
            try {
                repo.track(appId, pkg)
            } catch (_: Exception) {
            }
            val downloader = ApkDownloader(getApplication(), repo)
            downloader.download(appId)
        }
    }

    fun track() {
        viewModelScope.launch {
            val pkg = app.value?.packageName ?: packageNameArg ?: return@launch
            try {
                repo.track(appId, pkg)
            } catch (_: Exception) {
            }
        }
    }

    fun untrack() {
        viewModelScope.launch { repo.untrack(appId) }
    }

    fun reconcile() {
        viewModelScope.launch { repo.reconcileInstalled(appId) }
    }
}

/** Immutable detail info derived from overallInfo, decoupled from the DB entity. */
data class DetailInfo(
    val appName: String,
    val packageName: String,
    val developerName: String?,
    val iconUrl: String?,
    val latestVersionName: String?,
    val latestVersionCode: Long,
    val fileSize: Long,
    val category: String?,
    val appVerUpdatedAt: String?,
    val whatsNew: String?,
    val fullDescription: String?,
)
