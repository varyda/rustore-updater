package ru.app.rustoreupdater.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds the RuStore API stack.
 *
 * Kept separate from [ru.app.rustoreupdater.di.ServiceLocator] so that the network
 * layer can be exercised from JVM unit tests against the real backend (see
 * `RuStoreApiTest`) without any Android runtime.
 */
object RuStoreApiFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun client(
        device: RuStoreDevice,
        logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(RuStoreHeadersInterceptor(device))
        .addInterceptor(HttpLoggingInterceptor().apply { level = logLevel })
        .build()

    fun create(
        device: RuStoreDevice,
        baseUrl: String = RuStoreApi.BASE_URL,
        client: OkHttpClient = client(device),
    ): RuStoreApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RuStoreApi::class.java)
}
