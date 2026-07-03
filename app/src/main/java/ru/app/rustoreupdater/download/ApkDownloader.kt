package ru.app.rustoreupdater.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first
import ru.app.rustoreupdater.data.repo.AppRepository
import java.io.File

/**
 * Downloads an APK via the system [DownloadManager].
 *
 * The download is saved to the app's external files dir under `downloads/` so that
 * a [androidx.core.content.FileProvider] can hand it to the installer without
 * scoped-storage restrictions.
 */
class ApkDownloader(private val context: Context, private val repository: AppRepository) {

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Starts downloading the latest APK for [appId]. Resolves the direct URL from
     * RuStore, enqueues the system download, and remembers the download id so the
     * [ApkDownloadReceiver] can pick it up when finished.
     *
     * @return the system [DownloadManager] id, or -1 on failure.
     */
    suspend fun download(appId: String): Long {
        // Avoid duplicate concurrent downloads for the same app.
        ActiveDownloads.downloadIdFor(appId)?.let { existing ->
            Log.d(TAG, "download skipped: already in progress for $appId (id=$existing)")
            return existing
        }
        return try {
            val url = repository.fetchDownloadLink(appId)
            Log.d(TAG, "Resolved APK url for $appId: $url")
            val tracked = repository.observeApp(appId).first()
            val safeName = (tracked?.packageName ?: appId).replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = apkFile(context, "$safeName.apk")
            // Clean up any previous copy to avoid collisions.
            target.delete()
            target.parentFile?.mkdirs()

            val request = DownloadManager.Request(url.toUri()).apply {
                setTitle(tracked?.appName ?: "RuStore APK")
                setDescription("Загрузка APK из RuStore")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationUri(Uri.fromFile(target))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", USER_AGENT)
            }
            val id = downloadManager.enqueue(request)
            Log.d(TAG, "Enqueued download id=$id -> ${target.absolutePath}")
            ActiveDownloads.put(appId, id)
            id
        } catch (e: Exception) {
            Log.e(TAG, "download failed for $appId", e)
            -1L
        }
    }

    companion object {
        private const val TAG = "ApkDownloader"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** Directory holding downloaded APK files. */
        fun downloadsDir(context: Context): File =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "downloads").apply {
                if (!exists()) mkdirs()
            }

        fun apkFile(context: Context, name: String): File = File(downloadsDir(context), name)

        /** Resolve the on-disk file produced by a finished download id. */
        fun fileForDownloadId(context: Context, downloadId: Long): File? {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
            cursor.use {
                if (!it.moveToFirst()) return null
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status != DownloadManager.STATUS_SUCCESSFUL) return null
                val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    ?: return null
                return File(Uri.parse(localUri).path ?: return null)
            }
        }
    }
}
