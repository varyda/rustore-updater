package ru.app.rustoreupdater.ui.screens

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.data.repo.CheckInterval
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.selfupdate.SelfUpdateState
import ru.app.rustoreupdater.ui.BaseVm
import ru.app.rustoreupdater.work.UpdateScheduler

class SettingsViewModel(app: Application) : BaseVm(app) {

    val intervalHours: StateFlow<Int> =
        settings.intervalHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CheckInterval.DEFAULT.hours)

    val autoDownload: StateFlow<Boolean> =
        settings.autoDownload.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val checkSelfUpdates: StateFlow<Boolean> =
        settings.checkSelfUpdates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Reactive view of the self-update flow, surfaced to the Settings screen. */
    val selfUpdateState: StateFlow<SelfUpdateState> =
        selfUpdater.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SelfUpdateState.Idle)

    /** Version name of the currently installed build (e.g. "1.1"). */
    val installedVersionName: String get() = selfUpdater.installedVersionName

    fun setInterval(hours: Int) {
        viewModelScope.launch {
            settings.setInterval(hours)
            UpdateScheduler.schedule(getApplication(), hours)
        }
    }

    fun setAutoDownload(value: Boolean) {
        viewModelScope.launch { settings.setAutoDownload(value) }
    }

    fun setCheckSelfUpdates(value: Boolean) {
        viewModelScope.launch { settings.setCheckSelfUpdates(value) }
    }

    /** Query GitHub for a newer build and update [selfUpdateState]. */
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

    private val selfUpdater get() = ServiceLocator.selfUpdater()
}
