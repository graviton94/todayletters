package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Next
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Wall
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import io.github.graviton94.todayletters.design.uiHangul
import kotlinx.coroutines.flow.first

/*
 * 보상 · 새 편지 (시안 rw2, 사용자 허락 2026-10-24): 미술관의 벽 라벨과 조명. 효과는 빛 · 글자 · 느린 움직임만
 * (반짝이 · 튀는 도형 · 깜빡임 · 되풀이 없음). 움직임은 0.6~1.8초 한 번씩, 그림이 다가오는 것만 9초. 움직임 줄이기면 처음부터 끝 모습.
 * 진동은 편지 완료 · 메달 · 레벨 업에서 한 번씩.
 */

/** 전시실 벽 (보상 화면은 테마와 상관없이 밤의 전시실, 시안 그대로). */
object Museum {
    val wall = Color(0xFF121010)
    val wall2 = Color(0xFF1A1714)
    val ink = Color(0xFFEEE7DA)
    val soft = Color(0xFFA0978A)
    val faint = Color(0xFF5E574D)
    val gilt = Color(0xFFC9A866)
    val line = Color(0x1FEEE7DA)
}

/** 시안의 느린 끝 (cubic-bezier .22 .7 .18 1). */
val MuseumEase = CubicBezierEasing(0.22f, 0.7f, 0.18f, 1f)

/**
 * 한 화면의 시계 (밀리초, 처음 그릴 때부터): 차례로 뜨는 것들이 이 하나로 [win] 을 셈한다. [until] 이 지나면 멈춘다.
 * 움직임 줄이기면 처음부터 [until] (끝 모습).
 */
@Composable
fun rememberClock(key: Any, still: Boolean, until: Long = 10_000, start: Boolean = true): androidx.compose.runtime.State<Long> {
    val t = remember(key) { mutableLongStateOf(if (still) until else 0L) }
    LaunchedEffect(key, start, still) {
        if (still || !start) return@LaunchedEffect
        var first = -1L
        while (t.longValue < until) {
            val now = androidx.compose.runtime.withFrameMillis { it }
            if (first < 0) first = now - t.longValue
            t.longValue = (now - first).coerceAtMost(until)
        }
    }
    return t
}

/** [ms] 시각에서 [delay] 부터 [dur] 동안의 0..1 (느린 끝). */
fun win(ms: Long, delay: Int, dur: Int): Float = MuseumEase.transform(((ms - delay).toFloat() / dur).coerceIn(0f, 1f))

/** 떠오르기 (시안 rise): 아래 10dp · 흐림 3에서 제자리로. [k] 0..1. */
fun Modifier.rise(k: Float, blur: Float = 3f, dy: Float = 10f): Modifier = graphicsLayer {
    alpha = k
    translationY = (1f - k) * dy * density
    val r = (1f - k) * blur * density
    renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(r, r) else null
}

/** 흐린 데서 또렷해지기 · 흐려지며 사라지기 ([k] 1 이면 또렷). */
fun Modifier.focus(k: Float, blur: Float = 8f, dy: Float = 0f): Modifier = graphicsLayer {
    alpha = k
    translationY = (1f - k) * dy * density
    val r = (1f - k) * blur * density
    renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(r, r) else null
}

/**
 * 벽 라벨 머리말 (시안 .eb): Cormorant 대문자 · 넓은 자간. 한글 토막 ("마침") 은 한글 글꼴로 세워서 (Cormorant 에 한글이 없다).
 */
