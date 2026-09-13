package com.quransunah.app.media.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.car.app.connection.CarConnection
import com.quransunah.app.core.AppLog
import com.quransunah.app.media.PlaybackService

/**
 * Primes [PlaybackService] when a car connection is reported.
 *
 * Uses [Context.startService] (not startForegroundService) so an idle phone
 * never owes Android a sticky FGS notification. Foreground + media notification
 * start only when playback actually begins (same model as quran-app).
 */
class AutoCarConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CarConnection.ACTION_CAR_CONNECTION_UPDATED) return
        if (!intent.hasExtra(CarConnection.CAR_CONNECTION_STATE)) {
            AppLog.w(TAG, "CAR_CONNECTION_UPDATED without CAR_CONNECTION_STATE; ignore")
            return
        }
        val type = intent.getIntExtra(
            CarConnection.CAR_CONNECTION_STATE,
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED,
        )
        AppLog.i(TAG) { "CAR_CONNECTION_UPDATED type=$type" }
        if (type == CarConnection.CONNECTION_TYPE_NOT_CONNECTED) {
            val detach = Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_AUTO_DETACH)
            runCatching { context.startService(detach) }
            AppLog.i(TAG) { "car detached; demote Auto session" }
            return
        }
        // Only treat projection as a real head-unit attach (USB / wireless Auto).
        if (type != CarConnection.CONNECTION_TYPE_PROJECTION) {
            AppLog.i(TAG) { "ignore non-projection car type=$type" }
            return
        }
        val service = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_AUTO_SESSION)
        runCatching {
            context.startService(service)
            AppLog.i(TAG) { "started PlaybackService ACTION_AUTO_SESSION (no FGS)" }
        }.onFailure { t ->
            AppLog.e(TAG, "startService ACTION_AUTO_SESSION failed", t)
        }
    }

    companion object {
        private const val TAG = "QuranAuto"
    }
}
