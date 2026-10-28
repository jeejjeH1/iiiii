package app.goallive.live

import android.app.Application
import app.goallive.live.data.Api
import app.goallive.live.data.Store
import app.goallive.live.notify.LiveAlerts
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class GoalLiveApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
        Api.init(this)
        Store.init(this)
        app.goallive.live.data.Telemetry.init(this)
        app.goallive.live.data.Predictions.init(this)
        app.goallive.live.data.Backup.init(this)
        app.goallive.live.data.Translate.init(this)
        app.goallive.live.ui.Look.mode = Store.theme
        LiveAlerts.channels(this)
        LiveAlerts.start(this)
        Store.onFollowChange = { LiveAlerts.start(this) }
    }

    // crests and faces are reused everywhere: keep plenty of them in memory and on disk
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Api.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(80L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()
}
