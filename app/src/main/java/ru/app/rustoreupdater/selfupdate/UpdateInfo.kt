package ru.app.rustoreupdater.selfupdate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Manifest published alongside every GitHub release as `update.json`.
 *
 * The app fetches this file at runtime and compares [versionCode] with the
 * installed build to decide whether an update is available. The [apkUrl] points
 * directly to the release asset, and [apkSizeBytes] lets the UI show the
 * download size without an extra HEAD request.
 *
 * Example payload produced by the CI workflow:
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1",
 *   "apkUrl": "https://github.com/.../rustore-updater-1.1.apk",
 *   "apkSizeBytes": 22300000,
 *   "releaseNotes": "- Автообновление\n- Исправления"
 * }
 * ```
 */
@Serializable
data class UpdateInfo(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    @SerialName("apkSizeBytes") val apkSizeBytes: Long = 0,
    @SerialName("releaseNotes") val releaseNotes: String? = null,
)
