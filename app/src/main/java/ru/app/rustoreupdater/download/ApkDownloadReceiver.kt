package ru.app.rustoreupdater.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.Notifier
import ru.app.rustoreupdater.data.repo.AppRepository
import ru.app.rustoreupdater.di.ServiceLocator

/**
 * Listens for [DownloadManager.ACTION_DOWNLOAD_COMPLETE] for APK downloads that we
 * enqueued via [ApkDownloader]. On success it posts a "ready to install"
 * notification wired to launch the system installer.
 */
class ApkDownloadReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        Log.d(TAG, "DOWNLOAD_COMPLETE id=$downloadId")
        if (downloadId <= 0) return

        val appId = ActiveDownloads.appIdFor(downloadId)
        Log.d(TAG, "mapped appId=$appId for downloadId=$downloadId")
        if (appId == null) {
            // Not our download; ignore.
            return
        }
        val repo: AppRepository = ServiceLocator.appRepository(context)

        scope.launch {
            val file = ApkDownloader.fileForDownloadId(context, downloadId)
            ActiveDownloads.remove(downloadId)
            val app = repo.observeApp(appId).first()
            Log.d(TAG, "file=${file?.absolutePath} exists=${file?.exists()} app=$app")
            if (file != null && file.exists() && app != null) {
                // Move the app from "downloading" to "installing" state until the system
                // confirms the package is installed (handled by PackageStateReceiver).
                ActiveDownloads.markInstalling(app.appId, app.packageName)
                Notifier.notifyDownloadComplete(context, app, file)
            }
        }
    }

    companion object {
        private const val TAG = "ApkDownloadReceiver"
    }
}
