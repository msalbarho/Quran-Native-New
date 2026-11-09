package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import com.quransunah.app.ui.theme.LocalPaperColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RangeUiScreenshotTest {
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

    private fun capture(arabic: Boolean, tag: String, fileName: String) {
        val surah = if (arabic) "سورة البقرة" else "Al-Baqarah"
        val from = if (arabic) "من الآية" else "From ayah"
        val to = if (arabic) "إلى الآية" else "To ayah"
        composeRule.setContent {
            PreviewTheme {
                val paper = LocalPaperColors.current
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag(tag),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            surah,
                            color = paper.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        RangePill(label = from, value = 5, modifier = Modifier.weight(1f))
                        RangePill(label = to, value = 7, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        publish(composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap(), fileName)
    }

    @Test
    fun captureEnglishAndArabicRangePills() {
        capture(arabic = false, tag = "range-en", fileName = "v8-range-en.png")
        capture(arabic = true, tag = "range-ar", fileName = "v8-range-ar.png")
    }
}

@Composable
private fun RangePill(label: String, value: Int, modifier: Modifier = Modifier) {
    val gold = LocalPaperColors.current.accent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, gold.copy(alpha = 0.85f), GoldCornerFrameShape(10.dp))
            .clickable { }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = gold.copy(alpha = 0.78f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(EasternArabic.format(value), color = gold, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text("▾", color = gold.copy(alpha = 0.9f), fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
        }
    }
}
