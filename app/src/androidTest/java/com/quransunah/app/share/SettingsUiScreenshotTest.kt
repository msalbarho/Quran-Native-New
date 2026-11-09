package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.data.prefs.UserSettings
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.settings.SettingsScreenContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureSettingsSheet() {
        composeRule.setContent {
            PreviewTheme {
                SettingsScreenContent(
                    settings = UserSettings(nightMode = false, medinaMode = true),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("settings-shot"),
                )
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("settings-shot").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "v8-settings.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v8-maher")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert")
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
