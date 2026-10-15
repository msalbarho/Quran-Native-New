package com.quransunah.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.quransunah.app.R
import com.quransunah.app.BuildConfig
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.LanguageRegistry
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.data.prefs.UserSettings
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ThemedLanguageCard
import com.quransunah.app.ui.shell.ThemedListDialog
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.PaperPaletteId
import com.quransunah.app.ui.theme.PaperPalettes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenUserGuide: () -> Unit = {},
) {
    val inspection = LocalInspectionMode.current
    if (inspection) {
        SettingsScreenContent(
            settings = UserSettings(nightMode = LocalNightMode.current),
            onOpenUserGuide = onOpenUserGuide,
            modifier = modifier,
        )
        return
    }
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsScreenContent(
        settings = settings,
        onNightMode = viewModel::setNightMode,
        onPalette = viewModel::setPalette,
        onMedinaMode = viewModel::setMedinaMode,
        onMushafFontSize = viewModel::setMushafFontSize,
        onMushafFontSizePreview = viewModel::previewMushafFontSize,
        onLanguage = { language ->
            ArabicRtl.setSelectedLanguage(context, language)
            viewModel.setLanguage(language)
            (context as? Activity)?.recreate()
        },
        onOpenUserGuide = onOpenUserGuide,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreenContent(
    settings: UserSettings,
    modifier: Modifier = Modifier,
    onNightMode: (Boolean) -> Unit = {},
    onPalette: (PaperPaletteId) -> Unit = {},
    onMedinaMode: (Boolean) -> Unit = {},
    onMushafFontSize: (Float) -> Unit = {},
    onMushafFontSizePreview: (Float) -> Unit = {},
    onLanguage: (String?) -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
) {
    val paper = LocalPaperColors.current
    var aboutOpen by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalLayoutDirection provides uiLayoutDirection()) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
        SettingsSection(title = stringResource(R.string.settings_language)) {
            LanguageSettingRow(selected = settings.appLanguage, onSelect = onLanguage)
        }
        SettingsSection(title = stringResource(R.string.settings_app_color)) {
            PaletteChoiceGrid(
                selected = settings.palette,
                onPalette = onPalette,
            )
            Text(
                text = stringResource(R.string.settings_selected_palette, stringResource(settings.palette.paletteLabelRes())),
                color = paper.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        SettingsSection(title = stringResource(R.string.settings_page_display)) {
            SegmentedTwoOptions(
                firstLabel = stringResource(R.string.settings_pages_madina),
                secondLabel = stringResource(R.string.settings_pages_text),
                firstSelected = settings.medinaMode,
                onFirst = { onMedinaMode(true) },
                onSecond = { onMedinaMode(false) },
                fitSingleLine = true,
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
        SettingsSection(title = stringResource(R.string.settings_user_guide)) {
            Text(
                text = stringResource(R.string.settings_user_guide_button),
                color = paper.textStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(paper.tone300)
                    .border(1.dp, paper.tone500.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenUserGuide)
                    .padding(vertical = 12.dp, horizontal = 14.dp),
            )
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
    }
    if (aboutOpen) {
        AboutAppDialog(onClose = { aboutOpen = false })
    }
}

@Composable
private fun LanguageSettingRow(selected: String?, onSelect: (String?) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    val paper = LocalPaperColors.current
    val selectedLanguage = selected?.let { LanguageRegistry.byTag(it) }
    val currentLabel = if (selectedLanguage == null) {
        stringResource(R.string.settings_language_system)
    } else {
        selectedLanguage.nativeName
    }
    val currentMark = selectedLanguage?.flag ?: DEVICE_LANGUAGE_ICON
    val night = LocalNightMode.current
    val rowShape = RoundedCornerShape(16.dp)
    val rowFill = if (night) paper.tone400 else lerp(paper.surface, paper.chromeFill, 0.28f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowFill)
            .border(1.dp, paper.accent.copy(alpha = if (night) 0.4f else 0.22f), rowShape)
            .clickable(role = Role.Button) { dialogOpen = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = currentMark, fontSize = 14.sp)
        Text(
            text = currentLabel,
            color = paper.textStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
    if (dialogOpen) {
        LanguagePickerDialog(
            selected = selected,
            onSelect = { tag ->
                dialogOpen = false
                onSelect(tag)
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = LocalPaperColors.current
    var query by remember { mutableStateOf("") }
    val deviceLabel = stringResource(R.string.settings_language_system)
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) LanguageRegistry.sortedEntries
        else LanguageRegistry.sortedEntries.filter { entry ->
            entry.nativeName.lowercase().contains(q) ||
                entry.displayName.lowercase().contains(q) ||
                entry.tag.lowercase().contains(q)
        }
    }
    val showDevice = query.isBlank() || deviceLabel.lowercase().contains(query.trim().lowercase())
    val night = LocalNightMode.current
    val fieldShape = RoundedCornerShape(14.dp)
    val fieldFill = if (night) paper.tone400.copy(alpha = 0.72f) else lerp(paper.surface, paper.chromeFill, 0.28f)
    CompositionLocalProvider(LocalLayoutDirection provides uiLayoutDirection()) {
        ThemedListDialog(
            title = stringResource(R.string.settings_choose_language),
            onDismiss = onDismiss,
            header = {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = paper.textStrong,
                        fontSize = 16.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(paper.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 4.dp)
                        .clip(fieldShape)
                        .background(fieldFill)
                        .border(1.dp, paper.accent.copy(alpha = if (night) 0.34f else 0.18f), fieldShape)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            },
        ) {
            if (!showDevice && filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_languages),
                    color = paper.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            if (showDevice) {
                ThemedLanguageCard(
                    mark = DEVICE_LANGUAGE_ICON,
                    label = deviceLabel,
                    active = selected == null,
                    onClick = { onSelect(null) },
                    markSize = 15.sp,
                    showCheck = true,
                )
            }
            filtered.forEach { entry ->
                ThemedLanguageCard(
                    mark = entry.flag,
                    label = entry.nativeName,
                    active = selected.equals(entry.tag, ignoreCase = true),
                    onClick = { onSelect(entry.tag) },
                    markSize = 15.sp,
                    showCheck = true,
                )
            }
        }
    }
}

private const val DEVICE_LANGUAGE_ICON = "📱"

private fun PaperPaletteId.paletteLabelRes(): Int = when (this) {
    PaperPaletteId.White -> R.string.settings_palette_white
    PaperPaletteId.Beige -> R.string.settings_palette_beige
    PaperPaletteId.Blue -> R.string.settings_palette_blue
    PaperPaletteId.Green -> R.string.settings_palette_green
    PaperPaletteId.Purple -> R.string.settings_palette_purple
    PaperPaletteId.Rose -> R.string.settings_palette_rose
    PaperPaletteId.Teal -> R.string.settings_palette_teal
    PaperPaletteId.Amber -> R.string.settings_palette_amber
}

@Composable
private fun PaletteChoiceGrid(
    selected: PaperPaletteId,
    onPalette: (PaperPaletteId) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 270.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            PaperPaletteId.entries.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    row.forEach { id ->
                        val (start, end) = PaperPalettes.swatchGradient(id)
                        PaletteOption(
                            label = stringResource(id.paletteLabelRes()),
                            start = start,
                            end = end,
                            active = id == selected,
                            onClick = { onPalette(id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteOption(
    label: String,
    start: Color,
    end: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val shape = RoundedCornerShape(16.dp)
    val resting = if (night) {
        paper.tone400.copy(alpha = 0.72f)
    } else {
        lerp(paper.surface, paper.chromeFill, 0.4f)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) paper.accent.copy(alpha = 0.12f) else resting)
            .border(
                width = if (active) 1.7.dp else 1.dp,
                color = if (active) paper.accent else paper.tone500.copy(alpha = 0.78f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .shadow(if (active) 4.dp else 1.dp, RoundedCornerShape(8.dp), clip = false)
                .background(Brush.linearGradient(listOf(start, end))),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (active) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = label,
            color = paper.textStrong,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
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
    fitSingleLine: Boolean = false,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (night) paper.tone400 else lerp(paper.surface, paper.chromeFill, 0.35f))
            .border(1.dp, paper.accent.copy(alpha = if (night) 0.38f else 0.2f), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentedOption(
            label = firstLabel,
            selected = firstSelected,
            onClick = onFirst,
            fitSingleLine = fitSingleLine,
            modifier = Modifier.weight(1f),
        )
        SegmentedOption(
            label = secondLabel,
            selected = !firstSelected,
            onClick = onSecond,
            fitSingleLine = fitSingleLine,
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
    fitSingleLine: Boolean = false,
) {
    val paper = LocalPaperColors.current
    var fontSize by remember(label) { mutableStateOf(MushafOptionFont) }
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = label,
        color = if (selected) paper.pageBackground else paper.textStrong,
        fontSize = if (fitSingleLine) fontSize else MushafOptionFont,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = if (fitSingleLine) 1 else Int.MAX_VALUE,
        softWrap = !fitSingleLine,
        overflow = TextOverflow.Clip,
        style = if (fitSingleLine) {
            TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MushafOptionLine,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            )
        } else {
            TextStyle.Default
        },
        onTextLayout = if (fitSingleLine) {
            { result ->
                if (result.didOverflowWidth && fontSize > MushafOptionMinFont) {
                    fontSize *= 0.92f
                }
            }
        } else {
            null
        },
        modifier = modifier
            .clip(shape)
            .background(if (selected) paper.accent else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

private val MushafOptionFont = 12.sp
private val MushafOptionLine = 16.sp
private val MushafOptionMinFont = 8.sp

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
        TextMushafSizePreview(sizeSp = local)
    }
}

private const val TEXT_MUSHAF_PREVIEW = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"

@Composable
private fun TextMushafSizePreview(sizeSp: Float) {
    val paper = LocalPaperColors.current
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val fontManager = remember(context, inspection) {
        if (inspection) {
            null
        } else {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsFontEntryPoint::class.java,
            ).qcfFontManager()
        }
    }
    var uthmanicFace by remember(fontManager) {
        mutableStateOf(fontManager?.peekUthmanicTypeface())
    }
    LaunchedEffect(fontManager) {
        if (fontManager != null && uthmanicFace == null) {
            uthmanicFace = fontManager.loadUthmanicTypeface()
        }
    }
    val family = uthmanicFace?.let { FontFamily(it) } ?: FontFamily.Serif
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = TEXT_MUSHAF_PREVIEW,
            color = paper.textStrong,
            fontFamily = family,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.75f).sp,
            textAlign = TextAlign.Center,
            style = TextStyle(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsFontEntryPoint {
    fun qcfFontManager(): QcfFontManager
}

@Composable
private fun AboutAppDialog(onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val context = LocalContext.current
    val email = stringResource(R.string.settings_contact_email)
    val githubUrl = stringResource(R.string.settings_about_github_url)
    val privacyUrl = stringResource(R.string.settings_about_privacy_url)
    val displayedVersionName = BuildConfig.VERSION_NAME.removeSuffix("-debug")
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(paper.pageBody),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides uiLayoutDirection()) {
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
                    color = if (LocalNightMode.current) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            }
            val bodyDirection = uiLayoutDirection()
            val bodyScript = if (bodyDirection == LayoutDirection.Ltr) TextDirection.Ltr else TextDirection.Rtl
            CompositionLocalProvider(LocalLayoutDirection provides bodyDirection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(stringResource(R.string.settings_about_p1), color = paper.textPrimary, fontSize = 15.sp, lineHeight = 24.sp, textAlign = TextAlign.Start, style = TextStyle(textDirection = bodyScript))
                Text(stringResource(R.string.settings_about_p2), color = paper.textPrimary, fontSize = 15.sp, lineHeight = 24.sp, textAlign = TextAlign.Start, style = TextStyle(textDirection = bodyScript))
                Text(stringResource(R.string.settings_about_p3), color = paper.textStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, style = TextStyle(textDirection = bodyScript))
                HorizontalDivider(color = paper.tone500.copy(alpha = 0.45f))
                Text(
                    text = stringResource(R.string.settings_about_open_source_title),
                    color = paper.textStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    style = TextStyle(textDirection = bodyScript),
                )
                Text(
                    text = stringResource(R.string.settings_about_open_source_text),
                    color = paper.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                    style = TextStyle(textDirection = bodyScript),
                )
                Text(
                    text = stringResource(R.string.settings_about_github),
                    color = paper.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    style = TextStyle(textDirection = bodyScript),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(githubUrl),
                                    ),
                                )
                            }
                        }
                        .padding(vertical = 4.dp),
                )
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
                    style = TextStyle(textDirection = bodyScript),
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
                        textAlign = TextAlign.Start,
                        style = TextStyle(textDirection = bodyScript),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_about_privacy),
                    color = paper.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    style = TextStyle(textDirection = bodyScript),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(privacyUrl),
                                    ),
                                )
                            }
                        }
                        .padding(vertical = 4.dp),
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

    fun setLanguage(languageTag: String?) {
        viewModelScope.launch { preferences.setAppLanguage(languageTag) }
    }
}