@Composable
fun Eyebrow(text: String, color: Color, modifier: Modifier = Modifier, size: Float = 12f) {
    val latin = TextStyle(fontFamily = Faces.display, fontWeight = FontWeight.SemiBold, fontSize = size.sp, letterSpacing = 0.22.em)
    val t = buildAnnotatedString {
        var i = 0
        val up = text.uppercase()
        while (i < up.length) {
            val h = up[i] in '가'..'힣'
            var j = i
            while (j < up.length && (up[j] in '가'..'힣') == h) j++
            if (h) withStyle(SpanStyle(fontFamily = Faces.hangul, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em, fontSize = (size - 0.5f).sp)) { append(up.substring(i, j)) }
            else append(up.substring(i, j))
            i = j
        }
    }
    Text(t, style = latin, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** 화살표 (가는 선 · 꺾쇠): 글꼴에 없는 글자 대신 그린다. */
@Composable
fun ArrowGlyph(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(18.dp, 10.dp)) {
    val st = 1.2.dp.toPx(); val y = size.height / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), st, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width - 4.5.dp.toPx(), y - 4.dp.toPx()), Offset(size.width, y), st, cap = StrokeCap.Round)
    drawLine(color, Offset(size.width - 4.5.dp.toPx(), y + 4.dp.toPx()), Offset(size.width, y), st, cap = StrokeCap.Round)
}

/**
 * 어디로 가는지 적은 큰 단추 (시안 .cta2): 왼쪽 “다음 걸음 · 따라 읽기” + 작게 거기서 기다리는 것 “2 / 4” · 오른쪽 화살표.
 * 채운 단추 하나 (흐름상 다음 걸음이라 금빛 결, [Primary] 와 같은 규칙). 빛깔은 테마 단추, 전시실 화면에서는 [fill] 로.
 */
@Composable
fun GoButton(label: String, detail: String? = null, modifier: Modifier = Modifier, enabled: Boolean = true, shine: Boolean = true,
             fill: Color = Ink.palette.fill, on: Color = Ink.palette.onFill, soft: Color = Ink.palette.onFillSoft, onClick: () -> Unit) {
    val said = listOfNotNull(label, detail).joinToString(" · ")
    Row(
        modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).semantics(mergeDescendants = true) { contentDescription = said }
            .pressable(enabled, onClick = onClick)
            .then(if (enabled) Modifier.background(fill) else Modifier.border(1.dp, soft))
            .giltSweep(shine && enabled, fill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 이름은 다 보이게, 기다리는 것은 남은 폭에 (넘치면 그쪽만 줄임)
        Text(label, style = Type.body.ui().copy(fontSize = 16.sp), color = if (enabled) on else soft, maxLines = 1, softWrap = false,
            modifier = Modifier.clearAndSetSemantics { })
        Text(if (detail != null) "  $detail" else "", style = Type.small.ui().copy(fontSize = 13.sp), color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clearAndSetSemantics { })
        Spacer(Modifier.width(12.dp))
        ArrowGlyph(if (enabled) on else soft)
    }
}

/**
 * 정답 표시 (시안 1 · .chk): 가는 금선 원이 그려지고 (0.7초) 그 안에 체크가 그어진다 (0.4초, 0.55초 뒤). 튀거나 퍼지지 않는다.
 */
