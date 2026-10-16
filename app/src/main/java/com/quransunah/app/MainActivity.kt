package com.quransunah.app

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.AppPermissions
import com.quransunah.app.core.AppUiPresence
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.media.auto.AutoPowerCompat
import com.quransunah.app.ui.index.PlaceArtCache
import com.quransunah.app.ui.mushaf.MedinaPageLayoutStore
import com.quransunah.app.ui.shell.HolyQuranApp
import com.quransunah.app.ui.shell.HolyQuranViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HolyQuranViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.postOnboardingPermissionRequestOpen = false
        if (launchedFromAndroidAuto()) return@registerForActivityResult
        if (isFinishing || isDestroyed) {
            viewModel.postOnboardingBatteryPromptPending = true
            return@registerForActivityResult
        }
        AutoPowerCompat.maybePromptOnce(this)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ArabicRtl.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ always draws a system splash (centered icon). Do not hold it:
        // it exits on the first frame so the edge-to-edge artwork can take over.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val fromAuto = launchedFromAndroidAuto()
        enableEdgeToEdge()
        setContent {
            HolyQuranApp(viewModel)
        }
        if (!fromAuto) {
            lifecycleScope.launch {
                viewModel.onboardingDone.collect { done ->
                    if (done == true) beginPostOnboardingPrompts()
                }
            }
        }
        maybeYieldToCarDisplay()
    }

    /**
     * Notification (and legacy storage) first. The battery screen opens only
     * after that request finishes, or immediately when nothing is missing.
     */
    private fun beginPostOnboardingPrompts() {
        if (launchedFromAndroidAuto()) return
        if (viewModel.postOnboardingPermissionRequestOpen) return
        if (viewModel.postOnboardingBatteryPromptPending) {
            viewModel.postOnboardingBatteryPromptPending = false
            AutoPowerCompat.maybePromptOnce(this)
            return
        }
        if (viewModel.postOnboardingPromptsDispatched) return
        viewModel.postOnboardingPromptsDispatched = true
        viewModel.postOnboardingPermissionRequestOpen = true
        val requested = AppPermissions.requestIfNeeded(this) { missing ->
            permissionLauncher.launch(missing)
        }
        if (!requested) {
            viewModel.postOnboardingPermissionRequestOpen = false
            AutoPowerCompat.maybePromptOnce(this)
        }
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

}
