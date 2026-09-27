package com.quransunah.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class PaperPaletteId(val id: String, val arabicName: String, val swatch: Color) {
    White("white", "أبيض ناصع", Color(0xFFFFFFFF)),
    Beige("beige", "بيج", Color(0xFFFBF9F6)),
    Blue("blue", "أزرق", Color(0xFFFAFCFE)),
    Green("green", "أخضر", Color(0xFFFAFDFA)),
    Purple("purple", "بنفسجي", Color(0xFFFCFBFE)),
    Rose("rose", "وردي", Color(0xFFFEFAFB)),
    Teal("teal", "فيروزي", Color(0xFFFAFDFC)),
    Amber("amber", "عنبري", Color(0xFFFEFCF8)),
    ;

    companion object {
        fun fromId(id: String): PaperPaletteId = entries.find { it.id == id } ?: Beige
    }
}

data class PaperColors(
    val tone500: Color,
    val tone400: Color,
    val tone300: Color,
    val tone200: Color,
    val tone100: Color,
    val textPrimary: Color,
    val textStrong: Color,
    val textMuted: Color,
    val textLabel: Color,
    val accent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val pageBackground: Color,
    val pageBody: Color,
    /**
     * Surah banner / header / nav fill — React `--app-theme-chrome-fill`
     * (day: THEME_CHROME_COLORS_LIGHT, night: 38% pastel + 62% surface).
     */
    val chromeFill: Color,
    /** Interactive chrome / selection stroke (palette accent family). */
    val darkAccent: Color,
    val darkAccentInner: Color,
    /** Soft girih/star pattern tint (React gold @ low alpha). */
    val decorPattern: Color,
    val navBubble: Color,
    val ayahHighlight: Color,
    /** Elevated dialog/sheet surface. */
    val surface: Color,
)

object PaperPalettes {
    val CharcoalBlack = Color(0xFF000000)

    /** React `ChromeFrameDecor` strokes (`#c5a059` / `#e3d0ad`). */
    val FrameGold = Color(0xFFC5A059)
    val FrameGoldInner = Color(0xFFE3D0AD)

    /**
     * React `THEME_CHROME_COLORS` — night base pastels before dark mix.
     * Day uses [chromeLight] instead.
     */
    private fun chromePastel(id: PaperPaletteId): Color = when (id) {
        PaperPaletteId.White -> Color(0xFFF5F5F5)
        PaperPaletteId.Purple -> Color(0xFFE2D4F3)
        PaperPaletteId.Green -> Color(0xFFD0EBD6)
        PaperPaletteId.Blue -> Color(0xFFC9E2F8)
        PaperPaletteId.Beige -> Color(0xFFF5EBD9)
        PaperPaletteId.Rose -> Color(0xFFEDD4DE)
        PaperPaletteId.Teal -> Color(0xFFD0EBE8)
        PaperPaletteId.Amber -> Color(0xFFF5E8CC)
    }

    /** React `THEME_CHROME_COLORS_LIGHT` — daytime chrome / surah frame fill. */
    private fun chromeLight(id: PaperPaletteId): Color = when (id) {
        PaperPaletteId.White -> Color(0xFFE8E8E8)
        PaperPaletteId.Purple -> Color(0xFFD4C4EB)
        PaperPaletteId.Green -> Color(0xFFC0E0C8)
        PaperPaletteId.Blue -> Color(0xFFB8D6F0)
        PaperPaletteId.Beige -> Color(0xFFE8DCC4)
        PaperPaletteId.Rose -> Color(0xFFDFC4D0)
        PaperPaletteId.Teal -> Color(0xFFC0DEDA)
        PaperPaletteId.Amber -> Color(0xFFE8D8B8)
    }

