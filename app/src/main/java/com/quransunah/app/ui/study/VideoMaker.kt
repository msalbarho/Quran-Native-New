package com.quransunah.app.ui.study

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.defaultMinSize
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.shell.ChromeTokens
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.quransunah.app.fonts.QcfFontManager
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quransunah.app.R
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.LanguageRegistry
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.data.audio.AudioUrls
import com.quransunah.app.data.catalog.ReciterNames
import com.quransunah.app.ui.index.SurahNames
import com.quransunah.app.share.*
import com.quransunah.app.ui.settings.PaletteOption
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import com.quransunah.app.ui.shell.TiledOrnamentBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMaker(viewModel: StudyViewModel, ui: StudyUiState, onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val MakerGold = paper.accent
    val MakerPaper = paper.pageBackground
    var step by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(VideoMakerOptions()) }
    var reciters by remember { mutableStateOf(false) }
    var styleTab by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf<AyahShareContent?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewStamp by remember { mutableIntStateOf(0) }
    var retiredPreview by remember { mutableStateOf<Bitmap?>(null) }
    var geometry by remember { mutableStateOf(MakerPreviewGeometry()) }
    var previewClockMs by remember { mutableLongStateOf(0L) }
    var previewTimings by remember { mutableStateOf(emptyList<WordSegment>()) }
    val exportSession=remember { MakerExportSession { android.util.Log.d("MakerExportSession",it) } }
    var sharing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exporter = viewModel.videoMakerExporter
    val wasPlaying = remember { viewModel.audioPlayer.snapshot.value.isPlaying }
    DisposableEffect(exporter) {
        onDispose {
            exporter.releaseMakerPreview()
            if (wasPlaying) runCatching { viewModel.audioPlayer.resume() }
        }
    }
    LaunchedEffect(ui.videoFromAyah, ui.videoToAyah) {
        try { content = viewModel.makerContent() } catch (e: Exception) { error = e.message }
        saved = false
    }
    LaunchedEffect(options) { saved = false }
    var focusedBackground by remember { mutableStateOf(options.background) }
    var previewAyahSpans by remember { mutableStateOf(emptyList<Pair<LongRange, Int>>()) }
    var previewClips by remember { mutableStateOf(emptyList<PreviewClip>()) }
    var previewPlaying by remember { mutableStateOf(true) }
    var previewRestart by remember { mutableIntStateOf(0) }
    val onPreview = step in 1..3 && !busy
    LaunchedEffect(content, ui.videoReciterId) {
        previewTimings = emptyList()
        previewClockMs = 0L
        previewAyahSpans = emptyList()
        previewClips = emptyList()
        val data = content ?: return@LaunchedEffect
        val reciter = (ui.videoReciters + ui.reciters).firstOrNull { it.id == ui.videoReciterId }
        try {
            val store = QulWordTimings(context)
            val built = withContext(Dispatchers.IO) {
                data.verses.mapNotNull { verse ->
                    val timing = runCatching { store.loadForReciter(ui.videoReciterId, data.surah, verse.ayah) }
                        .getOrDefault(QulAyahTiming())
                    val qulUrl = QulRecitations.ayahAudioUrl(ui.videoReciterId, data.surah, verse.ayah)
                    val url = when {
                        QulRecitations.hasWordTimingCatalog(ui.videoReciterId) ->
                            qulUrl
                                ?: timing.audioUrl
                                ?: timing.surahAudioUrl
                        else ->
                            timing.audioUrl
                                ?: timing.surahAudioUrl
                                ?: qulUrl
                                ?: reciter?.let { runCatching { AudioUrls.ayahStreamUri(it, data.surah, verse.ayah).toString() }.getOrNull() }
                    } ?: return@mapNotNull null
                    PreviewClip(
                        ayah = verse.ayah,
                        url = url,
                        seekMs = timing.cropStartMs ?: 0L,
                        stopMs = timing.cropEndMs,
                        segments = timing.segments,
                    )
                }
            }
            previewClips = built
            if (built.isEmpty()) {
                val combined = ArrayList<WordSegment>()
                val spans = ArrayList<Pair<LongRange, Int>>()
                var offset = 0L
                data.verses.forEach { verse ->
                    val timing = withContext(Dispatchers.IO) {
                        runCatching { store.loadForReciter(ui.videoReciterId, data.surah, verse.ayah) }.getOrDefault(QulAyahTiming())
                    }
                    val duration = (timing.segments.maxOfOrNull { it.endMs } ?: 1L) + 200
                    combined += timing.segments.map { it.copy(startMs = it.startMs + offset, endMs = it.endMs + offset) }
                    spans += (offset until offset + duration) to verse.ayah
                    offset += duration
                }
                previewTimings = combined
                previewAyahSpans = spans
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    LaunchedEffect(onPreview, previewTimings, previewClips) {
        if (!onPreview || previewClips.isNotEmpty() || previewTimings.isEmpty()) return@LaunchedEffect
        val duration = previewTimings.last().endMs.coerceAtLeast(1000)
        val start = android.os.SystemClock.uptimeMillis()
        while (isActive) {
            previewClockMs = (android.os.SystemClock.uptimeMillis() - start) % duration
            delay(66)
        }
    }
    LaunchedEffect(previewClips, previewRestart, onPreview) {
        val clips = previewClips
        if (!onPreview || clips.isEmpty()) return@LaunchedEffect
        viewModel.audioPlayer.pause()
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        try {
            var index = 0
            var origin = 0L
            suspend fun arm(clip: PreviewClip): Boolean {
                val ready = CompletableDeferred<Boolean>()
                player.reset()
                player.setDataSource(context, Uri.parse(clip.url))
                val seek = clip.seekMs.coerceAtLeast(0L).toInt()
                player.setOnPreparedListener {
                    if (seek <= 0) {
                        if (!ready.isCompleted) ready.complete(true)
                    } else {
                        player.setOnSeekCompleteListener { if (!ready.isCompleted) ready.complete(true) }
                        player.seekTo(seek)
                    }
                }
                player.setOnErrorListener { _, _, _ ->
                    if (!ready.isCompleted) ready.complete(false)
                    true
                }
                player.prepareAsync()
                if (kotlinx.coroutines.withTimeoutOrNull(12_000) { ready.await() } != true) return false
                previewTimings = clip.segments.map { it.copy(startMs = it.startMs + origin, endMs = it.endMs + origin) }
                previewAyahSpans = listOf((origin until origin + 3_600_000L) to clip.ayah)
                previewClockMs = origin
                if (previewPlaying) player.start()
                return true
            }
            while (isActive) {
                val clip = clips[index]
                if (!arm(clip)) {
                    index = (index + 1) % clips.size
                    if (index == 0) origin = 0L
                    continue
                }
                var finished = false
                while (isActive && !finished) {
                    if (!previewPlaying) {
                        if (runCatching { player.isPlaying }.getOrDefault(false)) player.pause()
                        delay(80)
                        continue
                    }
                    val position = runCatching { player.currentPosition.toLong() }.getOrDefault(clip.seekMs)
                    val local = (position - clip.seekMs).coerceAtLeast(0L)
                    val playingNow = runCatching { player.isPlaying }.getOrDefault(false)
                    val stop = clip.stopMs
                    if (stop != null && position >= stop) finished = true
                    else if (local > 40L && !playingNow) finished = true
                    else {
                        if (!playingNow) player.start()
                        previewClockMs = origin + local
                        delay(50)
                    }
                }
                val played = (runCatching { player.currentPosition.toLong() }.getOrDefault(clip.seekMs) - clip.seekMs).coerceAtLeast(0L)
                origin += played.coerceAtLeast(1L)
                index += 1
                if (index >= clips.size) {
                    index = 0
                    origin = 0L
                }
            }
        } finally {
            runCatching {
                player.reset()
                player.release()
            }
        }
    }
    LaunchedEffect(content) {
        val data = content ?: return@LaunchedEffect
        snapshotFlow { Triple(options,previewClockMs,previewTimings) to (focusedBackground to step) }.conflate().collect { (triple, focusStep) ->
            val (settings,clock,timings)=triple
            val (focused, currentStep)=focusStep
            val durationMs = (timings.lastOrNull()?.endMs ?: 1L).coerceAtLeast(clock).coerceAtLeast(1L)
            val held = timings.cursorSegments(durationMs * 1000).lastOrNull { clock >= it.startMs }
            val spoken = held?.let { word -> timings.firstOrNull { it.position == word.position && clock in it.startMs until it.endMs } }
            val fraction = when {
                held == null -> 0f
                spoken == null -> 1f
                else -> ((clock - spoken.startMs).toFloat() / (spoken.endMs - spoken.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
            val ayah=previewAyahSpans.firstOrNull { clock in it.first }?.second ?: data.verses.first().ayah
            try {
                var nextGeometry=geometry
                val image=exporter.makerPreview(data,settings,held?.position,fraction,clock*1000,
                    durationMs * 1000,onGeometry={nextGeometry=it},
                    previewBackground=if(currentStep==1) focused else null, activeAyah=ayah)
                val shown = preview
                retiredPreview?.let { if (it !== shown && it !== image && !it.isRecycled) it.recycle() }
                retiredPreview = shown?.takeIf { it !== image }
                geometry=nextGeometry;preview=image;previewStamp++
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            delay(16)
        }
    }
    LaunchedEffect(ui.videoReciterId) { saved = false }
    fun export(share:Boolean=false) {
        if(busy)return
        val data=content ?: return
        val key=MakerExportKey(data,ui.videoReciterId,options)
        busy=true;sharing=share;error=null
        scope.launch {
            try {
                val file=exportSession.ensureSaved(key,
                    create={exporter.makeVideo(data,key.reciterId,key.options)},
                    save={exporter.saveMakerVideo(it)})
                saved=true
                if(share)exporter.shareMakerVideo(file)
                else android.widget.Toast.makeText(context, context.getString(R.string.video_saved_gallery), android.widget.Toast.LENGTH_SHORT).show()
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message ?: context.getString(R.string.video_prepare_failed)}
            finally{busy=false}
        }
    }
    val uiDirection = uiLayoutDirection()
    val uiLanguage = ArabicRtl.currentTag(LocalContext.current)
    val translationName = content?.translationTag?.let { LanguageRegistry.byTag(it)?.nativeName ?: it }
        ?: stringResource(R.string.video_translation)
    val translationLabel = stringResource(R.string.video_show_translation, translationName)
    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides uiDirection) {
            BackHandler { if (!busy) { if (step > 0) step-- else onClose() } }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MakerPaper)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
            ) {
                TiledOrnamentBackground(Modifier.matchParentSize().alpha(0.14f))
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.video_new), fontWeight = FontWeight.Bold, color = MakerGold)
                        TextButton(onClick = { if (!busy) onClose() }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.video_cancel), color = MakerGold) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(
                            R.string.video_step_verses,
                            R.string.video_step_background,
                            R.string.video_step_style,
                            R.string.video_step_share,
                        ).forEachIndexed { index, name ->
                            TextButton(onClick = { step = index }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("${if (step == index) "●" else if (step > index) "✓" else "○"} ${stringResource(name)}", color = MakerGold,
                                    fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            }
                        }
                    }
                    when (step) {
                        0 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPanel {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        SurahNames.displayName(uiLanguage, ui.word?.surah ?: content?.surah ?: 1, ui.surahName),
                                        color = MakerGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1.1f),
                                    )
                                    MakerNumber(stringResource(R.string.video_from_ayah), ui.videoFromAyah, 1..SurahAyahCounts.ayahCount(ui.word?.surah ?: 1), Modifier.weight(1f), viewModel::setVideoFrom)
                                    MakerNumber(stringResource(R.string.video_to_ayah), ui.videoToAyah, ui.videoFromAyah..minOf(SurahAyahCounts.ayahCount(ui.word?.surah ?: 1), ui.videoFromAyah + 9), Modifier.weight(1f), viewModel::setVideoTo)
                                }
                            }
                            MakerPanel {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(stringResource(R.string.video_reciter), color = MakerGold, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.35f))
                                    OutlinedButton(
                                        onClick = { reciters = true },
                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = GoldCornerFrameShape(10.dp),
                                    ) {
                                        Text(
                                            (ui.videoReciters.ifEmpty { ui.reciters }).firstOrNull { it.id == ui.videoReciterId }?.let { ReciterNames.display(uiLanguage, it.id, it.name) }
                                                ?: stringResource(R.string.video_choose_reciter),
                                            color = MakerGold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            content?.mushafLines?.takeIf { it.isNotEmpty() }?.let { lines ->
                                MakerPanel { MakerMushafLines(lines, viewModel.fontManager) }
                            }
                        }
                        1 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPreviewOrLoading(preview, previewStamp, options, geometry, step, busy) { options = it }
                            PreviewTransport(previewPlaying, { previewPlaying = !previewPlaying }, { previewRestart += 1 })
                            Text(stringResource(R.string.video_backgrounds_count, options.backgrounds.size), color = MakerGold, fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShareBackgrounds.FILES.take(20).chunked(3).forEach { row ->
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        row.forEach { name ->
                                            val image=remember(name) { context.assets.open(ShareBackgrounds.assetPath(name)).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=8 }) } }
                                            Box(Modifier.weight(1f).height(105.dp).border(if(name==focusedBackground)3.dp else if(name in options.backgrounds)2.dp else 1.dp,MakerGold,GoldCornerFrameShape(8.dp)).clickable {
                                                focusedBackground = name
                                                val chosen=options.backgrounds.toMutableList()
                                                if(name !in chosen) chosen.add(name)
                                                options=options.copy(background=chosen.first(),backgrounds=chosen)
                                            }) {
                                                image?.let { Image(it.asImageBitmap(), stringResource(R.string.video_background_item, name), contentScale=ContentScale.Crop, modifier=Modifier.fillMaxSize()) }
                                                if(name in options.backgrounds) Text(
                                                    "${options.backgrounds.indexOf(name)+1}",
                                                    color=Color.White,
                                                    modifier=Modifier.align(Alignment.TopEnd).background(MakerGold).padding(6.dp).clickable {
                                                        if (options.backgrounds.size > 1) {
                                                            val chosen = options.backgrounds.filter { it != name }
                                                            options = options.copy(background = chosen.first(), backgrounds = chosen)
                                                            if (focusedBackground == name) focusedBackground = chosen.first()
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MakerPreviewOrLoading(preview, previewStamp, options, geometry, step, busy) { options = it }
                            PreviewTransport(previewPlaying, { previewPlaying = !previewPlaying }, { previewRestart += 1 })
                            Text(stringResource(R.string.video_drag_hint), color=MakerGold, fontSize = 10.sp, lineHeight = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    R.string.video_tab_font,
                                    R.string.video_translation,
                                    R.string.video_tab_tracking,
                                    R.string.video_tab_title,
                                    R.string.video_tab_text,
                                ).forEachIndexed { index, label ->
                                    MakerChoice(stringResource(label), styleTab == index, Modifier.weight(1f)) { styleTab = index }
                                }
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPanel {
                                when(styleTab) {
                                    0 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(R.string.video_font_madinah, R.string.video_font_uthmani, R.string.video_font_naskh).forEachIndexed { i, label ->
                                            MakerChoice(stringResource(label), options.font == i, Modifier.weight(1f)) { options = options.copy(font = i) }
                                        }
                                    }
                                    1 -> MakerToggle(translationLabel, options.translation) { options = options.copy(translation = it) }
                                    2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(
                                                ShareMushafLayout.TRACKING_BAND to R.string.video_track_band,
                                                ShareMushafLayout.TRACKING_CUMULATIVE to R.string.video_track_color,
                                                ShareMushafLayout.TRACKING_UNDERLINE to R.string.video_track_line,
                                            ).forEach { (mode, label) ->
                                                MakerChoice(stringResource(label), options.trackingMode == mode, Modifier.weight(1f)) {
                                                    options = options.copy(trackingMode = mode)
                                                }
                                            }
                                        }
                                        Text(stringResource(R.string.video_highlight_color), fontSize = 12.sp, color = MakerGold)
                                        val swatches = videoSwatches()
                                        swatches.take(4).let { row ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                row.forEach { (name, value) ->
                                                    PaletteOption(label=name, start=Color(value), end=Color(value), active=options.highlightColor==value,
                                                        onClick={ options=options.copy(highlightColor=value) }, modifier=Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        swatches.drop(4).let { row ->
                                            if (row.isNotEmpty()) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    row.forEach { (name, value) ->
                                                        PaletteOption(label=name, start=Color(value), end=Color(value), active=options.highlightColor==value,
                                                            onClick={ options=options.copy(highlightColor=value) }, modifier=Modifier.weight(1f))
                                                    }
                                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                                }
                                            }
                                        }
                                    }
                                    3 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MakerToggle(stringResource(R.string.video_surah_name), options.showTitle) { options = options.copy(showTitle = it) }
                                        if (options.showTitle) {
                                            Text(stringResource(R.string.video_name_frame), fontSize = 12.sp, color = MakerGold)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(R.string.video_frame_mushaf, R.string.video_frame_ornate).forEachIndexed { i, label ->
                                                    MakerChoice(stringResource(label), options.titleFrame == i, Modifier.weight(1f)) {
                                                        options = options.copy(titleFrame = i)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    4 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.video_text_size, ShareMushafLayout.scaleToFontSize(options.textScale).toInt()), fontSize = 12.sp)
                                        Slider(
                                            value = ShareMushafLayout.scaleToFontSize(options.textScale),
                                            onValueChange = { options = options.copy(textScale = ShareMushafLayout.fontSizeToScale(it)) },
                                            valueRange = ShareMushafLayout.FONT_SIZE_MIN..ShareMushafLayout.FONT_SIZE_MAX,
                                            steps = (ShareMushafLayout.FONT_SIZE_MAX - ShareMushafLayout.FONT_SIZE_MIN).toInt() - 1,
                                        )
                                        MakerToggle(stringResource(R.string.video_backdrop), options.textBackdrop) { options = options.copy(textBackdrop = it) }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            MakerChoice(stringResource(R.string.video_color_white), !options.tajweedText, Modifier.weight(1f)) { options = options.copy(tajweedText = false) }
                                            MakerChoice(stringResource(R.string.video_color_tajweed), options.tajweedText, Modifier.weight(1f)) { options = options.copy(tajweedText = true) }
                                        }
                                        MakerToggle(stringResource(R.string.video_verse_number), options.marker) { options = options.copy(marker = it) }
                                        if (options.marker) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(R.string.video_swatch_gold, R.string.video_color_white, R.string.video_swatch_sky)
                                                    .zip(ShareMushafLayout.MARKER_COLOURS)
                                                    .forEach { (label, colour) ->
                                                        val value = colour.second
                                                        MakerChoice(stringResource(label), options.markerColor == value, Modifier.weight(1f)) {
                                                            options = options.copy(markerColor = value)
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                        else -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPreviewOrLoading(preview, previewStamp, options, geometry, step, busy) { options = it }
                            PreviewTransport(previewPlaying, { previewPlaying = !previewPlaying }, { previewRestart += 1 })
                            if(saved)Text(stringResource(R.string.video_saved_mark),color=MakerGold,style=MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { export() }, enabled = !busy && content!=null, modifier = Modifier.weight(1f).minimumInteractiveComponentSize().heightIn(min = 42.dp).defaultMinSize(minHeight = 40.dp),
                                    shape = GoldCornerFrameShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MakerGold),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(if(busy && !sharing) stringResource(R.string.video_preparing) else stringResource(R.string.video_save), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(onClick = { export(share=true) }, enabled = !busy && content!=null, modifier = Modifier.weight(1f).minimumInteractiveComponentSize().heightIn(min = 42.dp).defaultMinSize(minHeight = 40.dp),
                                    shape = GoldCornerFrameShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MakerGold),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text(if(busy && sharing) stringResource(R.string.video_preparing) else stringResource(R.string.video_share), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding(),
                    ) {
                        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MakerGold); Text(stringResource(R.string.video_please_wait), color = MakerGold) }
                        error?.let { Text(it, color = Color(0xFFB3261E)) }
                        if (step < 3) MakerButton(stringResource(listOf(R.string.video_next_background, R.string.video_next_style, R.string.video_next_share)[step]), !busy) { step++ }
                        else TextButton(onClick = { step-- }, enabled = !busy) { Text(stringResource(R.string.video_back_style), color = MakerGold) }
                    }
                }
            }
            if (reciters) ModalBottomSheet(onDismissRequest = { reciters = false }, containerColor = MakerPaper) {
                Text(stringResource(R.string.video_choose_reciter), modifier = Modifier.padding(20.dp), color = MakerGold, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(ui.videoReciters.ifEmpty { ui.reciters }, key = { it.id }) { reciter ->
                        ListItem(headlineContent = { Text(ReciterNames.display(uiLanguage, reciter.id, reciter.name)) },
                            supportingContent = if (QulRecitations.hasWordTimingCatalog(reciter.id)) {
                                { Text(stringResource(R.string.video_word_by_word), color = MakerGold, fontSize = 12.sp) }
                            } else null,
                            trailingContent = { if (reciter.id == ui.videoReciterId) Text("✓", color = MakerGold) },
                            colors = ListItemDefaults.colors(containerColor = MakerPaper),
                            modifier = Modifier.clickable { viewModel.selectVideoReciter(reciter.id); reciters = false })
                        HorizontalDivider(color = MakerGold.copy(alpha = .15f))
                    }
                }
            }
        }
    }
}

@Composable private fun videoSwatches(): List<Pair<String, Int>> {
    val labels = listOf(
        R.string.video_swatch_gold,
        R.string.video_swatch_mint,
        R.string.video_swatch_sky,
        R.string.video_swatch_orange,
        R.string.video_swatch_violet,
        R.string.video_swatch_green,
        R.string.video_swatch_red,
    )
    return ShareMushafLayout.HIGHLIGHT_COLOURS.mapIndexed { index, (_, color) ->
        stringResource(labels[index]) to color
    }
}

@Composable private fun PreviewTransport(playing: Boolean, onToggle: () -> Unit, onRestart: () -> Unit) {
    val MakerGold = LocalPaperColors.current.accent
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = stringResource(if (playing) R.string.audio_pause else R.string.audio_play),
                tint = MakerGold,
            )
        }
        IconButton(onClick = onRestart) {
            Icon(
                painterResource(R.drawable.ic_repeat),
                contentDescription = stringResource(R.string.video_preview_restart),
                tint = MakerGold,
            )
        }
    }
}

