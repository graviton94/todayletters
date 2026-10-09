package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Spot
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 전시실의 어둠: 테마와 상관없이 그림 산책은 늘 어두운 방에서. */
private val Room = Color(0xFF120D09)
private val Wall = Color(0xFFEADFC8)
private val WallSoft = Color(0xFFA8977C)
private val Gilt = Color(0xFFE6BF68)

/**
 * 그림 속 산책: 편지에 나온 그림을 확대해 걸어 다닌다.
 * 빛나는 자리(Spot)를 누르면 그곳으로 다가가고, 아래 쪽지에 큐레이터 설명 · 그 낱말 · 편지 속 문장이 나온다.
 * 두 손가락으로 확대, 두 번 눌러 전체 보기.
 */
@Composable
fun Artwork(s: AppState, r: Route.Artwork) {
    val room = r.from as? Route.Letter
    val pair = room?.let { s.letterOf(it) }
    val letter = pair?.second
    val pl = letter?.plate
    val work = s.work(r.series)
    val view = s.room(r.series)
    val ui = uiLang()
    val ctx = LocalContext.current
    val bmp: ImageBitmap? = remember(pl?.image) {
        pl?.image?.takeIf { it.isNotBlank() }?.let { f -> runCatching { ctx.assets.open("plates/$f").use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    }
    val spots = pl?.spots.orEmpty()
    var at by remember { mutableStateOf(-1) }
    var playing by remember { mutableStateOf<String?>(null) }
    val scale = remember { Animatable(1f) }
    val tx = remember { Animatable(0f) }
    val ty = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    androidx.activity.compose.BackHandler(enabled = at >= 0) { at = -1 }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { s.narrator.stop() } }

    fun play(asset: String) {
        if (playing == asset) { s.narrator.stop(); playing = null; return }
        s.narrator.stop()
        if (s.narrator.play(asset) { playing = null }) playing = asset
    }

    // 자리로 다가가기: 그림 크기를 아는 BoxWithConstraints 안에서 정해지고, 아래 쪽지 단추도 같이 쓴다
    val focusRef = remember { arrayOf<(Int) -> Unit>({}) }
    val focus: (Int) -> Unit = { focusRef[0](it) }
    Box(Modifier.fillMaxSize().background(Room)) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp, bottom = 250.dp).clipToBounds()) {
            val density = LocalDensity.current
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            val aspect = bmp?.let { it.width.toFloat() / it.height } ?: 1.25f
            val w = minOf(maxW, maxH * aspect)
            val h = w / aspect

            fun clampX(v: Float, z: Float) = v.coerceIn(-(w * z - w) / 2 - 0f, (w * z - w) / 2 + 0f)
            fun clampY(v: Float, z: Float) = v.coerceIn(-((h * z - maxH) / 2).coerceAtLeast(0f) - h * 0.15f, ((h * z - maxH) / 2).coerceAtLeast(0f) + h * 0.15f)
            focusRef[0] = { i ->
                at = i
                val z = if (i < 0) 1f else 2.3f
                val sp = spots.getOrNull(i)
                val gx = if (sp == null) 0f else -(sp.x - 0.5f) * w * z
                val gy = if (sp == null) 0f else -(sp.y - 0.5f) * h * z - maxH * 0.12f
                scope.launch { scale.animateTo(z, tween(700, easing = FastOutSlowInEasing)) }
                scope.launch { tx.animateTo(if (sp == null) 0f else clampX(gx, z), tween(700, easing = FastOutSlowInEasing)) }
                scope.launch { ty.animateTo(if (sp == null) 0f else clampY(gy, z), tween(700, easing = FastOutSlowInEasing)) }
            }

            Box(
                Modifier.fillMaxSize()
                    .pointerInput(w, h) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scope.launch {
                                val z = (scale.value * zoom).coerceIn(1f, 4f)
                                scale.snapTo(z)
                                tx.snapTo(clampX(tx.value + pan.x, z))
                                ty.snapTo(clampY(ty.value + pan.y, z))
                            }
                        }
                    }
                    .pointerInput(w, h) { detectTapGestures(onDoubleTap = { focus(-1) }) },
                contentAlignment = Alignment.Center,
            ) {
                val wd = with(density) { w.toDp() }
                val hd = with(density) { h.toDp() }
                Box(
                    Modifier.size(wd, hd).graphicsLayer {
                        scaleX = scale.value; scaleY = scale.value
                        translationX = tx.value; translationY = ty.value
                    },
                ) {
                    if (bmp != null) Image(bmp, pl?.title?.get(ui), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Box(Modifier.fillMaxSize().border(1.dp, WallSoft.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.plate_soon), style = Type.small.ui(), color = WallSoft)
                    }
                    spots.forEachIndexed { i, sp ->
                        val ring = 44.dp
                        val half = with(density) { (ring / 2).roundToPx() }
                        Box(
                            Modifier
                                .offset { IntOffset((sp.x * w).roundToInt() - half, (sp.y * h).roundToInt() - half) }
                                .size(ring)
                                .graphicsLayer { scaleX = 1f / scale.value; scaleY = 1f / scale.value }
                                .semantics { contentDescription = sp.title[ui] }
                                .pressable { focus(i) },
                            contentAlignment = Alignment.Center,
                        ) { SpotRing(on = i == at, still = s.reducedMotion, number = i + 1) }
                    }
                }
            }
        }

        // 위: 닫기 · 산책 n / N
        Row(
            Modifier.fillMaxWidth().background(Room).statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = Tokens.Space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (at >= 0) Row(
                Modifier.padding(start = Tokens.Space.s2, end = Tokens.Space.s3).heightIn(min = 36.dp).border(1.dp, Gilt.copy(alpha = 0.55f))
                    .pressable { focus(-1) }.padding(start = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(Modifier.size(14.dp)) { drawLine(Gilt, Offset(size.width * 0.7f, 0f), Offset(size.width * 0.25f, size.height / 2), 1.6.dp.toPx()); drawLine(Gilt, Offset(size.width * 0.25f, size.height / 2), Offset(size.width * 0.7f, size.height), 1.6.dp.toPx()) }
                Text(stringResource(R.string.walk_back_whole), style = Type.small.ui(), color = Gilt)
            } else IconButton(stringResource(R.string.close), onClick = { s.back() }) {
                Canvas(Modifier.size(18.dp)) {
                    val st = Stroke(1.6.dp.toPx())
                    drawLine(Wall, Offset.Zero, Offset(size.width, size.height), st.width)
                    drawLine(Wall, Offset(size.width, 0f), Offset(0f, size.height), st.width)
                }
            }
            Text(stringResource(R.string.walk_title), style = Type.body.ui(), color = if (at >= 0) WallSoft else Wall, modifier = Modifier.weight(1f))
            if (spots.isNotEmpty()) Text(
                if (at >= 0) "${at + 1} / ${spots.size}" else "· ${spots.size} ·",
                style = Type.caps, color = Gilt, modifier = Modifier.padding(end = Tokens.Space.s3),
            )
        }

        // 아래: 쪽지
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(Tokens.Space.s3)) {
            val sp = spots.getOrNull(at)
            if (sp == null) {
                Slip(seed = 41, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        pl?.let {
                            Text(it.date.uppercase() + " · " + it.collection, style = Type.caps, color = Color(0xFF7A5A20))
                            Text(it.title[work.series.original], style = Type.title.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic), color = Ink.palette.slipInk)
                            if (ui != work.series.original) Text(it.title[ui], style = Type.small.ui(), color = Ink.palette.slipSoft)
                        }
                        letter?.note?.let { note ->
                            var more by remember { mutableStateOf(false) }
                            var long by remember { mutableStateOf(false) }
                            Column(if (more) Modifier.heightIn(max = 300.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier) {
                                Text(
                                    note[ui], style = Type.small.of(ui), color = Ink.palette.slipInk,
                                    maxLines = if (more) Int.MAX_VALUE else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    onTextLayout = { if (!more) long = it.hasVisualOverflow },
                                )
                            }
                            if (long || more) Text(
                                stringResource(if (more) R.string.walk_less else R.string.walk_more), style = Type.small.ui(), color = Color(0xFF7A5A20),
                                modifier = Modifier.pressable { more = !more }.padding(vertical = 4.dp),
                            )
                        }
                        if (spots.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                            Text(stringResource(R.string.walk_hint), style = Type.small.ui(), color = Ink.palette.slipSoft, modifier = Modifier.weight(1f))
                            Box(Modifier.heightIn(min = 44.dp).background(Ink.palette.slipInk).pressable { focus(0) }.padding(horizontal = Tokens.Space.s4), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.walk_start), style = Type.small.ui(), color = Ink.palette.slip)
                            }
                        }
                    }
                }
            } else {
                SpotCard(
                    s, sp, at, spots.size, work.series.original, view.learn, view.read,
                    wordAsset = pair?.let { (ch, l) -> s.narrator.path(r.series, ch, l.id, "s${at + 1}_${view.learn.code}") },
                    quoteAsset = pair?.let { (ch, l) -> sp.quote?.let { q -> s.narrator.path(r.series, ch, l.id, "m${q + 1}_${view.learn.code}") } },
                    quote = sp.quote?.let { letter?.messages?.getOrNull(it) }?.text,
                    playing = playing, onPlay = ::play,
                    onPrev = { focus(at - 1) }, onNext = { focus(if (at + 1 < spots.size) at + 1 else -1) },
                )
            }
        }
    }
}

