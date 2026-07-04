package ru.app.rustoreupdater

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.work.UpdateScheduler

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Notifier.ensureChannels(this)
        // Schedule periodic update checks (idempotent — safe to call on every start).
        UpdateScheduler.ensureScheduled(this)
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
