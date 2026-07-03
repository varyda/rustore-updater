package ru.app.rustoreupdater.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A RuStore application the user is tracking for updates.
 *
 * `appId` is the RuStore application identifier (path segment of the catalog URL),
 * used as the primary key.
 */
@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey
    val appId: String,
    val packageName: String,
    val appName: String,
    val iconUrl: String?,
    val developerName: String?,
    val category: String?,
    val shortDescription: String?,
    val fullDescription: String?,
    val whatsNew: String?,
    /** Latest version name known from RuStore. */
    val latestVersionName: String?,
    /** Latest version code known from RuStore. */
    val latestVersionCode: Long,
    /** File size in bytes of the latest version. */
    val fileSize: Long,
    /** Last-modified timestamp of the latest version (ISO string). */
    val appVerUpdatedAt: String?,
    /** Version code currently installed on the device, or null if not installed. */
    val installedVersionCode: Long?,
    /** True when latestVersionCode > installedVersionCode (or not installed). */
    val updateAvailable: Boolean,
    val addedAt: Long,
    val lastCheckAt: Long?,
)
