package com.quransunah.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.AyahSearchHit
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalPaperColors

@Composable
fun SearchPane(
    onSelectHit: (AyahSearchHit) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    SearchPaneContent(
        ui = ui,
        showTitle = showTitle,
        onQueryChange = viewModel::setQuery,
        onClear = viewModel::clear,
        onSelectHit = onSelectHit,
        modifier = modifier,
    )
}

@Composable
fun SearchPaneContent(
    ui: SearchUiState,
    showTitle: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelectHit: (AyahSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(paper.pageBackground)
            .padding(horizontal = 16.dp),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.search_title),
                color = paper.textStrong,
                fontSize = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                textAlign = TextAlign.Center,
            )
        }
        OutlinedTextField(
            value = ui.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.search_placeholder), color = paper.textMuted)
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = paper.accent,
                )
            },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = paper.textStrong,
                unfocusedTextColor = paper.textPrimary,
                focusedBorderColor = paper.accent,
                unfocusedBorderColor = paper.tone500,
                cursorColor = paper.accent,
            ),
        )
        Text(
            text = stringResource(R.string.search_hint),
            color = paper.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        when {
            ui.loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp),
                    color = paper.accent,
                )
            }
            ui.tooShort -> {
                Text(
                    text = stringResource(R.string.search_short_query),
                    color = paper.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            ui.query.isNotBlank() && ui.results.isEmpty() && ui.indexed -> {
                Text(
                    text = stringResource(R.string.search_empty),
                    color = paper.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.results, key = { "${it.surah}:${it.ayah}" }) { hit ->
                        SearchHitRow(hit = hit, onClick = { onSelectHit(hit) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: AyahSearchHit, onClick: () -> Unit) {
    val paper = LocalPaperColors.current
    val snippet = highlightedSnippet(hit)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(paper.tone300)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = hit.surahName.ifBlank { stringResource(R.string.surah_fallback, EasternArabic.format(hit.surah)) },
                color = paper.textStrong,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.search_page_abbr, EasternArabic.format(hit.pageNumber)),
                color = paper.textMuted,
                fontSize = 13.sp,
            )
        }
        Text(
            text = stringResource(
                R.string.search_result_meta,
                EasternArabic.format(hit.ayah),
                EasternArabic.format(hit.juzNumber.coerceAtLeast(1)),
            ),
            color = paper.textLabel,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Text(
            text = snippet,
            color = paper.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun highlightedSnippet(hit: AyahSearchHit) = buildAnnotatedString {
    append(hit.snippet)
    val start = hit.highlightStart
    val end = hit.highlightEnd
    if (start in 0 until hit.snippet.length && end in (start + 1)..hit.snippet.length) {
        val paper = LocalPaperColors.current
        addStyle(
            SpanStyle(
                background = paper.accentSoft.copy(alpha = 0.35f),
                fontWeight = FontWeight.Bold,
                color = paper.textStrong,
            ),
            start,
            end,
        )
    }
}

@ArabicPreviews
@Composable
private fun SearchPanePreview() {
    PreviewTheme {
        SearchPaneContent(
            ui = PreviewFixtures.searchUi,
            showTitle = true,
            onQueryChange = {},
            onClear = {},
            onSelectHit = {},
        )
    }
}
