package ru.app.rustoreupdater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.app.rustoreupdater.di.ServiceLocator

/**
 * Base [AndroidViewModel] that exposes the shared dependencies to screens.
 */
open class BaseVm(app: Application) : AndroidViewModel(app) {
    protected val repo get() = ServiceLocator.appRepository(getApplication())
    protected val settings get() = ServiceLocator.settingsStore
}

/** Generic factory that constructs ViewModels needing only the Application. */
class AppVmFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            else -> super.create(modelClass)
        }
    }
}