@Composable
fun GiltCheck(still: Boolean, color: Color = Ink.palette.giltText, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val clock by rememberClock("check", still, 1000)
    val ring = win(clock, 50, 700); val tick = win(clock, 550, 400)
    Canvas(modifier.size(size)) {
        val st = 1.4.dp.toPx() * (size / 26.dp)
        val r = this.size.minDimension / 2 - st
        if (ring > 0f) drawArc(color, -90f, 360f * ring, false, Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(st, cap = StrokeCap.Round))
        if (tick > 0f) {
            val u = this.size.width / 26f
            val path = Path().apply { moveTo(8f * u, 13.5f * u); lineTo(11.4f * u, 16.9f * u); lineTo(18.5f * u, 9.6f * u) }
            val m = PathMeasure().apply { setPath(path, false) }
            val part = Path(); m.getSegment(0f, m.length * tick, part, true)
            drawPath(part, color, style = Stroke(st, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/**
 * 근거가 있는 지문 (시안 1 · .passage mark): [lit] 이 되면 나머지 글은 옅어지고 근거 [range] 는 먹색으로 남으며 (0.8초, 0.5초 뒤),
 * 그 아래 금선 밑줄이 왼쪽에서 오른쪽으로 그어진다 (0.8초, 0.55초 뒤, 줄이 바뀌면 줄마다 이어서). [range] 가 null 이면 글 전체가 근거,
 * 빈 범위면 이 글에는 근거가 없다 (옅어지기만).
 */
@Composable
fun EvidenceText(text: AnnotatedString, range: IntRange?, lit: Boolean, style: TextStyle, ink: Color, soft: Color, gilt: Color, still: Boolean, modifier: Modifier = Modifier) {
    val clock by rememberClock(text.text to lit, still || !lit, 1500, start = lit)
    val k = if (lit) win(clock, 500, 800) else 0f
    val u = if (lit) win(clock, 550, 800) else 0f
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val none = range != null && range.isEmpty()
    val r = range?.takeIf { !it.isEmpty() && it.first >= 0 && it.last < text.length }
    val painted = when {
        !lit -> text
        none -> AnnotatedString.Builder(text).apply { addStyle(SpanStyle(color = lerp(ink, soft, k)), 0, text.length) }.toAnnotatedString()
        r == null -> text
        else -> AnnotatedString.Builder(text).apply {
            val rest = SpanStyle(color = lerp(ink, soft, k))
            if (r.first > 0) addStyle(rest, 0, r.first)
            if (r.last + 1 < text.length) addStyle(rest, r.last + 1, text.length)
            addStyle(SpanStyle(color = ink), r.first, r.last + 1)
        }.toAnnotatedString()
    }
    Text(painted, style = style, color = ink, onTextLayout = { layout = it }, modifier = modifier.drawBehind {
        val lay = layout ?: return@drawBehind
        if (!lit || u <= 0f || none) return@drawBehind
        val a = r?.first ?: 0; val b = (r?.last ?: (text.length - 1)) + 1
        if (b <= a) return@drawBehind
        val l0 = lay.getLineForOffset(a); val l1 = lay.getLineForOffset((b - 1).coerceAtLeast(a))
        val segs = (l0..l1).map { ln ->
            val x0 = if (ln == l0) lay.getHorizontalPosition(a, true) else lay.getLineLeft(ln)
            val x1 = if (ln == l1) lay.getHorizontalPosition(b, true).let { if (it <= x0) lay.getLineRight(ln) else it } else lay.getLineRight(ln)
            Triple(minOf(x0, x1), maxOf(x0, x1), lay.getLineBaseline(ln) + 3.dp.toPx())
        }
        val total = segs.sumOf { (it.second - it.first).toDouble() }.toFloat()
        var left = total * u
        segs.forEach { (x0, x1, y) ->
            if (left <= 0f) return@forEach
            val w = minOf(x1 - x0, left)
            drawLine(gilt, Offset(x0, y), Offset(x0 + w, y), 1.dp.toPx())
            left -= w
        }
    })
}

/**
 * 화폐 수가 제자리에서 한 칸 넘어간다 (시안 1 · .digits): [from] 이 위로 빠지고 [to] 가 올라온다 (0.7초, [delay] 뒤). 날아다니지 않는다.
 */
@Composable
fun CurrencyTicker(label: String, from: Int, to: Int, ink: Color, soft: Color, still: Boolean, delay: Int = 900) {
    val clock by rememberClock(from to to, still, (delay + 800).toLong())
    val k = if (from == to) 1f else win(clock, delay, 700)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $to" }) {
        Text(label, style = Type.small.ui().copy(fontSize = 12.sp), color = soft, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
        val num = Type.body.copy(fontFamily = Faces.display, fontSize = 17.sp)
        Box(Modifier.height(20.dp).clipToBounds().clearAndSetSemantics { }, contentAlignment = Alignment.CenterStart) {
            if (k < 1f) Text("$from", style = num, color = ink, modifier = Modifier.graphicsLayer { translationY = -k * 20.dp.toPx(); alpha = 1f - k })
            Text("$to", style = num, color = ink, modifier = Modifier.graphicsLayer { translationY = (1f - k) * 20.dp.toPx(); alpha = k })
        }
    }
}

/** 걸음 이름 (마침 한 장 · 다음 걸음 단추). */
@Composable
fun stepName(m: ReplyMode): String = stringResource(modeLabel(m))

/**
 * 걸음 마침 한 장 (시안 rw2 2 · ①, 예전 “+2” 칩 자리): 화면을 덮고, 위 걸음 선이 한 칸 차고, 가운데에 그날 편지의 그림 한 장
 * (가는 테두리 · 그림자, 흐린 데서 또렷해지며 놓임, 우표 모양 없음 — 어느 작품이든 그날 편지의 그림), “+2 · 이해 확인을 마쳤어요 · 우표 2 · 그림 이름 · 해”.
 * 큰 단추는 다음 걸음 이름과 n / N. 누르면 그림이 다음 걸음 머리의 작은 그림 자리로 줄어들며 옮겨 가고 (사라지지 않는다), 그 걸음 내내 그 자리에 머문다.
 * 다음 걸음이 없으면 (마친 편지의 다시 하기 · 복습 탭) 들어온 곳으로 돌아가는 단추.
 */
@Composable
fun StepNoteOverlay(s: AppState) {
    val n = s.stepNote ?: return
    val p = Ink.palette
    val still = s.reducedMotion
    val w = s.work(n.room.series)
    val (_, letter) = s.letterOf(n.room)
    val view = s.room(n.room.series)
    val clock by rememberClock(n.n, still, 2200)
    val leave = remember(n.n) { Animatable(1f) }
    var going by remember(n.n) { mutableStateOf(false) }
    val pic = rememberBounds()
    LaunchedEffect(n.n) { s.cuePage() }
    LaunchedEffect(going) {
        if (!going) return@LaunchedEffect
        leave.animateTo(0f, tween(if (still) 0 else 420, easing = MuseumEase))
        if (s.stepNote?.n == n.n) s.stepNote = null
    }
    fun go() {
        if (going) return
        val nx = n.next
        if (nx is Next.AfterStep.Step && n.image != null) {
            s.stepPic = AppState.StepPic(Route.Play(n.room, nx.mode), n.image)
            s.flights.launch(n.image, pic.rect, "step-thumb", still, framed = true, alignY = 0.3f, source = "note")
        }
        going = true
    }
    androidx.activity.compose.BackHandler { go() }
    val modes = s.letterModes(n.room.series, s.letterOf(n.room).first, letter)
    val pr = s.progress(n.room.series, s.letterOf(n.room).first, letter.id)
    val doneSteps = modes.count { it in pr.replied || it in pr.skipped }
    val cur = w.kit.currency[uiLang()]
    val plate = letter.plate
    val sub = plate?.let { pl -> pl.title[view.read].takeIf { it.isNotBlank() }?.let { t -> listOf(t, Wall.year(pl.date)).filter { it.isNotBlank() }.joinToString(" · ") } }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = leave.value }.background(p.paper).pressable(haptic = false) { }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 걸음 선: 마친 걸음은 금빛, 방금 마친 칸은 왼쪽에서 차오른다
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                modes.forEach { m ->
                    val now = m == n.done
                    val on = (m in pr.replied || m in pr.skipped) && !now
                    Box(Modifier.weight(1f).height(2.dp).background(p.hair)) {
                        val f = if (now) win(clock, 200, 900) else if (on) 1f else 0f
                        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(2.dp).background(p.giltText))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.step_note_bar, stepName(n.done)), style = Type.small.ui().copy(fontSize = 12.sp), color = p.inkSoft, modifier = Modifier.weight(1f), maxLines = 1)
                Text("$doneSteps / ${modes.size}", style = Type.small.copy(fontFamily = Faces.display, fontSize = 13.sp), color = p.inkSoft)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically)) {
                Spacer(Modifier.height(24.dp))
                if (n.image != null) {
                    val k = win(clock, 300, 800)
                    Box(Modifier.size(112.dp, 140.dp).measured(pic).flightSource(s, "note")
                        .graphicsLayer { alpha = k; translationY = -(1f - k) * 8.dp.toPx(); val sc = 1.04f - 0.04f * k; scaleX = sc; scaleY = sc
                            val r = (1f - k) * 3f * density
                            renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(r, r) else null }
                        .shadow(14.dp).border(1.dp, p.ink.copy(alpha = 0.16f))) {
                        AssetImage(n.image, Modifier.fillMaxSize(), sample = 2, alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("+${n.gain}", style = Type.title.copy(fontFamily = Faces.display, fontSize = 24.sp), color = p.giltText)
                    val label = stepName(n.done)
                    Text(stringResource(R.string.step_note_done, if (uiHangul()) josa(label, "을", "를") else label), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink,
                        textAlign = TextAlign.Center)
                    Text(if (sub != null) stringResource(R.string.step_note_sub, cur, n.gain, sub) else stringResource(R.string.step_note_sub_only, cur, n.gain),
                        style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center, maxLines = 2)
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp).rise(win(clock, 900, 700), blur = 0f)) {
                when (val nx = n.next) {
                    is Next.AfterStep.Step -> GoButton(stringResource(R.string.go_next_step, stepName(nx.mode)), "$doneSteps / ${nx.total}") { go() }
                    is Next.AfterStep.Back -> GoButton(stringResource(if (nx.letter) R.string.go_back_letter else R.string.go_back)) { go() }
                }
            }
        }
    }
}

/** 걸음 머리의 작은 그림 (걸음 마침 한 장에서 옮겨 온 것): [PracticeHeader] 가 × 옆에 그린다. */
class StepThumb(val s: AppState, val image: String)
val LocalStepThumb = androidx.compose.runtime.compositionLocalOf<StepThumb?> { null }

@Composable
internal fun StepThumbSlot(t: StepThumb) {
    val p = Ink.palette
    Box(Modifier.padding(end = 10.dp).size(30.dp, 38.dp).flightSlot(t.s, "step-thumb").shadow(4.dp).border(1.dp, p.ink.copy(alpha = 0.16f))) {
        AssetImage(t.image, Modifier.fillMaxSize(), sample = 4, alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f))
    }
}

