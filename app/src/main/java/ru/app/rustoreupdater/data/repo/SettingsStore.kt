package ru.app.rustoreupdater.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Check interval choices in hours. */
enum class CheckInterval(val hours: Int, val label: String) {
    EVERY_1H(1, "Каждый час"),
    EVERY_3H(3, "Каждые 3 часа"),
    EVERY_6H(6, "Каждые 6 часов"),
    EVERY_12H(12, "Каждые 12 часов"),
    EVERY_24H(24, "Каждый день");

    companion object {
        val DEFAULT = EVERY_6H
        fun fromHours(hours: Int): CheckInterval =
            entries.firstOrNull { it.hours == hours } ?: DEFAULT
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val INTERVAL_HOURS = longPreferencesKey("interval_hours")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
    }

    val intervalHours: Flow<Int> = context.dataStore.data.map { p ->
        p[Keys.INTERVAL_HOURS]?.toInt() ?: CheckInterval.DEFAULT.hours
    }

    val autoDownload: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.AUTO_DOWNLOAD] ?: false
    }

    suspend fun setInterval(hours: Int) {
        context.dataStore.edit { it[Keys.INTERVAL_HOURS] = hours.toLong() }
    }

    suspend fun setAutoDownload(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DOWNLOAD] = value }
    }
}