private data class PreviewClip(
    val ayah: Int,
    val url: String,
    val seekMs: Long,
    val stopMs: Long?,
    val segments: List<WordSegment>,
)

@Composable private fun MakerPreviewOrLoading(
    preview: Bitmap?,
    stamp: Int,
    options: VideoMakerOptions,
    geometry: MakerPreviewGeometry,
    step: Int,
    busy: Boolean,
    onChange: (VideoMakerOptions) -> Unit,
) {
    val MakerGold = LocalPaperColors.current.accent
    preview?.let { bmp -> MakerDragPreview(bmp, stamp, options, geometry, step, busy, onChange) }
        ?: Box(Modifier.fillMaxWidth().height(if (step == 3) 460.dp else 320.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MakerGold)
        }
}

/** Dedicated immediate vertical drag targets win over the surrounding scroll container. */
@Composable private fun MakerDragPreview(bitmap:Bitmap,stamp:Int,options:VideoMakerOptions,geometry:MakerPreviewGeometry,step:Int,busy:Boolean,onChange:(VideoMakerOptions)->Unit) {
    val MakerGold = LocalPaperColors.current.accent
    val previewHeight=when(step){2->340.dp;3->460.dp;else->300.dp}
    val heightPx=with(LocalDensity.current){previewHeight.toPx()}
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight*9f/16f)) {
            val frame = remember(bitmap, stamp) { bitmap.asImageBitmap() }
            Image(frame, stringResource(R.string.video_preview), contentScale=ContentScale.FillBounds, modifier=Modifier.matchParentSize())
            if(step in 2..3 && !busy) {
                val ayahDrag=rememberDraggableState { delta ->
                    val current=makerVerticalPlacement(options.ayahY,options.titleY,geometry.ayahHeight,geometry.titleHeight,options.showTitle,options.translation)
                    val maxY=(if(options.translation).80f else .86f)-geometry.ayahHeight/2f
                    val minY=if(options.showTitle)current.titleCenterY+makerBlockSeparation(geometry.ayahHeight,geometry.titleHeight) else .08f+geometry.ayahHeight/2f
                    onChange(options.copy(ayahY=(current.ayahCenterY+delta/heightPx).coerceIn(minY.coerceAtMost(maxY),maxY),titleY=current.titleCenterY))
                }
                val titleDrag=rememberDraggableState { delta ->
                    val current=makerVerticalPlacement(options.ayahY,options.titleY,geometry.ayahHeight,geometry.titleHeight,options.showTitle,options.translation)
                    val minY=.05f+geometry.titleHeight/2f
                    val maxY=(current.ayahCenterY-makerBlockSeparation(geometry.ayahHeight,geometry.titleHeight)).coerceAtLeast(minY)
                    onChange(options.copy(titleY=(current.titleCenterY+delta/heightPx).coerceIn(minY,maxY),ayahY=current.ayahCenterY))
                }
                val ayahBoxHeight=maxOf(52.dp,previewHeight*geometry.ayahHeight)
                val titleBoxHeight=maxOf(48.dp,previewHeight*geometry.titleHeight)
                val dragVerse = stringResource(R.string.video_drag_verse)
                val dragSurah = stringResource(R.string.video_drag_surah)
                Box(Modifier.align(Alignment.TopCenter).offset(y=previewHeight*geometry.ayahCenterY-ayahBoxHeight/2f).fillMaxWidth(.96f).height(ayahBoxHeight)
                    .border(1.dp,MakerGold.copy(alpha=.8f),GoldCornerFrameShape(5.dp))
                    .semantics{contentDescription=dragVerse}.draggable(ayahDrag,Orientation.Vertical,enabled=!busy,startDragImmediately=true)) {
                }
                if(options.showTitle)Box(Modifier.align(Alignment.TopCenter).offset(y=previewHeight*geometry.titleCenterY-titleBoxHeight/2f).fillMaxWidth(.88f).height(titleBoxHeight)
                    .border(1.dp,MakerGold.copy(alpha=.8f),GoldCornerFrameShape(5.dp))
                    .semantics{contentDescription=dragSurah}.draggable(titleDrag,Orientation.Vertical,enabled=!busy,startDragImmediately=true)) {
                }
            }
            if(busy)Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.55f)),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color=Color(0xFFD4B06A));Text(stringResource(R.string.video_preparing),color=Color.White,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MakerMushafLines(lines: List<ShareMushafLine>, fontManager: QcfFontManager) {
    val pages = remember(lines) { lines.map { it.pageNumber }.distinct() }
    val faces = remember { androidx.compose.runtime.mutableStateMapOf<Int, Typeface>() }
    LaunchedEffect(pages) {
        pages.forEach { page ->
            val face = fontManager.loadPageTypeface(page, night = false)
            if (face != Typeface.DEFAULT) faces[page] = face
        }
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tokens = remember(lines) { lines.flatMap { line -> line.words.map { line.pageNumber to it } } }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        val available = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val base = 18.sp * ShareMushafLayout.VIDEO_ARABIC_SIZE_FACTOR
        val markerBase = 15.sp * ShareMushafLayout.VIDEO_ARABIC_SIZE_FACTOR
        var widestWord = 0f
        tokens.forEach { (page, word) ->
            val family = faces[page]?.let { FontFamily(it) } ?: FontFamily.Serif
            val glyph = word.qcf.ifBlank { word.uthmanic }
            val shown = if (glyph.any { it.code in 0xE000..0xF8FF }) glyph.reversed() else glyph
            if (shown.isEmpty()) return@forEach
            val ink = measurer.measure(
                text = shown,
                style = TextStyle(
                    fontSize = if (word.isMarker) markerBase else base,
                    fontFamily = family,
                    textDirection = TextDirection.Content,
                ),
            ).size.width.toFloat()
            if (ink > widestWord) widestWord = ink
        }
        val scale = if (widestWord <= available) 1f else (available / widestWord * 0.92f).coerceIn(0.45f, 1f)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                tokens.forEach { (page, word) ->
                    val face = faces[page]
                    val glyph = word.qcf.ifBlank { word.uthmanic }
                    val shown = if (glyph.any { it.code in 0xE000..0xF8FF }) glyph.reversed() else glyph
                    if (shown.isEmpty()) return@forEach
                    Text(
                        text = shown,
                        color = Color(0xFF1A140F),
                        fontSize = (if (word.isMarker) markerBase else base) * scale,
                        fontFamily = if (face != null) FontFamily(face) else FontFamily.Serif,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(textDirection = TextDirection.Content),
                    )
                }
            }
        }
    }
}

