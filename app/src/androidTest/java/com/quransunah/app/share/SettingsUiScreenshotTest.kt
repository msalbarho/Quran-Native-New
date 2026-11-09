package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
class SettingsUiScreenshotTest {
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
    fun settingsAppearanceSegmentsCentered() {
        composeRule.setContent {
            PreviewTheme {
                SettingsHarness()
            }
        }
        composeRule.waitForIdle()
        publish(composeRule.onNodeWithTag("v9_settings").captureToImage().asAndroidBitmap(), "v9-settings.png")
    }

    @Composable
    private fun SettingsHarness() {
        val paper = LocalPaperColors.current
        val shape = GoldCornerFrameShape(12.dp)
        val style = TextStyle(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
        Column(
            Modifier.fillMaxWidth().background(paper.surface).padding(16.dp).testTag("v9_settings"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("\u0627\u0644\u0645\u0638\u0647\u0631", color = paper.accent, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().clip(shape).border(1.dp, paper.accent.copy(alpha = 0.35f), shape).padding(2.dp),
            ) {
                listOf("\u0646\u0647\u0627\u0631\u064a" to true, "\u0644\u064a\u0644\u064a" to false).forEach { (label, sel) ->
                    Text(
                        label,
                        color = if (sel) paper.pageBackground else paper.textStrong,
                        style = style,
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp).clip(shape)
                            .background(if (sel) paper.accent else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text("\u0639\u0631\u0636 \u0627\u0644\u0635\u0641\u062d\u0629", color = paper.accent, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().clip(shape).border(1.dp, paper.accent.copy(alpha = 0.35f), shape).padding(2.dp),
            ) {
                listOf("\u0645\u0635\u062d\u0641 \u0627\u0644\u0645\u062f\u064a\u0646\u0629" to true, "\u0627\u0644\u0645\u0635\u062d\u0641 \u0627\u0644\u0646\u0635\u064a" to false).forEach { (label, sel) ->
                    Text(
                        label,
                        color = if (sel) paper.pageBackground else paper.textStrong,
                        style = style,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp).clip(shape)
                            .background(if (sel) paper.accent else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("\u062f\u0644\u064a\u0644 \u0627\u0644\u0645\u0633\u062a\u062e\u062f\u0645", "\u062d\u0648\u0644 \u0627\u0644\u062a\u0637\u0628\u064a\u0642").forEach { label ->
                    Text(
                        label,
                        color = paper.textStrong,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            textAlign = TextAlign.Center,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp).clip(GoldCornerFrameShape(10.dp))
                            .background(paper.tone300).border(1.dp, paper.accent.copy(alpha = 0.6f), GoldCornerFrameShape(10.dp))
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
