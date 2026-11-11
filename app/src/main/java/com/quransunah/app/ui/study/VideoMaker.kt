package com.quransunah.app.ui.study

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.ViewTreeObserver
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.defaultMinSize
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.shell.ChromeTokens
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import com.quransunah.app.fonts.QcfFontManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quransunah.app.R
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.LanguageRegistry
import com.quransunah.app.core.EasternArabic
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
    // ONLY the share-video preview MediaPlayer: pause when the maker leaves the
    // foreground. Do not auto-resume — user taps play. Does not touch the main
    // recitation AudioPlayer service / notification / Android Auto.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                previewPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewPlaying = false
        }
    }
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
        // USAGE_MEDIA is treated as now-playing on Android 13+ / Samsung One UI
        // and can surface this app's MediaSessionService ("Holy Quran • Recitation").
        // USAGE_GAME keeps preview audio local: no media notification, no lock-screen
        // controls, and no MediaSession / PlaybackService / Android Auto involvement.
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
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
            val contentInsets = rememberDialogSafeInsets()
            val density = LocalDensity.current
            val statusTopPx = WindowInsets.statusBars.getTop(density)
            val insetTopPx = contentInsets.getTop(density)
            val extraTop = with(density) { ((insetTopPx - statusTopPx).coerceAtLeast(0) / 2).toDp() }
            BackHandler { if (!busy) { if (step > 0) step-- else onClose() } }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MakerPaper),
            ) {
                TiledOrnamentBackground(Modifier.matchParentSize().alpha(0.14f))
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .windowInsetsPadding(
                            WindowInsets.statusBars.union(contentInsets.only(WindowInsetsSides.Horizontal)),
                        )
                        .padding(horizontal = 16.dp)
                        .padding(top = extraTop + 2.dp),
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
                            TextButton(
                                onClick = { step = index },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    "${if (step == index) "●" else if (step > index) "✓" else "○"} ${stringResource(name)}",
                                    color = MakerGold,
                                    fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
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
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
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
                            MakerPreviewOrLoading(preview, previewStamp, options, geometry, step, busy, Modifier.fillMaxWidth().weight(1f)) { options = it }
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
                            MakerPreviewOrLoading(preview, previewStamp, options, geometry, step, busy, Modifier.fillMaxWidth().weight(1f)) { options = it }
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
                            MakerPreviewOrLoading(preview, previewStamp, options, geometry, step, busy, Modifier.fillMaxWidth().weight(1f)) { options = it }
                            PreviewTransport(previewPlaying, { previewPlaying = !previewPlaying }, { previewRestart += 1 })
                            if(saved)Text(stringResource(R.string.video_saved_mark),color=MakerGold,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(contentInsets.only(WindowInsetsSides.Bottom))
                            .navigationBarsPadding()
                            .padding(bottom = 15.dp),
                    ) {
                        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MakerGold); Text(stringResource(R.string.video_please_wait), color = MakerGold) }
                        error?.let { Text(it, color = Color(0xFFB3261E)) }
                        if (step < 3) {
                            MakerButton(stringResource(listOf(R.string.video_next_background, R.string.video_next_style, R.string.video_next_share)[step]), !busy) { step++ }
                        } else {
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
                            TextButton(onClick = { step-- }, enabled = !busy) { Text(stringResource(R.string.video_back_style), color = MakerGold) }
                        }
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
    modifier: Modifier = Modifier,
    onChange: (VideoMakerOptions) -> Unit,
) {
    val MakerGold = LocalPaperColors.current.accent
    Box(modifier, contentAlignment = Alignment.Center) {
        preview?.let { bmp -> MakerDragPreview(bmp, stamp, options, geometry, step, busy, onChange, Modifier.fillMaxSize()) }
            ?: CircularProgressIndicator(color = MakerGold)
    }
}

