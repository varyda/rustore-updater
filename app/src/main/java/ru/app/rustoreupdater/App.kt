package ru.app.rustoreupdater

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.work.UpdateScheduler

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
     * Shared Coil [ImageLoader] with explicit caches. Critical for list scrolling: without a
     * stable disk cache, fast scroll re-triggers network/disk work for icons that were already
     * shown, causing jank on the main thread.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(64L * 1024 * 1024) // 64 MB
                .build()
        }
        .crossfade(false)
        .build()
}
