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
    LaunchedEffect(content,ui.videoReciterId) {
        previewTimings=emptyList();previewClockMs=0L
        val verse=content?.verses?.firstOrNull()
        val id=when(ui.videoReciterId){"123"->18;"92"->26;else->null}
        if(verse!=null && id!=null) try {
            val words=QulWordTimings(context).load(content!!.surah,verse.ayah,id)
            val durationUs=((words.maxOfOrNull { it.endMs } ?: 1L)+200)*1000
            previewTimings=words.cursorSegments(durationUs)
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
        snapshotFlow { Triple(options,previewClockMs,previewTimings) }.conflate().collect { (settings,clock,timings) ->
            val segment=timings.firstOrNull { clock in it.startMs until it.endMs }
            val fraction=segment?.let { ((clock-it.startMs).toFloat()/(it.endMs-it.startMs).coerceAtLeast(1)).coerceIn(0f,1f) } ?: 0f
            try {
                var nextGeometry=geometry
                val image=exporter.makerPreview(data,settings,segment?.position,fraction,clock*1000,
                    (timings.lastOrNull()?.endMs ?: 1L)*1000,onGeometry={nextGeometry=it})
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
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("فيديو جديد", fontWeight = FontWeight.Bold, color = MakerGold)
                        TextButton(onClick = { if (!busy) onClose() }, enabled = !busy) { Text("إلغاء", color = MakerGold) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("آيات", "خلفية", "تنسيق", "مشاركة").forEachIndexed { index, name ->
                            TextButton(onClick = { step = index }, enabled = !busy) {
                                Text("${if (step == index) "●" else if (step > index) "✓" else "○"} $name", color = MakerGold,
                                    fontWeight = if (step == index) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (step == 0) {
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
                                    Text(ui.reciters.firstOrNull { it.id == ui.videoReciterId }?.let { ReciterNames.display("ar", it.id, it.name) } ?: "اختر القارئ", color = MakerGold)
                                }
                                Text("تتبّع الكلمات متاح للعفاسي وياسر الدوسري. بقية القراء: تمييز الآية فقط.", style = MaterialTheme.typography.bodySmall)
                            }
                            content?.verses?.forEach { verse ->
                                MakerPanel { Text(verse.uthmanicText); Text("الآية ${verse.ayah}", color = MakerGold) }
                            }
                        } else {
                            preview?.let { bmp ->
                                MakerDragPreview(bmp,options,geometry,step,busy){options=it}
                            } ?: Box(Modifier.fillMaxWidth().height(330.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=MakerGold)}
                            when (step) {
                                1 -> {
                                    Text("الخلفيات المحدّدة: ${options.backgrounds.size} • بالترتيب", color = MakerGold, fontWeight = FontWeight.Bold)
                                    Text("تتوزّع مدة الفيديو بالتساوي مع انتقال ناعم")
                                    ShareBackgrounds.FILES.take(20).chunked(3).forEach { row ->
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            row.forEach { name ->
                                                val image=remember(name) { context.assets.open(ShareBackgrounds.assetPath(name)).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=8 }) } }
                                                Box(Modifier.weight(1f).height(105.dp).border(if(name in options.backgrounds)3.dp else 1.dp,MakerGold,GoldCornerFrameShape(8.dp)).clickable {
                                                    val chosen=options.backgrounds.toMutableList()
                                                    if(name in chosen){if(chosen.size>1)chosen.remove(name)}else chosen.add(name)
                                                    options=options.copy(background=chosen.first(),backgrounds=chosen)
                                                }) {
                                                    image?.let { Image(it.asImageBitmap(),"خلفية $name",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }
                                                    if(name in options.backgrounds) Text("${options.backgrounds.indexOf(name)+1}",color=Color.White,modifier=Modifier.align(Alignment.TopEnd).background(MakerGold).padding(6.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    Text("اسحب الإطار الذهبي لتحريك الآية أو اسم السورة", color=MakerGold)
                                    Text("معاينة حركة الكلمات • الصوت عند تشغيل الفيديو",style=MaterialTheme.typography.bodySmall)
                                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        listOf("الخط", "الألوان", "المؤثرات", "العنوان", "النص", "رقم الآية", "الحركة", "تتبّع الكلمات").forEachIndexed { index, label ->
                                            FilterChip(selected = styleTab == index, onClick = { styleTab = index }, label = { Text(label) })
                                        }
                                    }
                                    MakerPanel {
                                        when(styleTab) {
                                            0 -> listOf("مصحف المدينة V4", "عثماني", "نسخ النظام").forEachIndexed { i, label ->
                                                Row(Modifier.fillMaxWidth().clickable { options = options.copy(font = i) }, verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = options.font == i, onClick = { options = options.copy(font = i) }); Text(label)
                                                }
                                            }
                                            1 -> {
                                                Text("لون الكلمة المتلوّة • النص أبيض دائماً")
                                                val colours=listOf("ذهبي" to 0xFFD4B06A.toInt(),"نعناعي" to 0xFF66D9B8.toInt(),"سماوي" to 0xFF91CAFF.toInt(),
                                                    "أصفر" to 0xFFF2CD62.toInt(),"برتقالي" to 0xFFF49C65.toInt(),"وردي" to 0xFFF090B8.toInt(),
                                                    "بنفسجي" to 0xFFC6A5ED.toInt(),"أخضر" to 0xFF8CCB7A.toInt(),"أحمر" to 0xFFE17872.toInt())
                                                colours.chunked(3).forEach { row ->
                                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                                        row.forEach { (name,value) ->
                                                            PaletteOption(label=name,start=Color(value),end=Color(value),active=options.highlightColor==value,
                                                                onClick={options=options.copy(highlightColor=value)},modifier=Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                            2 -> MakerToggle("توهّج هادئ", options.glow) { options = options.copy(glow = it) }
                                            3 -> MakerToggle("اسم السورة", options.showTitle) { options = options.copy(showTitle = it) }
                                            4 -> { Text("حجم النص"); Slider(value = options.textScale, onValueChange = { options = options.copy(textScale = it) }, valueRange = .7f..1.4f); MakerToggle("لوحة داكنة خلف النص", options.textBackdrop) { options = options.copy(textBackdrop = it) } }
                                            5 -> {
                                                MakerToggle("زخرفة عثمانية أصلية • رقم مدمج",options.marker){options=options.copy(marker=it)}
                                                val colours=listOf("ذهبي" to 0xFFD4B06A.toInt(),"أبيض" to 0xFFFFFFFF.toInt(),"أسود" to 0xFF000000.toInt(),"أحمر" to 0xFFE53935.toInt(),"أصفر" to 0xFFFFEB3B.toInt(),"أخضر" to 0xFF4CAF50.toInt())
                                                colours.chunked(3).forEach { row -> Row { row.forEach { (name,value) ->
                                                    FilterChip(selected=options.markerColor==value,onClick={options=options.copy(markerColor=value)},label={Text(name)})
                                                } } }
                                            }
                                            7 -> listOf("شريط تقدم ذهبي متّصل","لون تراكمي","خط أسفل الكلمة","الكلمة الحالية فقط","توهّج هادئ للكلمة").forEachIndexed { mode,label ->
                                                Row(Modifier.fillMaxWidth().clickable{options=options.copy(trackingMode=mode)},verticalAlignment=Alignment.CenterVertically){
                                                    RadioButton(selected=options.trackingMode==mode,onClick={options=options.copy(trackingMode=mode)});Text(label)
                                                }
                                            }
                                            6 -> { MakerToggle("تقريب هادئ", options.motion) { options = options.copy(motion = it) }; Text("تظهر الحركة في الفيديو بعد إنشائه") }
                                        }
                                    }
                                    MakerPanel { MakerToggle("English", options.translation) { options = options.copy(translation = it) } }
                                }
                                3 -> {
                                    Text("اسحب الإطار الذهبي لتحريك الآية أو اسم السورة",color=MakerGold,style=MaterialTheme.typography.bodySmall)

                                    MakerButton(if(busy && !sharing)"جارٍ تجهيز الفيديو…" else "حفظ على الجهاز",!busy && content!=null){export()}
                                    MakerButton(if(busy && sharing)"جارٍ تجهيز المشاركة…" else "مشاركة",!busy && content!=null){export(share=true)}
                                    if(saved)Text("✓ تم الحفظ في الاستوديو",color=MakerGold,style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = MakerGold); Text("جارٍ تجهيز الفيديو… يرجى الانتظار", color = MakerGold) }
                        error?.let { Text(it, color = Color(0xFFB3261E)) }
                    }
                    if (step < 3) MakerButton(listOf("التالي: الخلفية", "التالي: التنسيق", "التالي: المشاركة")[step], !busy) { step++ }
                    else TextButton(onClick = { step-- }, enabled = !busy) { Text("رجوع إلى التنسيق", color = MakerGold) }
                }
            }
            if (reciters) ModalBottomSheet(onDismissRequest = { reciters = false }, containerColor = MakerPaper) {
                Text("اختر القارئ", modifier = Modifier.padding(20.dp), color = MakerGold, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(ui.reciters, key = { it.id }) { reciter ->
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

/** Dedicated immediate vertical drag targets win over the surrounding scroll container. */
@Composable private fun MakerDragPreview(bitmap:Bitmap,options:VideoMakerOptions,geometry:MakerPreviewGeometry,step:Int,busy:Boolean,onChange:(VideoMakerOptions)->Unit) {
    val previewHeight=if(step==2)290.dp else 330.dp
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
