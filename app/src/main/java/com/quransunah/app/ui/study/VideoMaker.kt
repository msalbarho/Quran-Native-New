package com.quransunah.app.ui.study

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.catalog.ReciterNames
import com.quransunah.app.share.*
import com.quransunah.app.ui.settings.PaletteOption
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import com.quransunah.app.ui.shell.TiledOrnamentBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import java.io.File

private val MakerGold = Color(0xFF7A6030)
private val MakerPaper = Color(0xFFF8F3E8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMaker(viewModel: StudyViewModel, ui: StudyUiState, onClose: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(VideoMakerOptions()) }
    var reciters by remember { mutableStateOf(false) }
    var styleTab by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf<AyahShareContent?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
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
    LaunchedEffect(ui.videoFromAyah, ui.videoToAyah) {
        try { content = viewModel.makerContent() } catch (e: Exception) { error = e.message }
        saved = false
    }
    LaunchedEffect(options) { saved = false }
    var focusedBackground by remember { mutableStateOf(options.background) }
    var previewAyahSpans by remember { mutableStateOf(emptyList<Pair<LongRange, Int>>()) }
    LaunchedEffect(content,ui.videoReciterId) {
        previewTimings=emptyList();previewClockMs=0L;previewAyahSpans=emptyList()
        val data=content ?: return@LaunchedEffect
        if(!QulRecitations.hasWordTimingCatalog(ui.videoReciterId)) return@LaunchedEffect
        try {
            val store=QulWordTimings(context)
            val combined=ArrayList<WordSegment>()
            val spans=ArrayList<Pair<LongRange, Int>>()
            var offset=0L
            data.verses.forEach { verse ->
                val timing=store.loadForReciter(ui.videoReciterId,data.surah,verse.ayah)
                val duration=((timing.segments.maxOfOrNull { it.endMs } ?: 1L)+200)
                combined += timing.segments.cursorSegments(duration*1000).map {
                    it.copy(startMs=it.startMs+offset,endMs=it.endMs+offset)
                }
                spans += (offset until offset+duration) to verse.ayah
                offset += duration
            }
            previewTimings=combined
            previewAyahSpans=spans
        } catch(e:CancellationException){throw e} catch(_:Exception){ /* Offline still preview remains usable. */ }
    }
    LaunchedEffect(step,busy,previewTimings) {
        if(step !in 2..3 || busy || previewTimings.isEmpty())return@LaunchedEffect
        val duration=previewTimings.last().endMs.coerceAtLeast(1000)
        val start=android.os.SystemClock.uptimeMillis()
        while(true){previewClockMs=(android.os.SystemClock.uptimeMillis()-start)%duration;delay(66)}
    }
    LaunchedEffect(content) {
        val data = content ?: return@LaunchedEffect
        snapshotFlow { Triple(options,previewClockMs,previewTimings) to (focusedBackground to step) }.conflate().collect { (triple, focusStep) ->
            val (settings,clock,timings)=triple
            val (focused, currentStep)=focusStep
            val segment=timings.firstOrNull { clock in it.startMs until it.endMs }
            val fraction=segment?.let { ((clock-it.startMs).toFloat()/(it.endMs-it.startMs).coerceAtLeast(1)).coerceIn(0f,1f) } ?: 0f
            val ayah=previewAyahSpans.firstOrNull { clock in it.first }?.second ?: data.verses.first().ayah
            try {
                var nextGeometry=geometry
                val image=exporter.makerPreview(data,settings,segment?.position,fraction,clock*1000,
                    (timings.lastOrNull()?.endMs ?: 1L)*1000,onGeometry={nextGeometry=it},
                    previewBackground=if(currentStep==1) focused else null, activeAyah=ayah)
                geometry=nextGeometry;preview=image
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
                else android.widget.Toast.makeText(context,"تم حفظ الفيديو في الاستوديو",android.widget.Toast.LENGTH_SHORT).show()
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message ?: "تعذّر تجهيز الفيديو"}
            finally{busy=false}
        }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BackHandler { if (!busy) { if (step > 0) step-- else onClose() } }
            Box(Modifier.fillMaxSize().background(MakerPaper).statusBarsPadding().navigationBarsPadding()) {
                TiledOrnamentBackground(Modifier.matchParentSize().alpha(.12f))
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("فيديو جديد", fontWeight = FontWeight.Bold, color = MakerGold)
                        TextButton(onClick = { if (!busy) onClose() }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("إلغاء", color = MakerGold) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("آيات", "خلفية", "تنسيق", "مشاركة").forEachIndexed { index, name ->
                            TextButton(onClick = { step = index }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("${if (step == index) "●" else if (step > index) "✓" else "○"} $name", color = MakerGold,
                                    fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            }
                        }
                    }
                    when (step) {
                        0 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MakerPanel {
                                Text(ui.surahName, color = MakerGold, fontWeight = FontWeight.Bold)
                                Text("اختر بداية المقطع ونهايته • حتى ١٠ آيات")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MakerNumber("من الآية", ui.videoFromAyah, 1..SurahAyahCounts.ayahCount(ui.word?.surah ?: 1), Modifier.weight(1f), viewModel::setVideoFrom)
                                    MakerNumber("إلى الآية", ui.videoToAyah, ui.videoFromAyah..minOf(SurahAyahCounts.ayahCount(ui.word?.surah ?: 1), ui.videoFromAyah + 9), Modifier.weight(1f), viewModel::setVideoTo)
                                }
                            }
                            MakerPanel {
                                Text("القارئ", color = MakerGold)
                                OutlinedButton(onClick = { reciters = true }, modifier = Modifier.fillMaxWidth(), shape = GoldCornerFrameShape(10.dp)) {
                                    Text((ui.videoReciters.ifEmpty { ui.reciters }).firstOrNull { it.id == ui.videoReciterId }?.let { ReciterNames.display("ar", it.id, it.name) } ?: "اختر القارئ", color = MakerGold)
                                }
                            }
                            content?.verses?.forEach { verse ->
                                MakerPanel { Text(verse.uthmanicText); Text("الآية ${verse.ayah}", color = MakerGold) }
                            }
                        }
                        1 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPreviewOrLoading(preview, options, geometry, step, busy) { options = it }
                            Text("الخلفيات المحدّدة: ${options.backgrounds.size} • بالترتيب", color = MakerGold, fontWeight = FontWeight.Bold)
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
                                                image?.let { Image(it.asImageBitmap(),"خلفية $name",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }
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
                            MakerPreviewOrLoading(preview, options, geometry, step, busy) { options = it }
                            Text("اسحب الإطار الذهبي لتحريك الآية أو اسم السورة", color=MakerGold, fontSize = 12.sp)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("الخط", "الألوان", "المؤثرات", "العنوان", "النص", "ترجمة", "تتبّع الكلمات").forEachIndexed { index, label ->
                                    FilterChip(selected = styleTab == index, onClick = { styleTab = index }, label = { Text(label, fontSize = 12.sp) })
                                }
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPanel {
                                when(styleTab) {
                                    0 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("مصحف المدينة V4", "عثماني", "نسخ النظام").forEachIndexed { i, label ->
                                            FilterChip(selected = options.font == i, onClick = { options = options.copy(font = i) },
                                                label = { Text(label, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    1 -> {
                                        Text("لون الكلمة المتلوّة", fontSize = 13.sp)
                                        ShareMushafLayout.HIGHLIGHT_COLOURS.take(4).let { row ->
                                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                                row.forEach { (name,value) ->
                                                    PaletteOption(label=name,start=Color(value),end=Color(value),active=options.highlightColor==value,
                                                        onClick={options=options.copy(highlightColor=value)},modifier=Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        ShareMushafLayout.HIGHLIGHT_COLOURS.drop(4).let { row ->
                                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                                row.forEach { (name,value) ->
                                                    PaletteOption(label=name,start=Color(value),end=Color(value),active=options.highlightColor==value,
                                                        onClick={options=options.copy(highlightColor=value)},modifier=Modifier.weight(1f))
                                                }
                                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                    2 -> {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = options.glow, onClick = { options = options.copy(glow = !options.glow) },
                                                label = { Text("توهّج هادئ", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                                            FilterChip(selected = options.shadow, onClick = { options = options.copy(shadow = !options.shadow) },
                                                label = { Text("ظل النص", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = options.vignette, onClick = { options = options.copy(vignette = !options.vignette) },
                                                label = { Text("تظليل الحواف", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                                            FilterChip(selected = options.motion, onClick = { options = options.copy(motion = !options.motion) },
                                                label = { Text("تقريب الخلفية", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    3 -> {
                                        MakerToggle("اسم السورة", options.showTitle) { options = options.copy(showTitle = it) }
                                        if (options.showTitle) {
                                            Text("إطار الاسم", fontSize = 13.sp)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf("إطار المصحف", "مستطيل مزخرف", "ميدالية").forEachIndexed { i, label ->
                                                    FilterChip(selected = options.titleFrame == i, onClick = { options = options.copy(titleFrame = i) },
                                                        label = { Text(label, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                    4 -> {
                                        Text("حجم النص  ${ShareMushafLayout.scaleToFontSize(options.textScale).toInt()}")
                                        Slider(
                                            value = ShareMushafLayout.scaleToFontSize(options.textScale),
                                            onValueChange = { options = options.copy(textScale = ShareMushafLayout.fontSizeToScale(it)) },
                                            valueRange = ShareMushafLayout.FONT_SIZE_MIN..ShareMushafLayout.FONT_SIZE_MAX,
                                        )
                                        MakerToggle("لوحة داكنة خلف النص", options.textBackdrop) { options = options.copy(textBackdrop = it) }
                                        Text("لون النص", fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = !options.tajweedText, onClick = { options = options.copy(tajweedText = false) }, label = { Text("أبيض") })
                                            FilterChip(selected = options.tajweedText, onClick = { options = options.copy(tajweedText = true) }, label = { Text("تجويد") })
                                        }
                                        MakerToggle("رقم الآية", options.marker) { options = options.copy(marker = it) }
                                        if (options.marker) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                ShareMushafLayout.MARKER_COLOURS.forEach { (name, value) ->
                                                    FilterChip(selected = options.markerColor == value, onClick = { options = options.copy(markerColor = value) },
                                                        label = { Text(name) }, modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                    5 -> MakerToggle("الترجمة الإنجليزية", options.translation) { options = options.copy(translation = it) }
                                    6 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("شريط ذهبي", "تلوين تراكمي", "خط ممتد").forEachIndexed { mode, label ->
                                            FilterChip(selected = options.trackingMode == mode, onClick = { options = options.copy(trackingMode = mode) },
                                                label = { Text(label, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            }
                        }
                        else -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MakerPreviewOrLoading(preview, options, geometry, step, busy) { options = it }
                            if(saved)Text("✓ تم الحفظ في الاستوديو",color=MakerGold,style=MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { export() }, enabled = !busy && content!=null, modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                                    shape = GoldCornerFrameShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MakerGold)) {
                                    Text(if(busy && !sharing)"جارٍ التجهيز…" else "حفظ على الجهاز", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(onClick = { export(share=true) }, enabled = !busy && content!=null, modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                                    shape = GoldCornerFrameShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MakerGold)) {
                                    Text(if(busy && sharing)"جارٍ التجهيز…" else "مشاركة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MakerGold); Text("جارٍ تجهيز الفيديو… يرجى الانتظار", color = MakerGold) }
                    error?.let { Text(it, color = Color(0xFFB3261E)) }
                    if (step < 3) MakerButton(listOf("التالي: الخلفية", "التالي: التنسيق", "التالي: المشاركة")[step], !busy) { step++ }
                    else TextButton(onClick = { step-- }, enabled = !busy) { Text("رجوع إلى التنسيق", color = MakerGold) }
                }
            }
            if (reciters) ModalBottomSheet(onDismissRequest = { reciters = false }, containerColor = MakerPaper) {
                Text("اختر القارئ", modifier = Modifier.padding(20.dp), color = MakerGold, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(ui.videoReciters.ifEmpty { ui.reciters }, key = { it.id }) { reciter ->
                        ListItem(headlineContent = { Text(ReciterNames.display("ar", reciter.id, reciter.name)) },
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

@Composable private fun MakerPreviewOrLoading(
    preview: Bitmap?,
    options: VideoMakerOptions,
    geometry: MakerPreviewGeometry,
    step: Int,
    busy: Boolean,
    onChange: (VideoMakerOptions) -> Unit,
) {
    preview?.let { bmp -> MakerDragPreview(bmp, options, geometry, step, busy, onChange) }
        ?: Box(Modifier.fillMaxWidth().height(if (step == 3) 380.dp else 340.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MakerGold)
        }
}

/** Dedicated immediate vertical drag targets win over the surrounding scroll container. */
@Composable private fun MakerDragPreview(bitmap:Bitmap,options:VideoMakerOptions,geometry:MakerPreviewGeometry,step:Int,busy:Boolean,onChange:(VideoMakerOptions)->Unit) {
    val previewHeight=when(step){2->340.dp;3->380.dp;else->300.dp}
    val heightPx=with(LocalDensity.current){previewHeight.toPx()}
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight*9f/16f)) {
            Image(bitmap.asImageBitmap(),"معاينة تلقائية للفيديو",contentScale=ContentScale.FillBounds,modifier=Modifier.matchParentSize())
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
                Box(Modifier.align(Alignment.TopCenter).offset(y=previewHeight*geometry.ayahCenterY-ayahBoxHeight/2f).fillMaxWidth(.96f).height(ayahBoxHeight)
                    .border(1.dp,MakerGold.copy(alpha=.8f),GoldCornerFrameShape(5.dp))
                    .semantics{contentDescription="سحب الآية"}.draggable(ayahDrag,Orientation.Vertical,enabled=!busy,startDragImmediately=true)) {
                }
                if(options.showTitle)Box(Modifier.align(Alignment.TopCenter).offset(y=previewHeight*geometry.titleCenterY-titleBoxHeight/2f).fillMaxWidth(.88f).height(titleBoxHeight)
                    .border(1.dp,MakerGold.copy(alpha=.8f),GoldCornerFrameShape(5.dp))
                    .semantics{contentDescription="سحب اسم السورة"}.draggable(titleDrag,Orientation.Vertical,enabled=!busy,startDragImmediately=true)) {
                }
            }
            if(busy)Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.55f)),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color=Color(0xFFD4B06A));Text("جارٍ تجهيز الفيديو…",color=Color.White,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun MakerPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFFCFAF5), GoldCornerFrameShape(10.dp))
        .border(1.dp, MakerGold.copy(alpha = .35f), GoldCornerFrameShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
@Composable private fun MakerButton(label: String, enabled: Boolean, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = GoldCornerFrameShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MakerGold)) { Text(label, fontWeight = FontWeight.Bold) }
}
@Composable private fun MakerToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked, change) }
}
@Composable private fun MakerNumber(label: String, value: Int, range: IntRange, modifier: Modifier, change: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = GoldCornerFrameShape(8.dp)) { Text("$label: $value", color = MakerGold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            range.forEach { n -> DropdownMenuItem(text = { Text(n.toString()) }, onClick = { change(n); open = false }) }
        }
    }
}
