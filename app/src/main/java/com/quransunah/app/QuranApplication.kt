package com.quransunah.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.media3.common.util.UnstableApi
import com.quransunah.app.core.CrashFileLogger
import com.quransunah.app.data.audio.AyatTimingStore
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.index.PlaceArtCache
import com.quransunah.app.ui.mushaf.MedinaPageLayoutStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class QuranApplication : Application() {
    @Inject lateinit var fontManager: QcfFontManager
    @Inject lateinit var ayatTimingStore: AyatTimingStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Do NOT wrap attachBaseContext: ContextWrapper here breaks BroadcastReceiver
        // instantiation on Android 14+ (ClassCastException → ContextImpl), which
        // would prevent Auto car-connection from priming PlaybackService.
        super.onCreate()
        CrashFileLogger.install(this)
        silenceMedia3Logs()
        if (::fontManager.isInitialized) {
            appScope.launch { runCatching { fontManager.ensureMaps() } }
        }
        appScope.launch { runCatching { PlaceArtCache.warmup(this@QuranApplication) } }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::fontManager.isInitialized) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            fontManager.evictPageFaces()
            ayatTimingStore.trim(keep = 1)
            PlaceArtCache.trim()
            MedinaPageLayoutStore.clear()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            fontManager.trimPageFaces()
            ayatTimingStore.trim(keep = 2)
        }
    }

    @OptIn(UnstableApi::class)
    private fun silenceMedia3Logs() {
        if (BuildConfig.DEBUG) return
        androidx.media3.common.util.Log.setLogLevel(
            androidx.media3.common.util.Log.LOG_LEVEL_OFF,
        )
        androidx.media3.common.util.Log.setLogStackTraces(false)
    }
}