    /** Legacy charcoal baseline (white/slate night falls back toward this structure). */
    val charcoalNight = PaperColors(
        tone500 = Color(0xFF4C4D4C),
        tone400 = Color(0xFF1E2228),
        tone300 = Color(0xFF1E2228),
        tone200 = Color(0xFF121417),
        tone100 = Color(0xFF121417),
        textPrimary = Color(0xFFFFFFFF),
        textStrong = Color(0xFFFFFFFF),
        textMuted = Color(0xFFA0A5AD),
        textLabel = Color(0xFFA0A5AD),
        accent = Color(0xFF818180),
        accentHover = Color(0xFFE7E6E7),
        accentSoft = Color(0xFF4C4D4C),
        pageBackground = Color(0xFF121417),
        pageBody = Color(0xFF1E2228),
        chromeFill = lerp(Color(0xFFF5F5F5), Color(0xFF1E2228), 0.62f),
        darkAccent = Color(0xFF4C4D4C),
        darkAccentInner = Color(0xFF818180),
        decorPattern = FrameGold.copy(alpha = 0.16f),
        navBubble = Color(0xFF1E2228),
        ayahHighlight = Color(0xFF818180).copy(alpha = 0.22f),
        surface = Color(0xFF1E2228),
    )

    fun resolve(id: PaperPaletteId, night: Boolean): PaperColors {
        if (night) return nightColors(id)
        return when (id) {
            PaperPaletteId.White -> light(
                id = id,
                t500 = 0xFFE4E4E4, t400 = 0xFFEDEDED, t300 = 0xFFF5F5F5, t200 = 0xFFFAFAFA, t100 = 0xFFFFFFFF,
                text = 0xFF2A2A2A, strong = 0xFF1A1A1A, muted = 0xFF5C5C5C, label = 0xFF4A4A4A,
                accent = 0xFF3A3A3A, hover = 0xFF262626, soft = 0xFF6A6A6A,
            )
            PaperPaletteId.Beige -> light(
                id = id,
                t500 = 0xFFDEC8B7, t400 = 0xFFEAE0D5, t300 = 0xFFF2ECE4, t200 = 0xFFFCFAF7, t100 = 0xFFFBF9F6,
                text = 0xFF3D342E, strong = 0xFF2B2420, muted = 0xFF6B5C52, label = 0xFF7A6658,
                accent = 0xFF6D5843, hover = 0xFF5C4A3A, soft = 0xFF8B7355,
            )
            PaperPaletteId.Blue -> light(
                id = id,
                t500 = 0xFFB8CCE8, t400 = 0xFFD0DFF0, t300 = 0xFFE8F0FA, t200 = 0xFFF5F9FD, t100 = 0xFFFAFCFE,
                text = 0xFF2E3A4A, strong = 0xFF1E2A38, muted = 0xFF5A6B7D, label = 0xFF4A5C6E,
                accent = 0xFF3D5A80, hover = 0xFF2F4766, soft = 0xFF5A7A9E,
            )
            PaperPaletteId.Green -> light(
                id = id,
                t500 = 0xFFB8D4BC, t400 = 0xFFD0E8D4, t300 = 0xFFE8F5EA, t200 = 0xFFF5FAF6, t100 = 0xFFFAFDFA,
                text = 0xFF2E3F32, strong = 0xFF1E2E22, muted = 0xFF5A6D5E, label = 0xFF4A5E4E,
                accent = 0xFF3D6B4F, hover = 0xFF2F5640, soft = 0xFF5A8A6A,
            )
            PaperPaletteId.Purple -> light(
                id = id,
                t500 = 0xFFCFC0E4, t400 = 0xFFE2D8F0, t300 = 0xFFF0EBF8, t200 = 0xFFF8F5FC, t100 = 0xFFFCFBFE,
                text = 0xFF3A2E4A, strong = 0xFF2A1E38, muted = 0xFF6D5A7D, label = 0xFF5E4A6E,
                accent = 0xFF5C4A7A, hover = 0xFF4A3A66, soft = 0xFF7A6A9E,
            )
            PaperPaletteId.Rose -> light(
                id = id,
                t500 = 0xFFE4C0CC, t400 = 0xFFEFD4DC, t300 = 0xFFF8E8EC, t200 = 0xFFFDF5F7, t100 = 0xFFFEFAFB,
                text = 0xFF4A2E38, strong = 0xFF381E28, muted = 0xFF7D5A66, label = 0xFF6E4A58,
                accent = 0xFF7A4A5A, hover = 0xFF663A4A, soft = 0xFF9A6A7A,
            )
            PaperPaletteId.Teal -> light(
                id = id,
                t500 = 0xFFB8D8D4, t400 = 0xFFD0E8E4, t300 = 0xFFE8F5F3, t200 = 0xFFF5FAFA, t100 = 0xFFFAFDFC,
                text = 0xFF2E403E, strong = 0xFF1E302E, muted = 0xFF5A6D6A, label = 0xFF4A5E5A,
                accent = 0xFF3D6B66, hover = 0xFF2F5652, soft = 0xFF5A8A84,
            )
            PaperPaletteId.Amber -> light(
                id = id,
                t500 = 0xFFE8D4B0, t400 = 0xFFF0E4C8, t300 = 0xFFF8F0E0, t200 = 0xFFFDF9F2, t100 = 0xFFFEFCF8,
                text = 0xFF4A3E2E, strong = 0xFF382E1E, muted = 0xFF7D6D5A, label = 0xFF6E5E4A,
                accent = 0xFF7A6030, hover = 0xFF664E26, soft = 0xFF9A8048,
            )
        }
    }

