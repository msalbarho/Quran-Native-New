package com.quransunah.app.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object AppPermissions {
    const val REQUEST_CODE = 2401

    fun neededRuntimePermissions(): Array<String> {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return permissions.toTypedArray()
    }

    fun missing(context: Context): Array<String> {
        return neededRuntimePermissions()
            .filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    /**
     * Requests whatever [neededRuntimePermissions] are still missing.
     * Returns false when Android has nothing to ask, so the caller can continue.
     * [launch] is the activity-result path; the default keeps the legacy request.
     */
    fun requestIfNeeded(
        activity: Activity,
        launch: (Array<String>) -> Unit = { missing ->
            ActivityCompat.requestPermissions(activity, missing, REQUEST_CODE)
        },
    ): Boolean {
        val missing = missing(activity)
        if (missing.isEmpty()) return false
        launch(missing)
        return true
    }

    fun canWriteLegacyStorage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
