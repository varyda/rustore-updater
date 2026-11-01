package ru.app.rustoreupdater.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Listens for [DownloadManager.ACTION_DOWNLOAD_COMPLETE] for APK downloads that we
 * enqueued via [ApkDownloader] and hands the id over to [ApkPrepareWorker], which
 * unpacks RuStore's `.zip` artifact when needed (see [ApkArchive]) and posts the
 * "ready to install" notification.
 *
 * Nothing heavy is done here: a receiver only gets a short grace period, and
 * unpacking a 170+ MB universal APK does not fit into it.
 */
class ApkDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        Log.d(TAG, "DOWNLOAD_COMPLETE id=$downloadId")
        if (downloadId <= 0) return

        // The in-memory map only exists while this process has been alive since the
        // download was enqueued; fall back to the persisted registry, otherwise a
        // download that finished after a process restart would be ignored.
        // Read (do not take) the registry entry: the worker clears it once it has
        // actually handled the file, so a job that never runs cannot lose it.
        val appId = ActiveDownloads.appIdFor(downloadId)
            ?: DownloadRegistry.get(context, downloadId)
        Log.d(TAG, "mapped appId=$appId for downloadId=$downloadId")
        if (appId == null) {
            // Not our download; ignore.
            return
        }

        val request = OneTimeWorkRequestBuilder<ApkPrepareWorker>()
            // Run right away: the user has just finished a download and expects the
            // "ready to install" notification, not a job deferred until Doze ends.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    ApkPrepareWorker.KEY_DOWNLOAD_ID to downloadId,
                    ApkPrepareWorker.KEY_APP_ID to appId,
                )
            )
            .build()
        // KEEP: a duplicated DOWNLOAD_COMPLETE broadcast must not start a second
        // extraction over the same file.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ApkPrepareWorker.workName(downloadId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val TAG = "ApkDownloadReceiver"
    }
}
