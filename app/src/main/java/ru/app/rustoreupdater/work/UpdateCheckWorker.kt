package ru.app.rustoreupdater.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.app.rustoreupdater.Notifier
import ru.app.rustoreupdater.di.ServiceLocator

/**
 * Periodic background worker that re-checks every tracked app against RuStore and
 * posts an update notification for each app that has a newer version available.
 *
 * If the user enabled auto-download, the APK is fetched via [ApkDownloader] right
 * away, so the download-complete notification (which carries the install action)
 * appears without further interaction.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ServiceLocator.appRepository(applicationContext)
        val autoDownload = ServiceLocator.settingsStore.autoDownload.first()

        val updates = try {
            repo.checkForUpdates()
        } catch (e: Exception) {
            return Result.retry()
        }

        if (updates.isEmpty()) return Result.success()

        for (app in updates) {
            Notifier.notifyUpdateAvailable(applicationContext, app)
            if (autoDownload) {
                // Best-effort background download; failures are non-fatal.
                try {
                    val downloader = ru.app.rustoreupdater.download.ApkDownloader(applicationContext, repo)
                    downloader.download(app.appId)
                } catch (_: Exception) {
                    // Ignore; the user can manually trigger it from the app.
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "rustore_update_check"
    }
}
