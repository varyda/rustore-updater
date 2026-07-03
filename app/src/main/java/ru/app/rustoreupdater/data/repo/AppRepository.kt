package ru.app.rustoreupdater.data.repo

import android.content.Context
import android.content.pm.PackageManager
import ru.app.rustoreupdater.data.db.AppDao
import ru.app.rustoreupdater.data.db.AppDatabase
import ru.app.rustoreupdater.data.db.TrackedAppEntity
import ru.app.rustoreupdater.data.network.DownloadLinkRequest
import ru.app.rustoreupdater.data.network.OverallInfoBodyDto
import ru.app.rustoreupdater.data.network.RuStoreApi
import ru.app.rustoreupdater.data.network.SearchResultBodyDto

/**
 * Single entry point for app data: RuStore network calls + local DB + installed-app detection.
 *
 * NOTE on identifiers:
 *  - The RuStore `overallInfo` endpoint takes the **package name** (e.g. "ru.russianhighways.mobile"),
 *    NOT the numeric appId — passing a number returns `code: ERROR`.
 *  - The `download-link` endpoint takes the numeric **appId**.
 * We store the numeric appId as the primary key and keep packageName alongside.
 */
class AppRepository(
    private val context: Context,
    private val api: RuStoreApi,
    private val dao: AppDao,
) {

    val trackedApps = dao.observeAll()

    fun observeApp(appId: String) = dao.observe(appId)

    suspend fun search(query: String, page: Int = 0): SearchResultBodyDto {
        val res = api.search(query = query, pageNumber = page)
        if (res.code != "OK") error("Search failed: ${res.code}")
        return res.body ?: error("Empty search body")
    }

    /** overallInfo is keyed by package name. */
    suspend fun fetchOverallInfo(packageName: String): OverallInfoBodyDto {
        val res = api.getOverallInfo(packageName)
        if (res.code != "OK") error("Info failed: ${res.code}")
        return res.body ?: error("Empty info body for $packageName")
    }

    /** download-link is keyed by numeric appId. */
    suspend fun fetchDownloadLink(appId: String): String {
        val res = api.getDownloadLink(DownloadLinkRequest(appId = appId))
        if (res.code != "OK") error("Download link failed: ${res.code}")
        val url = res.body?.downloadUrls?.firstOrNull()?.url
            ?: error("No download URL for $appId")
        // RuStore sometimes returns .zip suffix that is actually an .apk
        return url.replace(Regex("\\.zip$"), ".apk")
    }

    /** Detect the version code of an installed package, or null if not installed. */
    fun installedVersionCode(packageName: String): Long? = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        android.util.Log.d("AppRepo", "installedVersionCode($packageName) = $code")
        code
    } catch (e: PackageManager.NameNotFoundException) {
        android.util.Log.d("AppRepo", "installedVersionCode($packageName) = null (not found)")
        null
    }

    suspend fun isTracked(appId: String): Boolean = dao.count(appId) > 0

    /** Track a search result: appId is numeric, packageName is used for overallInfo. */
    suspend fun track(appId: String, packageName: String) {
        val info = fetchOverallInfo(packageName)
        val installed = installedVersionCode(info.packageName ?: packageName)
        val latestCode = info.versionCode
        dao.upsert(
            TrackedAppEntity(
                appId = appId,
                packageName = info.packageName ?: packageName,
                appName = info.appName ?: packageName,
                iconUrl = info.iconUrl,
                developerName = info.companyName,
                category = info.category,
                shortDescription = info.shortDescription,
                fullDescription = info.fullDescription,
                whatsNew = info.whatsNew,
                latestVersionName = info.versionName,
                latestVersionCode = latestCode,
                fileSize = info.fileSize,
                appVerUpdatedAt = info.appVerUpdatedAt,
                installedVersionCode = installed,
                updateAvailable = installed == null || latestCode > installed,
                addedAt = System.currentTimeMillis(),
                lastCheckAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun untrack(appId: String) {
        dao.delete(appId)
    }

    /**
     * Re-check every tracked app: refresh latest version from RuStore (by packageName) and
     * reconcile with the version installed on the device. Returns the list of apps with a
     * newly-detected available update.
     */
    suspend fun checkForUpdates(): List<TrackedAppEntity> {
        val now = System.currentTimeMillis()
        val updates = mutableListOf<TrackedAppEntity>()
        for (app in dao.getAll()) {
            try {
                val info = fetchOverallInfo(app.packageName)
                val latestCode = info.versionCode
                val installed = installedVersionCode(app.packageName)
                val updateAvailable = installed == null || latestCode > installed
                dao.updateLatestInfo(
                    appId = app.appId,
                    versionName = info.versionName,
                    versionCode = latestCode,
                    fileSize = info.fileSize,
                    whatsNew = info.whatsNew,
                    appVerUpdatedAt = info.appVerUpdatedAt,
                    updateAvailable = updateAvailable,
                    lastCheckAt = now,
                )
                if (updateAvailable) {
                    dao.get(app.appId)?.let { if (it.updateAvailable) updates.add(it) }
                }
            } catch (e: Exception) {
                // Skip this app on network failure; the next run retries.
            }
        }
        return updates
    }

    /** Refresh the "installed" state for a single app (call after install completes). */
    suspend fun reconcileInstalled(appId: String) {
        val app = dao.get(appId) ?: return
        val installed = installedVersionCode(app.packageName)
        val updateAvailable = installed == null || app.latestVersionCode > installed
        dao.setInstalled(appId, installed ?: -1L, updateAvailable)
    }

    /**
     * Reconcile the installed state for any tracked app matching [packageName].
     * Called by [ru.app.rustoreupdater.receiver.PackageStateReceiver] when the system
     * reports that an app was installed, replaced, or removed.
     */
    suspend fun reconcilePackage(packageName: String) {
        for (app in dao.getAll()) {
            if (app.packageName == packageName) {
                val installed = installedVersionCode(packageName)
                val updateAvailable = installed == null || app.latestVersionCode > installed
                dao.setInstalled(app.appId, installed ?: -1L, updateAvailable)
            }
        }
    }

    /** Force a re-evaluation of updateAvailable for all tracked apps. */
    suspend fun reconcileAllInstalled() {
        for (app in dao.getAll()) {
            val installed = installedVersionCode(app.packageName)
            val updateAvailable = installed == null || app.latestVersionCode > installed
            dao.setInstalled(app.appId, installed ?: -1L, updateAvailable)
        }
    }

    companion object {
        fun fromContext(context: Context, api: RuStoreApi): AppRepository =
            AppRepository(context, api, AppDatabase.get(context).appDao())
    }
}
