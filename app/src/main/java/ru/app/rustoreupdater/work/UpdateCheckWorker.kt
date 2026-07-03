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

        // Self-update check (best-effort, never blocks RuStore checks).
        runCatching {
            val checkSelf = ServiceLocator.settingsStore.checkSelfUpdates.first()
            if (checkSelf) {
                checkSelfUpdate()
            }
        }

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

    /**
     * Fetches the latest [ru.app.rustoreupdater.selfupdate.UpdateInfo] from GitHub
     * and posts a notification when a newer build of this app exists. Runs as
     * part of the periodic update check so no extra scheduler is required.
     */
    private suspend fun checkSelfUpdate() {
        val updater = ServiceLocator.selfUpdater()
        val info = updater.fetchLatest() ?: return
        if (updater.isNewer(info)) {
            Notifier.notifySelfUpdateAvailable(applicationContext, info.versionName)
        }
    }

    companion object {
        const val UNIQUE_NAME = "rustore_update_check"
    }
}
