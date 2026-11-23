package ru.app.rustoreupdater

import android.app.Application
import android.util.Log
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.work.UpdateScheduler
import java.util.concurrent.TimeUnit

class App : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Notifier.ensureChannels(this)
        // Restore the periodic update check with the interval the user picked.
        // Only registered when missing, so the schedule is not reset on every launch.
        appScope.launch { UpdateScheduler.ensureScheduled(this@App) }
    }

    /**
     * Shared Coil [ImageLoader] with explicit caches and a tuned OkHttp client.
     *
     * Key points for `static.rustore.ru`:
     * - `maxRequestsPerHost = 12` prevents icon prefetch bursts from queuing behind OkHttp's
     *   default 5-per-host limit and timing out.
     * - `respectCacheHeaders(false)` ensures icons/screenshots are cached on disk even when the
     *   CDN omits cache headers or sends `content-disposition: attachment`.
     * - Explicit `User-Agent` and `Accept` headers avoid CDN rejections.
     */
    override fun newImageLoader(): ImageLoader {
        val imageDispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        }
        val imageClient = OkHttpClient.Builder()
            .dispatcher(imageDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "RuStoreUpdater/1.2 (Android)")
                    .header("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5")
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128 MB
                    .build()
            }
            .eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    Log.w("CoilImage", "Failed to load ${request.data}", result.throwable)
                }
            })
            .crossfade(false)
            .build()
    }
}
