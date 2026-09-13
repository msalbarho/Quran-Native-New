package com.quransunah.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.quransunah.app.R
import com.quransunah.app.BuildConfig
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.data.prefs.UserSettings
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.PaperPaletteId
import com.quransunah.app.ui.theme.PaperPalettes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    val inspection = LocalInspectionMode.current
    if (inspection) {
        SettingsScreenContent(
            settings = UserSettings(nightMode = LocalNightMode.current),
            modifier = modifier,
        )
        return
    }
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreenContent(
        settings = settings,
        onNightMode = viewModel::setNightMode,
        onPalette = viewModel::setPalette,
        onMedinaMode = viewModel::setMedinaMode,
        onMushafFontSize = viewModel::setMushafFontSize,
        onMushafFontSizePreview = viewModel::previewMushafFontSize,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreenContent(
    settings: UserSettings,
    modifier: Modifier = Modifier,
    onNightMode: (Boolean) -> Unit = {},
    onPalette: (PaperPaletteId) -> Unit = {},
    onMedinaMode: (Boolean) -> Unit = {},
    onMushafFontSize: (Float) -> Unit = {},
    onMushafFontSizePreview: (Float) -> Unit = {},
) {
    val paper = LocalPaperColors.current
    var aboutOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(paper.pageBody)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SettingsSection(title = stringResource(R.string.settings_appearance), showDivider = false) {
            SegmentedTwoOptions(
                firstLabel = stringResource(R.string.settings_theme_light),
                secondLabel = stringResource(R.string.settings_theme_dark),
                firstSelected = !settings.nightMode,
                onFirst = { onNightMode(false) },
                onSecond = { onNightMode(true) },
            )
        }
        SettingsSection(title = stringResource(R.string.settings_app_color)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PaperPaletteId.entries.forEach { id ->
                    val active = id == settings.palette
                    val (start, end) = PaperPalettes.swatchGradient(id)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(if (active) 6.dp else 2.dp, RoundedCornerShape(9.dp), clip = false)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Brush.linearGradient(listOf(start, end)))
                            .border(
                                width = if (active) 2.dp else 2.dp,
                                color = if (active) paper.accent else paper.tone500.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(9.dp),
                            )
                            .clickable { onPalette(id) },
                    )
                }
            }
        }
        SettingsSection(title = stringResource(R.string.settings_page_display)) {
            SegmentedTwoOptions(
                firstLabel = stringResource(R.string.settings_pages_madina),
                secondLabel = stringResource(R.string.settings_pages_text),
                firstSelected = settings.medinaMode,
                onFirst = { onMedinaMode(true) },
                onSecond = { onMedinaMode(false) },
            )
        }
        SettingsSection(title = stringResource(R.string.settings_font_size)) {
            if (settings.medinaMode) {
                Text(
                    text = stringResource(R.string.settings_font_size_hint),
                    color = paper.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                )
            } else {
                FontSizeSlider(
                    size = settings.textSizeSp,
                    min = AppConstants.TEXT_MUSHAF_MIN_SP,
                    max = AppConstants.TEXT_MUSHAF_MAX_SP,
                    onPreview = onMushafFontSizePreview,
                    onCommit = onMushafFontSize,
                )
            }
        }
        SettingsSection(title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.settings_about_button),
                color = paper.textStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(paper.tone300)
                    .border(1.dp, paper.tone500.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                    .clickable { aboutOpen = true }
                    .padding(vertical = 12.dp, horizontal = 14.dp),
            )
        }
    }
    if (aboutOpen) {
        AboutAppDialog(onClose = { aboutOpen = false })
    }
}

@Composable
private fun SettingsSection(
    title: String,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                color = paper.tone500.copy(alpha = 0.72f),
            )
        }
        Text(
            text = title,
            color = paper.textStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun SegmentedTwoOptions(
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SegmentedOption(
            label = firstLabel,
            selected = firstSelected,
            onClick = onFirst,
            modifier = Modifier.weight(1f),
        )
        SegmentedOption(
            label = secondLabel,
            selected = !firstSelected,
            onClick = onSecond,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Text(
        text = label,
        color = paper.textStrong,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) paper.accent.copy(alpha = 0.14f) else paper.tone300)
            .border(
                1.dp,
                if (selected) paper.accent.copy(alpha = 0.55f) else paper.tone500.copy(alpha = 0.88f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

@Composable
private fun FontSizeSlider(
    size: Float,
    min: Float,
    max: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val paper = LocalPaperColors.current
    var local by remember { mutableFloatStateOf(size.coerceIn(min, max)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_font_px, EasternArabic.formatDecimal(local)),
            color = paper.textStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Slider(
            value = local,
            onValueChange = { raw ->
                local = raw
                onPreview(raw)
            },
            onValueChangeFinished = { onCommit(local) },
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = paper.accent,
                activeTrackColor = paper.accent,
                inactiveTrackColor = paper.tone500,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_font_small), color = paper.textMuted, fontSize = 12.sp)
            Text(stringResource(R.string.settings_font_large), color = paper.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AboutAppDialog(onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val context = LocalContext.current
    val email = stringResource(R.string.settings_contact_email)
    val displayedVersionName = BuildConfig.VERSION_NAME.removeSuffix("-debug")
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(paper.pageBody),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(paper.chromeFill)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_about_title),
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.close),
                    color = Color(0xFFD10000),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_about_p1), color = paper.textPrimary, fontSize = 15.sp, lineHeight = 24.sp)
                Text(stringResource(R.string.settings_about_p2), color = paper.textPrimary, fontSize = 15.sp, lineHeight = 24.sp)
                Text(stringResource(R.string.settings_about_p3), color = paper.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(
                        R.string.settings_app_version,
                        BuildConfig.VERSION_CODE,
                        displayedVersionName,
                    ),
                    color = paper.textStrong,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")),
                                )
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "✉️", fontSize = 16.sp)
                    Text(
                        text = stringResource(R.string.settings_about_contact, email),
                        color = paper.accent,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@ArabicPreviews
@Composable
private fun SettingsScreenPreview() {
    PreviewTheme {
        SettingsScreenContent(
            settings = UserSettings(nightMode = LocalNightMode.current),
        )
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )

    fun setNightMode(night: Boolean) {
        viewModelScope.launch { preferences.setNightMode(night) }
    }

    fun setPalette(id: PaperPaletteId) {
        viewModelScope.launch { preferences.setPalette(id) }
    }

    fun setMedinaMode(medina: Boolean) {
        viewModelScope.launch { preferences.setMedinaMode(medina) }
    }

    fun previewMushafFontSize(size: Float) {
        preferences.previewTextSize(size)
    }

    fun setMushafFontSize(size: Float) {
        viewModelScope.launch { preferences.setTextSize(size) }
    }
}
