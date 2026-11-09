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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import com.quransunah.app.ui.theme.LocalPaperColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongPressUiScreenshotTest {
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

    @Test
    fun longPressSheetArabic() {
        composeRule.setContent { PreviewTheme { LongPressHarness() } }
        composeRule.waitForIdle()
        publish(composeRule.onNodeWithTag("v9_longpress").captureToImage().asAndroidBitmap(), "v9-longpress.png")
    }

    @Composable
    private fun LongPressHarness() {
        val paper = LocalPaperColors.current
        val shape = GoldCornerFrameShape(12.dp)
        val labelStyle = TextStyle(
            textAlign = TextAlign.Center,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
        Column(
            Modifier.fillMaxWidth().background(paper.surface).padding(horizontal = 16.dp)
                .padding(top = 2.dp, bottom = 28.dp).testTag("v9_longpress"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                    .size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                    .background(paper.accent.copy(alpha = 0.35f)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("\u0671\u0644\u0652\u062d\u064e\u0645\u0652\u062f\u064f", fontSize = 22.sp, color = paper.textStrong, modifier = Modifier.weight(1f))
                Text("\u0625\u063a\u0644\u0627\u0642", color = paper.accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Column(
                Modifier.fillMaxWidth().clip(shape).background(paper.tone300.copy(alpha = 0.6f))
                    .border(1.dp, paper.accent.copy(alpha = 0.2f), shape).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("\u0627\u0644\u062a\u0641\u0633\u064a\u0631", color = paper.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("\u0627\u0644\u062d\u0645\u062f \u0644\u0644\u0647\u2026", color = paper.textStrong, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("\u062a\u0641\u0633\u064a\u0631", "\u062d\u0641\u0638").forEach { label ->
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(shape)
                            .background(paper.accent.copy(alpha = 0.16f)).border(1.dp, paper.accent, shape),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = paper.textStrong, fontWeight = FontWeight.Bold, fontSize = 14.sp, style = labelStyle) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("\u0645\u0634\u0627\u0631\u0643\u0629 \u0635\u0648\u0631\u0629", "\u0645\u0634\u0627\u0631\u0643\u0629 \u0646\u0635").forEach { label ->
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(shape).background(paper.accent).border(1.dp, paper.accent, shape),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = paper.pageBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = labelStyle) }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(shape)
                    .background(paper.accent.copy(alpha = 0.15f)).border(1.dp, paper.accent, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u0645\u0634\u0627\u0631\u0643\u0629 \u0643\u0641\u064a\u062f\u064a\u0648",
                    color = paper.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, style = labelStyle,
                )
            }
        }
    }
}
