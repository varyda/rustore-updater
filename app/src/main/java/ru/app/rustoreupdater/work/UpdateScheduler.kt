package ru.app.rustoreupdater.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.app.rustoreupdater.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic [UpdateCheckWorker]. WorkManager keeps periodic work
 * registered across reboots, so a BOOT_COMPLETED receiver is not required.
 */
object UpdateScheduler {

    private const val DEFAULT_INTERVAL_HOURS = 6

    /** Ensure a periodic check is registered with the given interval (hours). */
    fun schedule(context: Context, intervalHours: Int) {
        val hours = intervalHours.coerceAtLeast(1).toLong()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(hours, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Called at app start: registers the periodic check **only when none is active**,
     * using the interval the user picked in Settings.
     *
     * Re-enqueueing unconditionally with a hardcoded interval (the previous
     * behaviour) silently reset the user's choice back to 6 hours on every launch.
     */
    suspend fun ensureScheduled(context: Context) {
        val ctx = context.applicationContext
        val infos: List<WorkInfo> = withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(ctx)
                    .getWorkInfosForUniqueWork(UpdateCheckWorker.UNIQUE_NAME)
                    .get()
            }.getOrDefault(emptyList())
        }
        if (infos.any { !it.state.isFinished }) return
        val hours = runCatching { ServiceLocator.settingsStore.intervalHours.first() }
            .getOrDefault(DEFAULT_INTERVAL_HOURS)
        schedule(ctx, hours)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UpdateCheckWorker.UNIQUE_NAME)
    }
}
