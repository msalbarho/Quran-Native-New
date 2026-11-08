package com.quransunah.app.recitation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.quransunah.app.R

/**
 * Microphone permission for recitation checking. Returns a function: call it with the action to run once
 * RECORD_AUDIO is granted (runs immediately if it already is). [onDenied] is called when the user refuses.
 */
@Composable
fun rememberMicPermissionRequest(onDenied: () -> Unit = {}): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending[0]; pending[0] = null
        if (granted) action?.invoke() else onDenied()
    }
    return remember(launcher) {
        { onGranted ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                onGranted()
            } else {
                pending[0] = onGranted
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/** One-time notice required by the Zipformer Quran licence: automatic feedback can be wrong and is not a ruling. */
@Composable
fun ZipformerFirstUseNotice(onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.recitation_engine_zipformer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recitation_zipformer_notice))
                Text(stringResource(R.string.recitation_disclaimer))
            }
        },
        confirmButton = { TextButton(onAcknowledge) { Text(stringResource(R.string.recitation_understood)) } },
    )
}

/** Shows a bundled licence text (NPL-1.2 of the model, Apache-2.0 of sherpa-onnx). */
@Composable
fun LicenseTextDialog(title: String, assetPath: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val text = remember(assetPath) { runCatching { ctx.assets.open(assetPath).bufferedReader().readText() }.getOrDefault("—") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 11.sp, style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr))
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.recitation_close)) } },
    )
}
