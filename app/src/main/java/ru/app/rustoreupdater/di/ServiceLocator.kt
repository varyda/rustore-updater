package ru.app.rustoreupdater.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import ru.app.rustoreupdater.data.network.RuStoreApi
import ru.app.rustoreupdater.data.repo.AppRepository
import ru.app.rustoreupdater.data.repo.SettingsStore
import ru.app.rustoreupdater.selfupdate.SelfUpdater
import java.util.concurrent.TimeUnit

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

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
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

    private fun buildApi(): RuStoreApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("User-Agent", "RuStoreUpdater/1.0 (Android)")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(RuStoreApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(RuStoreApi::class.java)
    }
}
