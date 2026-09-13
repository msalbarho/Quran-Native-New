package com.quransunah.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.LocalPaperColors

@Preview(
    name = "فاتح عربي",
    locale = "ar",
    showBackground = true,
    backgroundColor = 0xFFFBF9F6,
)
@Preview(
    name = "ليلي عربي",
    locale = "ar",
    showBackground = true,
    backgroundColor = 0xFF000000,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class ArabicPreviews

@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    val night = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    HolyQuranTheme(nightMode = night) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalPaperColors.current.pageBackground),
        ) {
            content()
        }
    }
}