    /** Settings swatches — same gradients as React `APP_COLOR_SCHEMES`. */
    fun swatchGradient(id: PaperPaletteId): Pair<Color, Color> = when (id) {
        PaperPaletteId.White -> Color(0xFF4C4D4C) to Color(0xFFE7E6E7)
        PaperPaletteId.Beige -> Color(0xFF5C2D0C) to Color(0xFFC86218)
        PaperPaletteId.Blue -> Color(0xFF0D3A66) to Color(0xFF1876D3)
        PaperPaletteId.Green -> Color(0xFF006165) to Color(0xFF1B8F67)
        PaperPaletteId.Purple -> Color(0xFF4A158D) to Color(0xFF7B1FA2)
        PaperPaletteId.Rose -> Color(0xFF6D1F3A) to Color(0xFFC2185B)
        PaperPaletteId.Teal -> Color(0xFF004D47) to Color(0xFF00897B)
        PaperPaletteId.Amber -> Color(0xFF6B4E0C) to Color(0xFFD4A017)
    }

    private fun nightColors(id: PaperPaletteId): PaperColors = when (id) {
        PaperPaletteId.Beige -> luxuryNight(
            id = id,
            bg = 0xFF120A05,
            surface = 0xFF22140C,
            elevated = 0xFF2C1A10,
            accent = 0xFFAA713B,
            secondary = 0xFF5C2D0C,
            text = 0xFFEFE4D7,
            muted = 0xFFB8A08D,
        )
        PaperPaletteId.Blue -> luxuryNight(
            id = id,
            bg = 0xFF040D1A,
            surface = 0xFF0A192F,
            elevated = 0xFF10243F,
            accent = 0xFF4B82B8,
            secondary = 0xFF0D3A66,
            text = 0xFFE4ECF4,
            muted = 0xFF8CA8C6,
        )
        PaperPaletteId.Purple -> luxuryNight(
            id = id,
            bg = 0xFF0F0619,
            surface = 0xFF1D0E30,
            elevated = 0xFF281440,
            accent = 0xFF8968A8,
            secondary = 0xFF4A158D,
            text = 0xFFECE5F3,
            muted = 0xFFB59EC9,
        )
        PaperPaletteId.White -> luxuryNight(
            id = id,
            bg = 0xFF121417,
            surface = 0xFF1E2228,
            elevated = 0xFF282C34,
            accent = 0xFF818180,
            secondary = 0xFF4C4D4C,
            text = 0xFFE9EAEC,
            muted = 0xFFA0A5AD,
            accentHover = 0xFFB8B9BA,
        )
        PaperPaletteId.Green -> luxuryNight(
            id = id,
            bg = 0xFF05130E,
            surface = 0xFF0C241B,
            elevated = 0xFF123028,
            accent = 0xFF4B967D,
            secondary = 0xFF006165,
            text = 0xFFE2EFEA,
            muted = 0xFF8DBDAF,
        )
        PaperPaletteId.Rose -> luxuryNight(
            id = id,
            bg = 0xFF14080E,
            surface = 0xFF261018,
            elevated = 0xFF321820,
            accent = 0xFFB06B82,
            secondary = 0xFF6D1F3A,
            text = 0xFFF0E4E8,
            muted = 0xFFC49AAC,
        )
        PaperPaletteId.Teal -> luxuryNight(
            id = id,
            bg = 0xFF041210,
            surface = 0xFF0A2422,
            elevated = 0xFF10302C,
            accent = 0xFF4B9A91,
            secondary = 0xFF004D47,
            text = 0xFFE1EFED,
            muted = 0xFF8DBDB8,
        )
        PaperPaletteId.Amber -> luxuryNight(
            id = id,
            bg = 0xFF120E05,
            surface = 0xFF221C0C,
            elevated = 0xFF2C2412,
            accent = 0xFFC09A4C,
            secondary = 0xFF6B4E0C,
            text = 0xFFF0E8D5,
            muted = 0xFFB8A878,
        )
    }

