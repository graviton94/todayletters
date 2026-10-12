package io.github.graviton94.todayletters.ui

import io.github.graviton94.todayletters.data.recipientOf
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/*
 * 편지 도착 (v21 2단계 D1): 새 편지를 처음 열 때만.
 *   1. 어두운 화면에 그 편지의 그림이 천천히 밝아지며 다가온다 (새 편지 · 날짜 · 그림 이름). 누르면 다음.
 *   2. 그림이 가라앉고, 그 위에 첫 줄이 낭독에 맞춰 한 글자씩 쓰인다 (누르면 바로 끝까지).
 *   3. 다 쓰이면 우리말이 스며 나오고 ‘편지 읽기’.
 * 움직임 줄이기면 3만. 그림이 없는 편지는 2부터. 색은 테마와 상관없이 밤의 전시실.
 */
private val Night = Color(0xFF120E0A) // 뷰어와 같은 밤색 (D4)
private val Light = Color(0xFFF3EAD6)
private val Soft = Color(0xFFB9AFA0)
private val Faint = Color(0xFF8F8572)
private val Gilt = Color(0xFFC9B48C)

@Composable
fun LetterArrival(s: AppState, series: String, chapter: String, letter: Letter, n: Int, onBack: () -> Unit, onDone: (heardFirst: Boolean) -> Unit) {
    // 뒤로 가기 (U5): 편지 방으로 들어가지 않고 들어온 곳으로. 도착 장면은 다음에 열 때 다시
    FullDialog(onDismissRequest = onBack) {
        // 첫 줄에서 누른 낱말의 뜻 카드는 이 창 안에 (창 밖의 GlossHost 는 가려지므로)
        val layer = remember { s.glossLayers + 1 }
        DisposableEffect(Unit) { s.glossLayers = layer; onDispose { s.glossLayers = layer - 1; s.gloss = null } }
        Box(Modifier.fillMaxSize()) {
            ArrivalBody(s, series, chapter, letter, n, onDone, onBack = onBack)
            GlossHost(s, layer)
        }
    }
}

