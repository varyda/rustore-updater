package ru.app.rustoreupdater.ui.screens

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.data.repo.CheckInterval
import ru.app.rustoreupdater.ui.BaseVm
import ru.app.rustoreupdater.work.UpdateScheduler

class SettingsViewModel(app: Application) : BaseVm(app) {

    val intervalHours: StateFlow<Int> =
        settings.intervalHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CheckInterval.DEFAULT.hours)

    val autoDownload: StateFlow<Boolean> =
        settings.autoDownload.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setInterval(hours: Int) {
        viewModelScope.launch {
            settings.setInterval(hours)
            UpdateScheduler.schedule(getApplication(), hours)
        }
    }

    fun setAutoDownload(value: Boolean) {
        viewModelScope.launch { settings.setAutoDownload(value) }
    }
}
