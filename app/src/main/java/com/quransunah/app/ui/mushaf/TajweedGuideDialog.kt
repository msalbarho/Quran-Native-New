package com.quransunah.app.ui.mushaf

import com.quransunah.app.ui.shell.GoldCornerFrame
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quransunah.app.R
import com.quransunah.app.core.ArabicOrdinals
import com.quransunah.app.core.HizbQuarterKind
import com.quransunah.app.core.UiNumbers
import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalPaperColors

/** Uses the most recent real quarter boundary, including pages without a boundary. */
internal fun currentHeaderQuarter(pageNumber: Int, quarters: List<DivisionInfo>): DivisionInfo? =
    quarters.filter { it.pageNumber <= pageNumber }
        .maxWithOrNull(compareBy<DivisionInfo> { it.pageNumber }.thenBy { it.number })

@Composable
fun localizedHeaderHizb(page: MushafPage?, quarters: List<DivisionInfo>): String {
    val boundary = currentHeaderQuarter(page?.pageNumber ?: 1, quarters)
    val parts = boundary?.name?.let(ArabicOrdinals::hizbBadgeParts)
    val fraction = when (parts?.kind) {
        HizbQuarterKind.Quarter -> stringResource(R.string.mushaf_hizb_quarter)
        HizbQuarterKind.Half -> stringResource(R.string.mushaf_hizb_half)
        HizbQuarterKind.ThreeQuarters -> stringResource(R.string.mushaf_hizb_three) + " " +
            stringResource(R.string.mushaf_hizb_quarters)
        else -> ""
    }
    val number = parts?.number ?: boundary?.parentNumber?.takeIf { it > 0 } ?: page?.hizbNumber ?: 1
    val language = com.quransunah.app.core.ArabicRtl.currentTag(androidx.compose.ui.platform.LocalContext.current)
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val words = androidx.compose.runtime.remember(number, language, resources) { headerNumberWords(number, language, resources) }
    return listOf(fraction, stringResource(R.string.mushaf_hizb_full), words)
        .filter { it.isNotEmpty() }.joinToString(" ")
}

@Composable
fun TajweedGuideDialog(onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    // Tajweed ink has semantic meaning and is intentionally independent of UI themes.
    val entries = listOf(
        Color(0xFFB50000) to R.string.tajweed_rule_1,
        Color(0xFFF40000) to R.string.tajweed_rule_2,
        Color(0xFFFF7B00) to R.string.tajweed_rule_3,
        Color(0xFFCE9E00) to R.string.tajweed_rule_4,
        Color(0xFF09B000) to R.string.tajweed_rule_5,
        Color(0xFF3F48E6) to R.string.tajweed_rule_6,
        Color(0xFF2FADFF) to R.string.tajweed_rule_7,
        Color(0xFFA5A5A5) to R.string.tajweed_rule_8,
    )
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.border(ChromeTokens.GoldStroke, ChromeTokens.Gold, GoldCornerFrameShape(20.dp)),
        shape = GoldCornerFrameShape(20.dp),
        containerColor = paper.chromeFill,
        titleContentColor = paper.textStrong,
        textContentColor = paper.textStrong,
        title = { Text(stringResource(R.string.tajweed_guide)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                entries.forEach { (color, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(20.dp).background(color, CircleShape))
                        Text(stringResource(label), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close), color = paper.textStrong) }
        },
    )
}