@Composable
internal fun ArrivalBody(s: AppState, series: String, chapter: String, letter: Letter, n: Int, onDone: (heardFirst: Boolean) -> Unit, startAt: Int? = null, onBack: () -> Unit = {}) {
    val work = s.work(series)
    val view = s.room(series)
    val still = s.reducedMotion
    val plate = rememberAsset(letter.plate?.image?.takeIf { it.isNotBlank() }?.let { "plates/$it" } ?: "")
    var stage by remember(letter.id) { mutableIntStateOf(startAt ?: if (still) 2 else if (plate == null) 1 else 0) }
    val first = letter.messages.firstOrNull()
    val line = first?.let { Breaks.plain(it.text[view.learn]) }.orEmpty()
    val audio = s.narrator.path(series, chapter, letter.id, "m1_${view.learn.code}")
    var shown by remember(letter.id) { mutableIntStateOf(if (stage == 2) line.length else 0) }
    var heard by remember(letter.id) { mutableFloatStateOf(if (stage == 2) 1f else 0f) }
    // 첫 줄 낭독을 끝까지 들었는가 (그러면 편지 방에서 첫 줄을 다시 읽지 않는다)
    var heardAll by remember(letter.id) { androidx.compose.runtime.mutableStateOf(false) }
    BackHandler(onBack = onBack)
    DisposableEffect(letter.id) { onDispose { s.narrator.stop() } }

    // 1. 그림이 밝아지며 다가온다
    val reveal = remember(letter.id) { Animatable(if (still || startAt != null) 1f else 0f) }
    LaunchedEffect(letter.id) { if (reveal.value < 1f) reveal.animateTo(1f, tween(Tokens.Motion.introHoldMs + Tokens.Motion.introFadeMs)) }
    // 2. 첫 줄이 낭독에 맞춰 쓰인다 (소리를 껐거나 낭독이 없으면 글자 빠르기로)
    LaunchedEffect(stage) {
        if (stage != 1) return@LaunchedEffect
        val voiced = s.app.sound && s.narrator.play(audio) { heardAll = true }
        val start = System.currentTimeMillis()
        val plain = (line.length * Tokens.Motion.typeCharMs * 2L).coerceAtLeast(1200L)
        // 목소리가 나오는 구간: 마디 시각의 처음(앞 쉼만큼 늦춤) ~ 끝(뒤 쉼 뺌). 없으면 파일 처음 ~ 끝
        val cs = s.narrator.chunks(audio)
        val from = cs.firstOrNull()?.let { it.start + io.github.graviton94.todayletters.core.Spoken.LEAD_MS } ?: 0
        val to = cs.lastOrNull()?.let { (it.end - 300).coerceAtLeast(from + 500) }
        while (shown < line.length) {
            androidx.compose.runtime.withFrameMillis { }
            val frac = if (voiced) {
                val d = s.narrator.duration(); val at = s.narrator.position()
                val end = to ?: d
                if (at < 0) 1f else if (end > from) (at - from).toFloat() / (end - from) else 0f
            } else (System.currentTimeMillis() - start).toFloat() / plain
            heard = frac.coerceIn(0f, 1f)
            shown = maxOf(shown, (line.length * heard).toInt())
        }
        stage = 2
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        val h = maxHeight
        if (stage == 0) {
            // 그림이 먼저
            Box(Modifier.fillMaxSize().pressable(haptic = false) { stage = 1 }) {
                if (plate != null) Image(plate, null, Modifier.fillMaxWidth().height(h * 0.74f).graphicsLayer {
                    alpha = reveal.value; val k = 1.08f - 0.08f * reveal.value; scaleX = k; scaleY = k
                }, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.4f))
                Box(Modifier.fillMaxWidth().height(h * 0.74f).background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Night)))
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Night.copy(alpha = 0.6f), Color.Transparent))))
                // 낮은 화면에서는 글이 넘치지 않게 굴려 본다 (평소에는 아래에 붙어 있다)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().heightIn(min = h).statusBarsSafe().navigationBarsSafe().padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
                        verticalArrangement = Arrangement.Bottom) {
                        Column(Modifier.graphicsLayer { alpha = reveal.value }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Caps("${deco(work.series.original, "LETTRE", "BRIEF")} ${roman(n)} · ${letter.place}", Gilt, small = true, decorative = true)
                            Text(stringResource(R.string.arrival_title, work.name[uiLang()]), style = Type.title.ui(), color = Light)
                            Text(stringResource(R.string.arrival_sub, dateLine(letter.date, letter.place), work.name[uiLang()], work.recipientOf(letter)[uiLang()]),
                                style = Type.small.ui(), color = Soft)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s8), verticalAlignment = Alignment.Bottom) {
                            PlateCredit(letter, Modifier.weight(1f))
                            Text(stringResource(R.string.arrival_tap), style = Type.small.ui(), color = Light)
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        // 2 · 3: 그림은 가라앉고 그 위에 첫 줄
        if (plate != null) Image(plate, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.34f }, contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -0.4f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night.copy(alpha = 0.3f), Night.copy(alpha = 0.85f)))))
        Column(Modifier.fillMaxSize().statusBarsSafe().navigationBarsSafe()
            .pressable(enabled = stage == 1, haptic = false) { shown = line.length; stage = 2 }) {
            Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s7, end = Tokens.Space.s7, top = Tokens.Space.s8), verticalAlignment = Alignment.CenterVertically) {
                Caps("${deco(work.series.original, "LETTRE", "BRIEF")} ${roman(n)}", Gilt, small = true, decorative = true, modifier = Modifier.weight(1f))
                Text(dateLine(letter.date, letter.place), style = Type.small.ui().copy(fontStyle = FontStyle.Italic), color = Soft)
            }
            Box(Modifier.padding(horizontal = Tokens.Space.s7, vertical = Tokens.Space.s3).fillMaxWidth().height(1.dp).background(Soft.copy(alpha = 0.25f)))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s7, vertical = Tokens.Space.s8),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
                // 문장 짝 (v22): 배우는 말 한 문장이 쓰이면 바로 아래에 그 번역이 스민다 (원문 한 덩이 · 번역 한 덩이가 아니라 짝마다)
                val big = Type.title.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic, fontSize = 29.sp, lineHeight = 38.sp).of(view.learn)
                val readLine = first?.takeIf { view.read != view.learn && view.showRead }?.text?.get(view.read)
                val pairs = remember(line, readLine) { io.github.graviton94.todayletters.core.Pairs.of(line, readLine, clauses = true) }
                pairs.forEach { (r, tr) ->
                    val seg = line.substring(r.first.coerceAtLeast(0), (r.last + 1).coerceAtMost(line.length))
                    val local = (shown - r.first).coerceIn(0, seg.length)
                    // 아직 안 쓴 글자도 자리는 차지한다 (줄이 쓰이는 동안 글이 움직이지 않게)
                    val typed = buildAnnotatedString {
                        append(seg.take(local))
                        val soft = seg.drop(local).take(2)
                        withStyle(SpanStyle(color = Light.copy(alpha = 0.35f))) { append(soft) }
                        withStyle(SpanStyle(color = Color.Transparent)) { append(seg.drop(local + soft.length)) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        // 다 쓰인 뒤에는 눌러서 뜻 보기
                        if (stage == 2 && first != null) GlossText(s, series, chapter, letter, 0, typed, style = big, color = Light, full = line, offset = r.first)
                        else Text(typed, style = big, color = Light)
                        val ready = stage == 2 || shown > r.last
                        val translation by animateFloatAsState(if (ready) 1f else 0f, tween(if (still) 0 else Tokens.Motion.introFadeMs), label = "tr")
                        if (tr.isNotBlank()) Text(tr, style = Type.body.of(view.read), color = Soft, modifier = Modifier.graphicsLayer { alpha = translation })
                    }
                }
            }
            if (stage == 1) Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s7, vertical = Tokens.Space.s7), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Box(Modifier.size(8.dp).background(Color(0xFFC25546), CircleShape))
                    Text(stringResource(R.string.arrival_voice, work.name[uiLang()]), style = Type.small.ui(), color = Soft)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Soft.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(heard).height(2.dp).background(Light))
                }
            } else Column(Modifier.fillMaxWidth().padding(Tokens.Space.s4), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                PlateCredit(letter, Modifier, center = true)
                Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(Light).pressable { onDone(heardAll) },
                    contentAlignment = Alignment.Center) { Text(stringResource(R.string.arrival_read), style = Type.body.ui(), color = Night) }
            }
        }
    }
}

/** 그림 이름 · 그린 해 · 소장처 (작게). */
@Composable
private fun PlateCredit(letter: Letter, modifier: Modifier, center: Boolean = false) {
    val pl = letter.plate ?: return
    val where = pl.collection.substringBefore(" · ").trim()
    val era = if (pl.era) stringResource(R.string.plate_era) + " · " else ""
    Text(era + listOf(pl.title[uiLang()], pl.date).filter { it.isNotBlank() }.joinToString(" · ") + if (where.isNotEmpty()) "\n$where" else "",
        style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Faint, modifier = modifier,
        textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else null)
}
