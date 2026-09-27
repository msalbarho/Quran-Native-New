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

    fun requestIfNeeded(activity: Activity) {
        val missing = missing(activity)
        if (missing.isEmpty()) return
        ActivityCompat.requestPermissions(activity, missing, REQUEST_CODE)
    }

    fun canWriteLegacyStorage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
