package ru.app.rustoreupdater.di

import android.content.Context
import ru.app.rustoreupdater.data.network.RuStoreApi
import ru.app.rustoreupdater.data.network.RuStoreApiFactory
import ru.app.rustoreupdater.data.network.RuStoreDevice
import ru.app.rustoreupdater.data.repo.AppRepository
import ru.app.rustoreupdater.data.repo.SettingsStore
import ru.app.rustoreupdater.selfupdate.SelfUpdater

/**
 * Minimal manual dependency container. Avoids pulling in a DI framework for a
 * single-module app; all singletons are created lazily and scoped to the
 * application context.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    @Volatile private var apiBacking: RuStoreApi? = null
    @Volatile private var settingsBacking: SettingsStore? = null
    @Volatile private var repository: AppRepository? = null
    @Volatile private var selfUpdater: SelfUpdater? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val api: RuStoreApi
        get() {
            apiBacking?.let { return it }
            return synchronized(this) {
                apiBacking ?: buildApi().also { apiBacking = it }
            }
        }

    val settingsStore: SettingsStore
        get() {
            settingsBacking?.let { return it }
            return synchronized(this) {
                settingsBacking ?: SettingsStore(appContext).also { settingsBacking = it }
            }
        }

    fun appRepository(context: Context): AppRepository {
        repository?.let { return it }
        return synchronized(this) {
            repository ?: AppRepository.fromContext(context.applicationContext, api).also { repository = it }
        }
    }

    /** Lazily-created [SelfUpdater] scoped to the application context. */
    fun selfUpdater(): SelfUpdater {
        selfUpdater?.let { return it }
        return synchronized(this) {
            selfUpdater ?: SelfUpdater(appContext).also { selfUpdater = it }
        }
    }

    /**
     * Builds the RuStore API client with the device fingerprint that backapi expects.
     *
     * Without the [RuStoreDevice] headers (`ruStoreVerCode` in particular) the
     * `overallInfo` and `download-link` endpoints answer HTTP 400 with an empty
     * body, which used to make adding any app fail.
     */
    private fun buildApi(): RuStoreApi =
        RuStoreApiFactory.create(RuStoreDevice.fromContext(appContext))
}