/** 빛나는 자리: 금빛 고리가 천천히 숨 쉬고, 고른 자리는 채워진다. */
@Composable
private fun SpotRing(on: Boolean, still: Boolean, number: Int) {
    val pulse = if (still) 0.5f else rememberInfiniteTransition(label = "spot").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "pulse",
    ).value
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val r0 = size.minDimension / 2
            if (!on) drawCircle(Gilt.copy(alpha = (1f - pulse) * 0.7f), r0 * (0.45f + 0.55f * pulse), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.35f), r0 * 0.42f)
            drawCircle(if (on) Gilt else Color.Transparent, r0 * 0.38f)
            drawCircle(Gilt, r0 * 0.38f, style = Stroke(1.5.dp.toPx()))
        }
        Text("$number", style = Type.caps.copy(fontSize = Tokens.Text.caps), color = if (on) Room else Gilt)
    }
}

@Composable
private fun SpotCard(
    s: AppState, sp: Spot, at: Int, total: Int,
    original: io.github.graviton94.todayletters.core.Lang,
    learn: io.github.graviton94.todayletters.core.Lang, read: io.github.graviton94.todayletters.core.Lang,
    wordAsset: String?, quoteAsset: String?, quote: io.github.graviton94.todayletters.core.Tri?,
    playing: String?, onPlay: (String) -> Unit, onPrev: () -> Unit, onNext: () -> Unit,
) {
    val p = Ink.palette
    val ui = uiLang()
    Slip(seed = 60 + at, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sp.title[ui], style = Type.heading.ui(), color = p.slipInk)
                    if (ui != original) Text(sp.title[original], style = Type.small.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic), color = p.slipSoft)
                }
            }
            Text(sp.note[ui], style = Type.small.of(ui), color = p.slipInk)
            sp.word?.let { wd ->
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(p.slipInk.copy(alpha = 0.12f)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.walk_word), style = Type.caps.copy(fontSize = Tokens.Text.caps), color = Color(0xFF7A5A20))
                        Text(wd.text[learn], style = Type.target.of(learn), color = p.slipInk)
                        Text(listOfNotNull(wd.ipa.takeIf { it.isNotEmpty() && learn == original }?.let { "[$it]" }, wd.text[read]).joinToString("  "), style = Type.small.of(read), color = p.slipSoft)
                    }
                    if (wordAsset != null && s.narrator.has(wordAsset)) SpeakerButton(playing == wordAsset, stringResource(R.string.listen_line)) { onPlay(wordAsset) }
                }
            }
            if (quote != null) {
                Column(
                    Modifier.fillMaxWidth().border(1.dp, p.slipInk.copy(alpha = 0.18f)).padding(Tokens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(stringResource(R.string.walk_from_letter), style = Type.caps.copy(fontSize = Tokens.Text.caps), color = Color(0xFF7A5A20))
                    val line = "“${quote[learn]}”"
                    val marked = androidx.compose.ui.text.buildAnnotatedString {
                        append(line)
                        sp.word?.let { wd -> wordMarks(line, listOf(wd.text[learn])).forEach { (range, _) -> addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color(0xFF7A5A20)), range.first, range.last + 1) } }
                    }
                    Text(marked, style = Type.base.of(learn).copy(fontStyle = FontStyle.Italic), color = p.slipInk, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (read != learn) Text(quote[read], style = Type.small.of(read), color = p.slipSoft, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, p.slipInk.copy(alpha = 0.5f)).pressable(enabled = at > 0) { onPrev() }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.walk_prev), style = Type.small.ui(), color = p.slipInk.copy(alpha = if (at > 0) 1f else 0.35f))
                }
                Box(Modifier.weight(1f).heightIn(min = 44.dp).background(p.slipInk).pressable { onNext() }, contentAlignment = Alignment.Center) {
                    Text(stringResource(if (at + 1 < total) R.string.walk_next else R.string.walk_whole), style = Type.small.ui(), color = p.slip)
                }
            }
        }
    }
}
