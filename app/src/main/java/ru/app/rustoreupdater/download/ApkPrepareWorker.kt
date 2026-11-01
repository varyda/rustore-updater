package ru.app.rustoreupdater.download

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.app.rustoreupdater.Notifier
import ru.app.rustoreupdater.data.repo.AppRepository
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.selfupdate.SelfUpdater
import java.io.File

/**
 * Post-processes a finished [android.app.DownloadManager] download: unpacks RuStore's
 * `.zip` artifact when needed (see [ApkArchive]), validates the APK and posts the
 * "ready to install" notification.
 *
 * This runs in WorkManager rather than inside [ApkDownloadReceiver] on purpose: a
 * broadcast receiver only gets a ~10 s grace period ([android.content.BroadcastReceiver.goAsync]),
 * while unpacking a 170+ MB universal APK can take longer than that on a slow phone —
 * the process would be killed halfway, leaving a truncated file and no notification.
 */
class ApkPrepareWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val appId = inputData.getString(KEY_APP_ID)
        if (downloadId <= 0 || appId == null) {
            Log.e(TAG, "missing input data (downloadId=$downloadId, appId=$appId)")
            return Result.failure()
        }

        val context = applicationContext
        val repo: AppRepository = ServiceLocator.appRepository(context)
        val download = ApkDownloader.fileForDownloadId(context, downloadId)

        if (download == null) {
            // The system download did not produce a file: HTTP error, expired link,
            // no space, and so on. Clear the in-flight state and tell the user
            // instead of leaving a spinner that never resolves.
            val reason = ApkDownloader.failureReason(context, downloadId)
                ?: "неизвестная ошибка"
            Log.e(TAG, "download $downloadId for $appId failed: $reason")
            ActiveDownloads.remove(downloadId)
            DownloadRegistry.remove(context, downloadId)
            Notifier.notifyDownloadFailed(context, appLabel(context, repo, appId), reason)
            return Result.success()
        }

        val apk = ApkArchive.resolveApk(download)
        if (apk != null && apk != download) {
            // RuStore archive: keep only the extracted APK to free ~170 MB.
            download.delete()
        }
        ActiveDownloads.remove(downloadId)
        DownloadRegistry.remove(context, downloadId)

        if (apk == null || !apk.exists()) {
            Log.e(TAG, "no installable APK for download $downloadId ($appId)")
            Notifier.notifyDownloadFailed(
                context,
                appLabel(context, repo, appId),
                "скачанный архив повреждён",
            )
            return Result.success()
        }

        val expectedPackage = if (appId == SelfUpdater.SELF_UPDATE_APP_ID) {
            context.packageName
        } else {
            repo.observeApp(appId).first()?.packageName
        }
        val mismatch = expectedPackage?.let { verifyApk(context, apk, it) }
        if (mismatch != null) {
            Log.e(TAG, "rejecting $apk: $mismatch")
            Notifier.notifyDownloadFailed(context, appLabel(context, repo, appId), mismatch)
            return Result.success()
        }

        // Self-update download: route through the dedicated notification.
        if (appId == SelfUpdater.SELF_UPDATE_APP_ID) {
            Log.d(TAG, "self-update APK: ${apk.absolutePath}")
            Notifier.notifySelfUpdateReady(context, apk)
            return Result.success()
        }

        val app = repo.observeApp(appId).first()
        Log.d(TAG, "apk=${apk.absolutePath} app=$app")
        if (app == null) {
            // Tracked app was removed while it was downloading.
            Log.w(TAG, "app $appId is no longer tracked; dropping download")
            Notifier.notifyDownloadFailed(
                context,
                null,
                "приложение удалено из отслеживаемых",
            )
            return Result.success()
        }

        // Move the app from "downloading" to "installing" state until the system
        // confirms the package is installed (handled by PackageStateReceiver).
        ActiveDownloads.markInstalling(app.appId, app.packageName)
        Notifier.notifyDownloadComplete(context, app, apk)
        return Result.success()
    }

    /** Display name for notifications: the tracked app name, or a fallback. */
    private suspend fun appLabel(
        context: Context,
        repo: AppRepository,
        appId: String,
    ): String? = if (appId == SelfUpdater.SELF_UPDATE_APP_ID) {
        context.getString(ru.app.rustoreupdater.R.string.app_name)
    } else {
        repo.observeApp(appId).first()?.appName
    }

    /**
     * Validates the downloaded APK before offering it to the installer.
     *
     * RuStore occasionally serves an older build, and a wrong or corrupted archive
     * would only fail later inside the system installer with a cryptic message.
     *
     * @return a user-facing problem description, or `null` when the APK is fine.
     */
    private fun verifyApk(context: Context, apk: File, expectedPackage: String): String? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return "скачанный файл не является APK"
        if (archive.packageName != expectedPackage) {
            return "в архиве другое приложение: ${archive.packageName}"
        }
        val installed = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(expectedPackage, 0)
        }.getOrNull() ?: return null // not installed yet — nothing to compare against
        val installedCode = PackageInfoCompat.getLongVersionCode(installed)
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        if (archiveCode < installedCode) {
            return "в RuStore более старая версия ($archiveCode < $installedCode)"
        }
        return null
    }

    companion object {
        private const val TAG = "ApkPrepareWorker"

        const val KEY_DOWNLOAD_ID = "downloadId"
        const val KEY_APP_ID = "appId"

        /** Unique work name — one job per system download id. */
        fun workName(downloadId: Long) = "apk-prepare-$downloadId"
    }
}
