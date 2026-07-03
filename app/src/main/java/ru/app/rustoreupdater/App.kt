package ru.app.rustoreupdater

import android.app.Application
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.work.UpdateScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Notifier.ensureChannels(this)
        // Schedule periodic update checks (idempotent — safe to call on every start).
        UpdateScheduler.ensureScheduled(this)
    }
}