@Composable private fun MakerPanel(content: @Composable ColumnScope.() -> Unit) {
    val paper = LocalPaperColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(paper.pageBody, GoldCornerFrameShape(10.dp))
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, GoldCornerFrameShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
@Composable private fun MakerButton(label: String, enabled: Boolean, click: () -> Unit) {
    val paper = LocalPaperColors.current
    Button(
        onClick = click,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().heightIn(min = 40.dp).defaultMinSize(minHeight = 40.dp),
        shape = GoldCornerFrameShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = paper.accent, contentColor = paper.pageBackground),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}
@Composable private fun MakerChoice(label: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    val paper = LocalPaperColors.current
    val shape = GoldCornerFrameShape(8.dp)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .defaultMinSize(minHeight = 40.dp)
            .background(if (selected) paper.accent else paper.pageBody, shape)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold.copy(alpha = if (selected) 1f else 0.55f), shape)
            .clickable(onClick = click)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) paper.pageBackground else paper.accent,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
@Composable private fun MakerToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked, change) }
}
@Composable private fun MakerNumber(label: String, value: Int, range: IntRange, modifier: Modifier, change: (Int) -> Unit) {
    val MakerGold = LocalPaperColors.current.accent
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = GoldCornerFrameShape(8.dp)) { Text("$label: $value", color = MakerGold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            range.forEach { n -> DropdownMenuItem(text = { Text(n.toString()) }, onClick = { change(n); open = false }) }
        }
    }
}