/**
 * 새 메달 (시안 rw2 5 · ③): 실제 고대 동전 메달이 옆에서 돌아 정면을 보고 (1.8초), 빛이 한 번 표면을 스친다. 진동 한 번.
 * 아래 진열장 줄에 같은 가족의 메달 칸, 새 칸이 금선으로 채워지고 “4 / 31”. 큰 단추 “진열장에 넣기 · 4 / 31” → 메달이 진열장의 그 칸으로 옮겨 간다.
 */
@Composable
fun MedalMoment(s: AppState) {
    val m = s.reward ?: return
    if (m.medals.isEmpty() || m.piece != null || m.catalogue != null) return
    val md = s.medalBook.medal(m.medals.first()) ?: return
    val still = s.reducedMotion
    val w = s.work(m.series.ifEmpty { s.current })
    val ui = uiLang()
    val clock by rememberClock(m, still, 3200)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coin = rememberBounds()
    LaunchedEffect(m) { s.cueTier(io.github.graviton94.todayletters.core.Tier.MASTER) }
    // 진동 한 번: 동전이 정면을 볼 때
    LaunchedEffect(m) {
        if (!still) androidx.compose.runtime.snapshotFlow { clock }.first { it >= 1900 }
        if (s.app.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    val all = cabinetMedals(s)
    val owned = all.count { it.id in s.medalsOwned }
    val family = all.filter { it.family == md.family }.sortedBy { it.threshold }
    fun place() {
        s.medalLanding = md.id
        s.flights.launch(md.image, coin.rect, "medal:${md.id}", still, round = true, source = "coin")
        s.reward = null
        s.go(Route.Medals)
    }
    androidx.activity.compose.BackHandler { s.reward = null }
    val bmp = rememberAsset(md.image)
    Box(Modifier.fillMaxSize().background(Museum.wall).pressable(haptic = false) { }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Spacer(Modifier.height(40.dp))
                Eyebrow(Wall.medal(w.series.original), Museum.gilt, Modifier.rise(win(clock, 0, 900)))
                val turn = win(clock, 200, 1800)
                val sweep = win(clock, 1600, 1400)
                Box(Modifier.size(150.dp).measured(coin).flightSource(s, "coin").graphicsLayer {
                    alpha = turn; rotationY = -70f * (1f - turn); translationY = (1f - turn) * 10.dp.toPx(); cameraDistance = 14f * density
                }.shadow(18.dp, CircleShape).clip(CircleShape).drawWithContent {
                    drawContent()
                    if (sweep > 0f && sweep < 1f) {
                        val x = size.width * (1.2f - 1.6f * sweep)
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x61FFF4DC), Color.Transparent),
                            start = Offset(x - size.width * 0.3f, size.height * 0.1f), end = Offset(x + size.width * 0.3f, size.height * 0.9f)), blendMode = BlendMode.Screen)
                    }
                }) {
                    if (bmp != null) Image(bmp, md.title[ui], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(md.title[ui], style = Type.title.ui().copy(fontSize = 24.sp), color = Museum.ink, textAlign = TextAlign.Center, modifier = Modifier.rise(win(clock, 1200, 900)))
                Text(medalHow(s, md), style = Type.small.ui(), color = Museum.soft, textAlign = TextAlign.Center, modifier = Modifier.rise(win(clock, 1350, 900)))
                // 진열장 줄: 같은 가족의 칸 (받은 것은 동전, 새 칸은 금선, 빈 칸은 자리만)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    family.forEach { f ->
                        val got = f.id in s.medalsOwned
                        val nw = f.id == md.id
                        val k = if (nw) win(clock, 2200, 800) else 1f
                        Box(Modifier.size(24.dp).then(if (nw) Modifier.rise(k) else Modifier).clip(CircleShape)
                            .background(if (got) Color.Transparent else Museum.wall2)) {
                            if (got) AssetImage(f.image, Modifier.fillMaxSize().padding(if (nw) 3.dp else 0.dp).clip(CircleShape), sample = 4)
                            // 새 칸은 금선, 빈 칸은 자리만 (그림 위에 그어 가리지 않게)
                            Box(Modifier.fillMaxSize().border(1.dp, if (nw) Museum.gilt else if (got) Color.Transparent else Museum.line, CircleShape))
                        }
                    }
                }
                Text("$owned / ${all.size}", style = Type.small.copy(fontFamily = Faces.display, fontSize = 14.sp), color = Museum.soft)
                if (m.medals.size > 1) m.medals.drop(1).mapNotNull { s.medalBook.medal(it) }.forEach { more ->
                    Text(stringResource(R.string.reward_ach, more.title[ui]), style = Type.small.ui(), color = Museum.gilt, maxLines = 1)
                }
                if (m.gain > 0) Text("+${m.gain} ${w.kit.currency[ui]}", style = Type.small.ui(), color = Museum.soft, maxLines = 1)
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                GoButton(stringResource(R.string.go_cabinet), "$owned / ${all.size}", fill = Museum.ink, on = Museum.wall, soft = Color(0xFF6B6255)) { place() }
            }
        }
    }
}
