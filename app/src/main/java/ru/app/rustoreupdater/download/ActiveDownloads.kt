package ru.app.rustoreupdater.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory tracking of downloads and installs.
 *
 * Two distinct reactive sets drive the UI:
 *  - [downloadingApps]: appIds with an active APK download (DownloadManager in progress).
 *  - [installingApps]: appIds whose APK finished downloading and is being installed
 *    (from the install dialog opening until the system confirms the package is present).
 *
 * A normal flow for one app:
 *   download start   -> downloadingApps += appId
 *   download finish  -> downloadingApps -= appId, installingApps += appId
 *   system confirms  -> installingApps -= appId   (handled by PackageStateReceiver)
 */
object ActiveDownloads {
    private val byAppId = ConcurrentHashMap<String, Long>()
    private val byDownloadId = ConcurrentHashMap<Long, String>()

    private val _downloadingApps = MutableStateFlow<Set<String>>(emptySet())
    /** Reactive set of appIds that currently have an active APK download. */
    val downloadingApps: StateFlow<Set<String>> = _downloadingApps.asStateFlow()

    private val _installingApps = MutableStateFlow<Set<String>>(emptySet())
    /** Reactive set of appIds whose APK is downloaded and being installed. */
    val installingApps: StateFlow<Set<String>> = _installingApps.asStateFlow()

    /** Maps packageName -> appId for apps currently in the installing state, so the
     *  PackageStateReceiver (which only knows the package name) can clear the state. */
    private val installingPackageNames = ConcurrentHashMap<String, String>()

    fun put(appId: String, downloadId: Long) {
        byAppId[appId] = downloadId
        byDownloadId[downloadId] = appId
        _downloadingApps.value = _downloadingApps.value + appId
    }

    fun appIdFor(downloadId: Long): String? = byDownloadId[downloadId]

    fun downloadIdFor(appId: String): Long? = byAppId[appId]

    fun isDownloading(appId: String): Boolean = byAppId.containsKey(appId)

    fun remove(appId: String) {
        val id = byAppId.remove(appId)
        if (id != null) byDownloadId.remove(id)
        _downloadingApps.value = _downloadingApps.value - appId
    }

    fun remove(downloadId: Long) {
        val appId = byDownloadId.remove(downloadId)
        if (appId != null) {
            byAppId.remove(appId)
            _downloadingApps.value = _downloadingApps.value - appId
        }
    }

    /** Mark [appId] / [packageName] as installing (download finished, awaiting system install). */
    fun markInstalling(appId: String, packageName: String) {
        _installingApps.value = _installingApps.value + appId
        installingPackageNames[packageName] = appId
    }

    /** Clear the installing state for [appId] — called once the package is present. */
    fun clearInstalling(appId: String) {
        _installingApps.value = _installingApps.value - appId
        installingPackageNames.entries.removeIf { it.value == appId }
    }

    /** Clear the installing state by package name (used by PackageStateReceiver). */
    fun clearInstallingByPackage(packageName: String) {
        val appId = installingPackageNames.remove(packageName)
        if (appId != null) {
            _installingApps.value = _installingApps.value - appId
        }
    }
}
