package ru.app.rustoreupdater.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM tracked_apps ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps WHERE appId = :appId LIMIT 1")
    fun observe(appId: String): Flow<TrackedAppEntity?>

    @Query("SELECT * FROM tracked_apps WHERE appId = :appId LIMIT 1")
    suspend fun get(appId: String): TrackedAppEntity?

    @Query("SELECT * FROM tracked_apps")
    suspend fun getAll(): List<TrackedAppEntity>

    @Query("SELECT COUNT(*) FROM tracked_apps WHERE appId = :appId")
    suspend fun count(appId: String): Int

    @Upsert
    suspend fun upsert(app: TrackedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(app: TrackedAppEntity): Long

    @Query("DELETE FROM tracked_apps WHERE appId = :appId")
    suspend fun delete(appId: String)

    @Query(
        """
        UPDATE tracked_apps
        SET latestVersionName = :versionName,
            latestVersionCode = :versionCode,
            fileSize = :fileSize,
            whatsNew = :whatsNew,
            appVerUpdatedAt = :appVerUpdatedAt,
            updateAvailable = :updateAvailable,
            lastCheckAt = :lastCheckAt
        WHERE appId = :appId
        """
    )
    suspend fun updateLatestInfo(
        appId: String,
        versionName: String?,
        versionCode: Long,
        fileSize: Long,
        whatsNew: String?,
        appVerUpdatedAt: String?,
        updateAvailable: Boolean,
        lastCheckAt: Long,
    )

    @Query("UPDATE tracked_apps SET installedVersionCode = :versionCode, updateAvailable = :updateAvailable WHERE appId = :appId")
    suspend fun setInstalled(appId: String, versionCode: Long, updateAvailable: Boolean)

    @Query("UPDATE tracked_apps SET updateAvailable = :updateAvailable WHERE appId = :appId")
    suspend fun setUpdateAvailable(appId: String, updateAvailable: Boolean)
}
