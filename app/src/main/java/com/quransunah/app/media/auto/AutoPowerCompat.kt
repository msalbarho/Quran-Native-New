package com.quransunah.app.media.auto

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Samsung One UI / Android 14+ often freezes media apps during wireless Auto
 * unless battery optimization is unrestricted. Prompt once from the phone UI;
 * Auto browse itself never needs MainActivity.
 */
object AutoPowerCompat {
    private const val PREFS = "auto_power"
    private const val KEY_PROMPT_SHOWN = "battery_prompt_shown"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the system dialog to exempt this app. Safe to call from an Activity only. */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) return false
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun maybePromptOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PROMPT_SHOWN, false)) return
        if (isIgnoringBatteryOptimizations(context)) {
            prefs.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
            return
        }
        if (requestIgnoreBatteryOptimizations(context)) {
            prefs.edit().putBoolean(KEY_PROMPT_SHOWN, true).apply()
        }
    }
}