/** Dedicated immediate vertical drag targets win over the surrounding scroll container. */
@Composable private fun MakerDragPreview(bitmap:Bitmap,stamp:Int,options:VideoMakerOptions,geometry:MakerPreviewGeometry,step:Int,busy:Boolean,onChange:(VideoMakerOptions)->Unit,modifier:Modifier=Modifier) {
    val MakerGold = LocalPaperColors.current.accent
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val previewHeight = minOf(maxHeight, maxWidth * 16f / 9f)
        val previewWidth = previewHeight * 9f / 16f
        val heightPx=with(LocalDensity.current){previewHeight.toPx()}
        Box(Modifier.height(previewHeight).width(previewWidth)) {
            val frame = remember(bitmap, stamp) { bitmap.asImageBitmap() }
            Image(frame, stringResource(R.string.video_preview), contentScale=ContentScale.FillBounds, modifier=Modifier.matchParentSize())
            if(step in 2..3 && !busy) {
                val ayahDrag=rememberDraggableState { delta ->
                    val current=makerVerticalPlacement(options.ayahY,options.titleY,geometry.ayahHeight,geometry.titleHeight,options.showTitle,options.translation)
                    val maxY=(if(options.translation).88f else .90f)-geometry.ayahHeight/2f
                    val minY=if(options.showTitle)current.titleCenterY+makerBlockSeparation(geometry.ayahHeight,geometry.titleHeight) else .08f+geometry.ayahHeight/2f
                    onChange(options.copy(ayahY=(current.ayahCenterY+delta/heightPx).coerceIn(minY.coerceAtMost(maxY),maxY),titleY=current.titleCenterY))
                }
                val titleDrag=rememberDraggableState { delta ->
                    val current=makerVerticalPlacement(options.ayahY,options.titleY,geometry.ayahHeight,geometry.titleHeight,options.showTitle,options.translation)
                    val minY=.04f+geometry.titleHeight/2f
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
            val face = faces[page]
            val useQcf = face != null && word.qcf.isNotBlank()
            // QCF page glyphs are visual-order (PUA / Arabic Presentation Forms). Draw LTR so
            // Compose bidi does not reshuffle them into scrambled shapes; never paint those
            // codes with Serif while the page face is still loading.
            val shown = if (useQcf) word.qcf else word.uthmanic
            if (shown.isEmpty()) return@forEach
            val family = if (useQcf) FontFamily(face!!) else FontFamily.SansSerif
            val ink = measurer.measure(
                text = shown,
                style = TextStyle(
                    fontSize = if (word.isMarker) markerBase else base,
                    fontFamily = family,
                    textDirection = if (useQcf) TextDirection.Ltr else TextDirection.Content,
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
                    val useQcf = face != null && word.qcf.isNotBlank()
                    val shown = if (useQcf) word.qcf else word.uthmanic
                    if (shown.isEmpty()) return@forEach
                    Text(
                        text = shown,
                        color = Color(0xFF1A140F),
                        fontSize = (if (word.isMarker) markerBase else base) * scale,
                        fontFamily = if (useQcf) FontFamily(face!!) else FontFamily.SansSerif,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(textDirection = if (useQcf) TextDirection.Ltr else TextDirection.Content),
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
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
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) paper.pageBackground else paper.accent,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = TextStyle(
                textAlign = TextAlign.Center,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
@Composable private fun MakerToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked, change) }
}
@Composable private fun MakerNumber(label: String, value: Int, range: IntRange, modifier: Modifier, change: (Int) -> Unit) {
    val MakerGold = LocalPaperColors.current.accent
    var open by remember { mutableStateOf(false) }
    val labelStyle = TextStyle(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MakerGold.copy(alpha = 0.78f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp),
            style = labelStyle,
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .border(1.dp, MakerGold.copy(alpha = 0.85f), GoldCornerFrameShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = EasternArabic.format(value),
                    color = MakerGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = labelStyle,
                )
                Text(
                    text = " ▾",
                    color = MakerGold.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    style = labelStyle,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                range.forEach { n ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                EasternArabic.format(n),
                                fontWeight = if (n == value) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = { change(n); open = false },
                    )
                }
            }
        }
    }
}


private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun readHostSystemInsets(context: Context, fallbackView: View): WindowInsets {
    val typesCompat = WindowInsetsCompat.Type.systemBars() or
        WindowInsetsCompat.Type.displayCutout() or
        WindowInsetsCompat.Type.mandatorySystemGestures()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val wm = context.getSystemService(WindowManager::class.java)
            ?: context.findActivity()?.windowManager
        val metrics = wm?.currentWindowMetrics?.windowInsets
        if (metrics != null) {
            val types = android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout() or
                android.view.WindowInsets.Type.mandatorySystemGestures()
            val sys = metrics.getInsets(types)
            if (sys.bottom > 0 || sys.top > 0) {
                return WindowInsets(sys.left, sys.top, sys.right, sys.bottom)
            }
        }
    }
    val root = context.findActivity()?.window?.decorView ?: fallbackView.rootView
    val sys = ViewCompat.getRootWindowInsets(root)?.getInsets(typesCompat)
    return if (sys != null) {
        WindowInsets(sys.left, sys.top, sys.right, sys.bottom)
    } else {
        WindowInsets(0, 0, 0, 0)
    }
}

/**
 * Dialog windows often expose empty navigation-bar WindowInsets.
 * Union Compose safeDrawing with the host window's WindowMetrics so the
 * same padding works for any screen size, density, and navigation mode.
 */
@Composable
private fun rememberDialogSafeInsets(): WindowInsets {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var host by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    DisposableEffect(context, view, configuration) {
        val root = context.findActivity()?.window?.decorView ?: view.rootView
        fun read() {
            host = readHostSystemInsets(context, view)
        }
        read()
        val listener = ViewTreeObserver.OnGlobalLayoutListener { read() }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { root.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return WindowInsets.safeDrawing.union(host)
}
