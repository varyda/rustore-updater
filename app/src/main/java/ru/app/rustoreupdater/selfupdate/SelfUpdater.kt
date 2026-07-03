package ru.app.rustoreupdater.selfupdate

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.download.ActiveDownloads
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Self-update engine: checks GitHub for a newer build of *this* app and, when
 * found, downloads the APK through the system [DownloadManager] (reusing the
 * same plumbing as RuStore app downloads) so the existing [ApkDownloadReceiver]
 * + [ru.app.rustoreupdater.install.ApkInstaller] chain handles the install.
 *
 * Flow:
 *   1. [fetchLatest]  — GET `update.json` from the releases latest URL.
 *   2. compare        — installed [installedVersionCode] vs [UpdateInfo.versionCode].
 *   3. [downloadApk]  — enqueue a system download tagged with [SELF_UPDATE_APP_ID].
 *
 * The `SELF_UPDATE_APP_ID` sentinel lets [ApkDownloadReceiver] route the finished
 * download through the self-update notification instead of a tracked-app one.
 */
class SelfUpdater(private val context: Context) {

    /** Sentinel appId used to distinguish self-update downloads from app downloads. */
    val apkFile: File
        get() = File(downloadsDir(), "rustore-updater-self.apk")

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _state = MutableStateFlow<SelfUpdateState>(SelfUpdateState.Idle)
    /** Reactive view of the current self-update flow for the UI. */
    val state: StateFlow<SelfUpdateState> = _state.asStateFlow()

    /**
     * Fetch [UpdateInfo] from GitHub. Returns `null` on network/parse errors.
     * Runs on [Dispatchers.IO].
     */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(MANIFEST_URL).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "fetchLatest HTTP ${resp.code}")
                    return@runCatching null
                }
                val body = resp.body?.string() ?: return@runCatching null
                json.decodeFromString(UpdateInfo.serializer(), body)
            }
        }.getOrElse { e ->
            Log.e(TAG, "fetchLatest failed", e); null
        }
    }

    /** True when the published [UpdateInfo] is newer than the running build. */
    fun isNewer(info: UpdateInfo): Boolean = info.versionCode > installedVersionCode

    /** The version code of the currently installed APK. */
    val installedVersionCode: Int by lazy {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pi.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION") pi.versionCode
            }
        }.getOrDefault(0)
    }

    /** The version name of the currently installed APK (e.g. "1.0"). */
    val installedVersionName: String by lazy {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("0.0")
    }

    /**
     * Enqueue the APK download via the system DownloadManager. The download is
     * tagged with [SELF_UPDATE_APP_ID] so [ApkDownloadReceiver] recognises it.
     *
     * @return the DownloadManager id, or -1 on failure.
     */
    suspend fun downloadApk(info: UpdateInfo): Long = withContext(Dispatchers.IO) {
        // Avoid stacking concurrent self-update downloads.
        if (ActiveDownloads.isDownloading(SELF_UPDATE_APP_ID)) {
            Log.d(TAG, "self-update download already in progress")
            return@withContext ActiveDownloads.downloadIdFor(SELF_UPDATE_APP_ID) ?: -1L
        }
        runCatching {
            apkFile.delete()                  // clear any stale copy
            apkFile.parentFile?.mkdirs()

            val request = DownloadManager.Request(info.apkUrl.toUri()).apply {
                setTitle("Обновление RuStore Updater ${info.versionName}")
                setDescription("Скачивание новой версии ${info.versionName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationUri(Uri.fromFile(apkFile))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", USER_AGENT)
            }
            val id = downloadManager.enqueue(request)
            ActiveDownloads.put(SELF_UPDATE_APP_ID, id)
            _state.value = SelfUpdateState.Downloading(info)
            Log.d(TAG, "enqueued self-update download id=$id -> ${apkFile.absolutePath}")
            id
        }.getOrElse { e ->
            Log.e(TAG, "downloadApk failed", e); -1L
        }
    }

    /** Reset transient UI state back to idle (e.g. after the user dismissed a dialog). */
    fun resetState() { _state.value = SelfUpdateState.Idle }

    /**
     * Run a full check cycle against GitHub and publish the resulting state to
     * [state]: [SelfUpdateState.Available], [SelfUpdateState.UpToDate], or
     * [SelfUpdateState.Error]. Used by the Settings screen "Check for updates".
     */
    suspend fun runCheck() {
        _state.value = SelfUpdateState.Checking
        val info = fetchLatest()
        _state.value = when {
            info == null -> SelfUpdateState.Error("Не удалось проверить обновления")
            isNewer(info) -> SelfUpdateState.Available(info)
            else -> SelfUpdateState.UpToDate
        }
    }

    /** Update the state to an error (used when a download fails to start). */
    fun setError(message: String) { _state.value = SelfUpdateState.Error(message) }

    private fun downloadsDir(): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "downloads").apply {
            if (!exists()) mkdirs()
        }

    companion object {
        private const val TAG = "SelfUpdater"

        /** Sentinel appId used in [ActiveDownloads] for self-update APK downloads. */
        const val SELF_UPDATE_APP_ID = "__self_update__"

        /**
         * Raw URL of `update.json` attached to the rolling `latest` release.
         * `raw.githubusercontent.com` works for default branch files; for release
         * assets we use the `releases/download` URL which serves the asset directly.
         */
        const val MANIFEST_URL =
            "https://github.com/varyda/rustore-updater/releases/download/latest/update.json"

        /** GitHub releases page — used to open in browser as a fallback. */
        const val RELEASES_URL = "https://github.com/varyda/rustore-updater/releases"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val json: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** UI-facing snapshot of the self-update flow. */
sealed interface SelfUpdateState {
    /** No update check in progress / no result yet. */
    data object Idle : SelfUpdateState
    /** A check is currently running. */
    data object Checking : SelfUpdateState
    /** A newer version is available. */
    data class Available(val info: UpdateInfo) : SelfUpdateState
    /** The running build is up to date. */
    data object UpToDate : SelfUpdateState
    /** The APK download is in progress. */
    data class Downloading(val info: UpdateInfo) : SelfUpdateState
    /** The last check/download failed. */
    data class Error(val message: String) : SelfUpdateState
}
