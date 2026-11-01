package ru.app.rustoreupdater.download

import android.content.Context

/**
 * Persistent `downloadId -> appId` map for downloads enqueued in the system
 * [android.app.DownloadManager].
 *
 * The system keeps downloading (and later delivers
 * [android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE]) even if Android kills our
 * process in the meantime — which is the normal case for a multi-hundred-megabyte
 * APK while the user switches apps. [ActiveDownloads] is in-memory only, so without
 * this registry the completion broadcast would be dropped and the "ready to
 * install" notification would never appear.
 */
object DownloadRegistry {

    private const val PREFS = "apk_downloads"
    private const val KEY_PREFIX = "dl_"

    fun put(context: Context, downloadId: Long, appId: String) {
        prefs(context).edit().putString(KEY_PREFIX + downloadId, appId).apply()
    }

    fun get(context: Context, downloadId: Long): String? =
        prefs(context).getString(KEY_PREFIX + downloadId, null)

    fun remove(context: Context, downloadId: Long) {
        prefs(context).edit().remove(KEY_PREFIX + downloadId).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