    private fun luxuryNight(
        id: PaperPaletteId,
        bg: Long,
        surface: Long,
        elevated: Long,
        accent: Long,
        secondary: Long,
        text: Long,
        muted: Long,
        accentHover: Long = accent,
    ): PaperColors {
        val accentColor = Color(accent)
        val secondaryColor = Color(secondary)
        val surfaceColor = Color(surface)
        // React `--app-theme-chrome-fill-effective`: 38% pastel + 62% dark-700.
        val chrome = lerp(chromePastel(id), surfaceColor, 0.62f)
        val goldMix = lerp(FrameGold, accentColor, 0.28f)
        return PaperColors(
            tone500 = secondaryColor,
            tone400 = Color(elevated),
            tone300 = surfaceColor,
            tone200 = Color(bg),
            tone100 = Color(bg),
            textPrimary = Color(text),
            textStrong = Color(text),
            textMuted = Color(muted),
            textLabel = Color(muted),
            accent = accentColor,
            accentHover = Color(accentHover),
            accentSoft = secondaryColor,
            pageBackground = Color(bg),
            pageBody = surfaceColor,
            chromeFill = chrome,
            darkAccent = secondaryColor,
            darkAccentInner = lerp(secondaryColor, accentColor, 0.45f),
            decorPattern = goldMix.copy(alpha = 0.10f),
            navBubble = Color(elevated),
            ayahHighlight = accentColor.copy(alpha = 0.16f),
            surface = Color(elevated),
        )
    }

    private fun light(
        id: PaperPaletteId,
        t500: Long, t400: Long, t300: Long, t200: Long, t100: Long,
        text: Long, strong: Long, muted: Long, label: Long,
        accent: Long, hover: Long, soft: Long,
    ): PaperColors {
        val accentColor = Color(accent)
        val hoverColor = Color(hover)
        val softColor = Color(soft)
        val tone500Color = Color(t500)
        return PaperColors(
            tone500 = tone500Color,
            tone400 = Color(t400),
            tone300 = Color(t300),
            tone200 = Color(t200),
            tone100 = Color(t100),
            textPrimary = Color(text),
            textStrong = Color(strong),
            textMuted = Color(muted),
            textLabel = Color(label),
            accent = accentColor,
            accentHover = hoverColor,
            accentSoft = softColor,
            pageBackground = Color(t200),
            pageBody = Color(t300),
            chromeFill = chromeLight(id),
            // Soft interactive stroke: accent (not near-black hover).
            darkAccent = accentColor,
            darkAccentInner = softColor,
            decorPattern = FrameGold.copy(alpha = 0.18f),
            navBubble = tone500Color,
            ayahHighlight = accentColor.copy(alpha = 0.14f),
            surface = Color.White,
        )
    }
}
