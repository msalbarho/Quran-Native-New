package com.quransunah.app

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.AppPermissions
import com.quransunah.app.core.AppUiPresence
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.hydration.HydrationState
import com.quransunah.app.media.auto.AutoPowerCompat
import com.quransunah.app.ui.index.PlaceArtCache
import com.quransunah.app.ui.mushaf.MedinaPageLayoutStore
import com.quransunah.app.ui.shell.HolyQuranApp
import com.quransunah.app.ui.shell.HolyQuranViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HolyQuranViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var allowSplash = true

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ArabicRtl.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val fromAuto = launchedFromAndroidAuto()
        // System splash exits as soon as hydration is ready. Brand presence
        // (large black splash) is held ~1.25s in Compose — Android 12+ system
        // splash can only show a small icon.
        splash.setKeepOnScreenCondition {
            when {
                fromAuto -> false
                !allowSplash -> false
                else -> viewModel.hydration.value is HydrationState.Pending
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hydration.collectLatest { state ->
                    if (state !is HydrationState.Pending) {
                        allowSplash = false
                    }
                }
            }
        }
        // Absolute fallback so a stuck hydrate never freezes launch.
        mainHandler.postDelayed({ allowSplash = false }, SPLASH_FALLBACK_MS)
        enableEdgeToEdge()
        setContent {
            HolyQuranApp(viewModel)
        }
        if (!fromAuto) {
            window.decorView.post {
                AppPermissions.requestIfNeeded(this)
                AutoPowerCompat.maybePromptOnce(this)
            }
        }
        maybeYieldToCarDisplay()
    }

    override fun onStart() {
        super.onStart()
        AppUiPresence.onActivityStarted()
    }

    override fun onStop() {
        AppUiPresence.onActivityStopped()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeYieldToCarDisplay()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        PlaceArtCache.trim()
        MedinaPageLayoutStore.clear()
        super.onDestroy()
    }

    private fun launchedFromAndroidAuto(): Boolean {
        val carUi = getSystemService(UiModeManager::class.java)?.currentModeType ==
            Configuration.UI_MODE_TYPE_CAR
        val fromAuto = intent.getBooleanExtra(AppConstants.EXTRA_FROM_ANDROID_AUTO, false)
        return carUi || fromAuto
    }

    private fun maybeYieldToCarDisplay() {
        if (!launchedFromAndroidAuto()) return
        mainHandler.post {
            runCatching { moveTaskToBack(true) }
        }
    }

    companion object {
        private const val SPLASH_FALLBACK_MS = 8_000L
    }
}
