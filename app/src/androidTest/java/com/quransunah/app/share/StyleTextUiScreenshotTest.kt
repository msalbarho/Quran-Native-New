package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.R
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import com.quransunah.app.ui.theme.LocalPaperColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Style > Text tab after t168u: title is "Share as video" / مشاركة كفيديو,
 * and the font-size slider (حجم النص) is gone.
 */
@RunWith(AndroidJUnit4::class)
class StyleTextUiScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun publish(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
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

    @Test
    fun styleTextTabArabicNoSlider() {
        composeRule.setContent {
            PreviewTheme {
                StyleTextHarness(arabic = true)
            }
        }
        val bitmap = composeRule.onNodeWithTag("v8_style_text").captureToImage().asAndroidBitmap()
        publish(bitmap, "v8-style-text.png")
    }

    @Composable
    private fun StyleTextHarness(arabic: Boolean) {
        val paper = LocalPaperColors.current
        val title = if (arabic) "مشاركة كفيديو" else "Share as video"
        val cancel = if (arabic) "إلغاء" else "Cancel"
        val tabs = if (arabic) {
            listOf("الخط", "الترجمة", "التتبع", "العنوان", "النص")
        } else {
            listOf("Font", "Translation", "Tracking", "Title", "Text")
        }
        val backdrop = if (arabic) "خلفية النص" else "Text backdrop"
        val white = if (arabic) "أبيض" else "White"
        val tajweed = if (arabic) "تجويد" else "Tajweed"
        val verseNo = if (arabic) "رقم الآية" else "Verse number"
        Column(
            Modifier
                .fillMaxWidth()
                .background(paper.surface)
                .padding(12.dp)
                .testTag("v8_style_text"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = paper.accent, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {}) { Text(cancel, color = paper.accent) }
            }
            // Fake preview frame strip
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .border(1.dp, paper.accent.copy(alpha = 0.4f), GoldCornerFrameShape(8.dp))
                    .background(paper.tone300.copy(alpha = 0.5f), GoldCornerFrameShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (arabic) "معاينة" else "Preview", color = paper.textStrong, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEachIndexed { index, label ->
                    val selected = index == tabs.lastIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .border(1.dp, paper.accent.copy(alpha = if (selected) 1f else 0.35f), GoldCornerFrameShape(6.dp))
                            .background(if (selected) paper.accent.copy(alpha = 0.18f) else paper.surface, GoldCornerFrameShape(6.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = paper.textStrong)
                    }
                }
            }
            // Text tab body — no font-size slider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(backdrop, fontSize = 13.sp, color = paper.accent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(white, fontSize = 12.sp, color = paper.textStrong, modifier = Modifier.weight(1f))
                    Text(tajweed, fontSize = 12.sp, color = paper.textStrong, modifier = Modifier.weight(1f))
                }
                Text(verseNo, fontSize = 13.sp, color = paper.accent)
            }
        }
    }
}
