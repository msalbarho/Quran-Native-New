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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v9-share")
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
                    Modifier.fillMaxWidth().padding(16.dp).testTag(tag),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(surah, color = paper.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RangePill(label = from, value = 5, modifier = Modifier.weight(1f))
                        RangePill(label = to, value = 7, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        publish(composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap(), fileName)
    }

    @Test fun captureEnglishRangePills() = capture(false, "range-en", "v9-range-en.png")
    @Test fun captureArabicRangePills() = capture(true, "range-ar", "v9-range-ar.png")
}

@Composable
private fun RangePill(label: String, value: Int, modifier: Modifier = Modifier) {
    val gold = LocalPaperColors.current.accent
    val style = TextStyle(
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Column(modifier = modifier) {
        Text(
            label,
            color = gold.copy(alpha = 0.78f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp),
            style = style,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp)
                .border(1.dp, gold.copy(alpha = 0.85f), GoldCornerFrameShape(10.dp))
                .clickable { }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(EasternArabic.format(value), color = gold, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, style = style)
            Text(" ▾", color = gold.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, style = style)
        }
    }
}
