package ru.app.rustoreupdater.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic [UpdateCheckWorker]. WorkManager keeps periodic work
 * registered across reboots, so a BOOT_COMPLETED receiver is not required.
 */
object UpdateScheduler {

    private const val DEFAULT_INTERVAL_HOURS = 6L

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

    /** Convenience used at app start: schedules with the stored or default interval. */
    fun ensureScheduled(context: Context) {
        schedule(context, DEFAULT_INTERVAL_HOURS.toInt())
    }

    /** Run an immediate one-off check (used by the "Check now" button). */
    fun runNow(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).build()
        // Re-schedule immediately by replacing the existing periodic work with a 0 initial delay.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(DEFAULT_INTERVAL_HOURS, TimeUnit.HOURS)
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UpdateCheckWorker.UNIQUE_NAME)
    }
}
