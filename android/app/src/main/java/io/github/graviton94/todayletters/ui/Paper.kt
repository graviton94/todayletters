package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay

/**
 * 종이 재료. 편지지 조각 · 봉투 · 카드가 같은 재료를 쓴다.
 * 편지지는 두 테마 모두 밝은 종이(다크에서는 램프 아래의 종이)이고, 글자는 늘 [Palette.slipInk].
 */

/** 손으로 찢은 듯한 가장자리. 같은 [seed] 는 늘 같은 모양. */
class DeckleShape(private val seed: Int, private val jag: Dp = 2.2.dp, private val step: Dp = 9.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val j = with(density) { jag.toPx() }
        val st = with(density) { step.toPx() }
        val rnd = java.util.Random(seed.toLong())
        fun r() = rnd.nextFloat() * j
        val path = Path()
        path.moveTo(r(), r())
        var x = st
        while (x < size.width) { path.lineTo(x, r()); x += st }
        path.lineTo(size.width - r(), r())
        var y = st
        while (y < size.height) { path.lineTo(size.width - r() * 0.6f, y); y += st }
        path.lineTo(size.width - r(), size.height - r())
        x = size.width - st
        while (x > 0) { path.lineTo(x, size.height - r()); x -= st }
        path.lineTo(r(), size.height - r())
        y = size.height - st
        while (y > 0) { path.lineTo(r() * 0.6f, y); y -= st }
        path.close()
        return Outline.Generic(path)
    }
}

/** 종이 결: 가로 결(laid lines) · 세로 줄(chain lines) · 가장자리의 바랜 빛. */
fun Modifier.paperGrain(base: Color, laid: Color, age: Color): Modifier = drawBehind {
    drawRect(base)
    val gap = 4.dp.toPx()
    var y = 0f
    while (y < size.height) { drawLine(laid, Offset(0f, y), Offset(size.width, y), 1f); y += gap }
    val chain = 38.dp.toPx()
    var x = chain / 2
    val chainInk = laid.copy(alpha = laid.alpha * 0.45f)
    while (x < size.width) { drawLine(chainInk, Offset(x, 0f), Offset(x, size.height), 1f); x += chain }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Transparent, age),
            center = Offset(size.width * 0.45f, size.height * 0.32f),
            radius = maxOf(size.width, size.height) * 0.85f,
        )
    )
}

/** 책상 바탕: 아주 옅은 나뭇결 + 램프 빛. 오프닝 · 처음 소개 · 대화방 바탕. */
fun Modifier.desk(base: Color, lamp: Color, center: Float = 0.45f): Modifier = drawBehind {
    drawRect(base)
    drawRect(Brush.radialGradient(listOf(lamp, Color.Transparent), center = Offset(size.width / 2, size.height * center), radius = size.width * 0.9f))
}

/**
 * 편지지 조각. 공통 틀에서는 상자를 만들지 않는다: 종이와 거의 같은 빛의 평평한 면 하나 (그림자 · 결 · 찢은 가장자리 없음).
 * [seed] · [tilt] 는 예전 부르는 곳과 맞추려고 남겨 둠.
 */
@Composable
fun Slip(@Suppress("UNUSED_PARAMETER") seed: Int, @Suppress("UNUSED_PARAMETER") tilt: Float = 0f, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = Ink.palette
    Box(modifier.background(p.slip), content = content)
}

/** 동그란 초상: assets/portraits/<파일>. 없으면 이름 첫 글자. 채팅 앱의 프로필 사진처럼 어디서나 같은 모양. */
@Composable
fun Portrait(file: String, name: String, size: Dp = Tokens.Size.avatar, modifier: Modifier = Modifier) {
    val p = Ink.palette
    val bmp: ImageBitmap? = rememberPicture(if (file.isBlank()) "" else "portraits/$file").bmp
    val m = modifier.size(size).clip(CircleShape).border(1.dp, p.gilt.copy(alpha = 0.45f), CircleShape).semantics { contentDescription = name }
    if (bmp != null) Image(bmp, null, m, contentScale = ContentScale.Crop)
    else Box(m.background(p.hide), contentAlignment = Alignment.Center) {
        Text(name.take(1), style = Type.signature.copy(fontSize = (size.value * 0.44f).sp), color = p.inkSoft)
    }
}

/** 네모 체크: 켜면 채우고 체크 표시. */
@Composable
fun CheckMark(on: Boolean, size: Dp = 24.dp) {
    val p = Ink.palette
    Canvas(Modifier.size(size)) {
        if (on) {
            drawRect(p.fill)
            val w = this.size.width
            val path = Path().apply { moveTo(w * 0.22f, w * 0.52f); lineTo(w * 0.42f, w * 0.71f); lineTo(w * 0.79f, w * 0.31f) }
            drawPath(path, p.onFill, style = Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else drawRect(p.ink, style = Stroke(1.5.dp.toPx()))
    }
}

/** 점선 테두리 (아직 열리지 않은 자리). */
fun Modifier.dashed(color: Color): Modifier = drawBehind {
    drawRect(color, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
}

/** 듣기 단추: 동그란 금빛 테. 재생 중이면 멈춤 모양. */
@Composable
fun SpeakerButton(playing: Boolean, label: String, onDark: Boolean = false, onClick: () -> Unit) {
    val p = Ink.palette
    val c = if (onDark) p.giltText else Color(0xFF7A5A20)
    Box(
        Modifier.size(Tokens.Size.speaker).clip(CircleShape)
            .background(if (playing) p.slipInk else Color.Transparent)
            .border(1.dp, if (playing) p.slipInk else c.copy(alpha = 0.7f), CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = this.size.width
            if (playing) {
                drawRect(p.slip, Offset(w * 0.22f, w * 0.18f), Size(w * 0.18f, w * 0.64f))
                drawRect(p.slip, Offset(w * 0.6f, w * 0.18f), Size(w * 0.18f, w * 0.64f))
            } else {
                val st = Stroke(1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                val body = Path().apply {
                    moveTo(w * 0.12f, w * 0.38f); lineTo(w * 0.3f, w * 0.38f); lineTo(w * 0.52f, w * 0.18f)
                    lineTo(w * 0.52f, w * 0.82f); lineTo(w * 0.3f, w * 0.62f); lineTo(w * 0.12f, w * 0.62f); close()
                }
                drawPath(body, c, style = st)
                drawArc(c, -50f, 100f, false, Offset(w * 0.5f, w * 0.3f), Size(w * 0.36f, w * 0.4f), style = st)
            }
        }
    }
}

/** 재생 · 멈춤 표시 (그린 세모 · 네모): 글자 ▶ · ■ 는 기기에 따라 그림 글자(이모지)로 보일 수 있어서. */
@Composable
fun PlayGlyph(playing: Boolean, c: Color, size: Dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    if (playing) drawRect(c, Offset(w * 0.2f, w * 0.2f), Size(w * 0.6f, w * 0.6f))
    else drawPath(Path().apply { moveTo(w * 0.22f, w * 0.12f); lineTo(w * 0.9f, w * 0.5f); lineTo(w * 0.22f, w * 0.88f); close() }, c)
}

/**
 * 천천히 단추: 동그란 금빛 테에 “0.7×” (받아쓰기의 0.7× 동그라미와 같은 모양). [on] 이면 채워진다 (지금 천천히 소리 나는 중, 다시 누르면 멈춤).
 * [onDark] 는 [SpeakerButton] 과 같다 (편지지 위가 아닌 앱 바탕 위). [touch] 를 주면 누르는 자리는 그만큼 (동그라미는 [size]).
 */
@Composable
fun SlowButton(on: Boolean, label: String, onDark: Boolean = false, size: Dp = Tokens.Size.speaker, touch: Dp = size, color: Color? = null,
               text: TextStyle = Type.small.copy(fontSize = Tokens.Text.caps), onClick: () -> Unit) {
    val p = Ink.palette
    val c = color ?: if (onDark) p.giltText else Color(0xFF7A5A20)
    Box(Modifier.size(touch).clip(CircleShape).semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(if (on) c else Color.Transparent).border(1.dp, c, CircleShape), contentAlignment = Alignment.Center) {
            Text(SLOW_TEXT, style = text, color = if (on) p.paper else c, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * 0.7× 단추 (누르면 [play] 로 천천히 한 번, 그 소리가 나는 동안 채워지고 다시 누르면 멈춤). [ListenButtons] · 받아쓰기가 같이 쓴다.
 * [play] 는 [io.github.graviton94.todayletters.core.Listen.SLOW] 빠르기로 낭독을 시작해야 한다.
 */
@Composable
fun SlowToggle(play: () -> Unit, onDark: Boolean = false, size: Dp = Tokens.Size.speaker, touch: Dp = size, color: Color? = null,
               text: TextStyle = Type.small.copy(fontSize = Tokens.Text.caps), onStop: (() -> Unit)? = null) {
    val n = io.github.graviton94.todayletters.data.Narrator.shared(LocalContext.current)
    val key = remember { Any() }
    val on = n.now != null && n.slowBy === key
    SlowButton(on, androidx.compose.ui.res.stringResource(io.github.graviton94.todayletters.R.string.a11y_listen_slow), onDark, size, touch, color, text) {
        if (on) { n.slowBy = null; onStop?.invoke() ?: n.stop() }
        else { play(); n.slowBy = key }
    }
}

/** 천천히 단추의 글자 ("0.7×"). */
val SLOW_TEXT = "${io.github.graviton94.todayletters.core.Listen.SLOW}\u00D7"

/**
 * 듣기 단추 둘 (시안 slow.html): 소리가 나는 모든 자리에 이 부품 하나.
 * 스피커 = 이 작품의 기본 빠르기 ([play] 에 null), 0.7× = 그 소리를 [io.github.graviton94.todayletters.core.Listen.SLOW] 로 한 번.
 * 0.7× 를 다시 누르면 멈춘다 ([onStop], 없으면 낭독 멈춤). [onListen] 을 주면 스피커는 그것을 (편지 방처럼 스스로 재생 · 멈춤을 다루는 곳).
 * [ring] 이면 낱말 이름표 · 카드의 작은 금빛 고리 모양 (누르는 자리 48dp, 고리 36dp), 아니면 [SpeakerButton] 모양.
 */
@Composable
fun ListenButtons(
    play: (Float?) -> Unit, label: String = androidx.compose.ui.res.stringResource(io.github.graviton94.todayletters.R.string.a11y_listen_word),
    onDark: Boolean = false, vertical: Boolean = false, playing: Boolean = false, ring: Boolean = false,
    onListen: (() -> Unit)? = null, onStop: (() -> Unit)? = null,
) {
    val p = Ink.palette
    val n = io.github.graviton94.todayletters.data.Narrator.shared(LocalContext.current)
    // 천천히 소리가 나는 중이면 스피커는 멈춤 모양을 쓰지 않는다
    val slowOn = playing && n.now != null && n.slowBy != null
    val listen = { onListen?.invoke() ?: play(null) }
    val speaker = @Composable {
        if (ring) Box(Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = label }.clickable(role = Role.Button) { listen() }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp).border(1.dp, p.giltText, CircleShape), contentAlignment = Alignment.Center) { SpeakerGlyph(p.giltText, 16.dp) }
        } else SpeakerButton(playing && !slowOn, label, onDark) { listen() }
    }
    val slow = @Composable {
        val go = { play(io.github.graviton94.todayletters.core.Listen.SLOW) }
        if (ring) SlowToggle(go, size = 36.dp, touch = 48.dp, color = p.giltText, onStop = onStop)
        else SlowToggle(go, onDark, onStop = onStop)
    }
    if (vertical) androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Tokens.Space.s1),
        horizontalAlignment = Alignment.CenterHorizontally) { speaker(); slow() }
    else androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (ring) 0.dp else Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically) { speaker(); slow() }
}

/**
 * 잉크 번짐: 글자가 줄마다 차례로, 펜으로 쓰듯 왼쪽에서 오른쪽으로 번지며 나타난다.
 * [animate] 가 꺼져 있으면(이미 읽은 편지 · 바로 보기 · 움직임 줄이기) 처음부터 다 보인다.
 * [go] 가 켜질 때 시작하고, 다 나타나면 [onDone].
 */
@Composable
fun InkText(
    text: String, style: TextStyle, color: Color, animate: Boolean, modifier: Modifier = Modifier,
    go: Boolean = true, lineMs: Int = Tokens.Motion.inkLineMs, onDone: () -> Unit = {},
    marks: List<IntRange> = emptyList(), markColor: Color = Color.Unspecified, onMark: ((Int) -> Unit)? = null,
    /** 지금 낭독 중인 자리 ([spokenRange]): 옅은 바탕과 밑줄. */
    spoken: IntRange? = null, spokenColor: Color = markColor,
    /** 눌러서 뜻 보기: 모든 낱말의 자리 ([marks] 가 아닌 곳을 누르면 [onTap]). [hints] 는 옅은 점선 밑줄을 그을 자리, [picked] 는 지금 펼친 낱말. */
    taps: List<IntRange> = emptyList(), onTap: ((Int) -> Unit)? = null, hints: List<IntRange> = emptyList(), picked: IntRange? = null,
    /** [picked] 가 든 글 상자의 자리 ([glossSpot], 뜻 카드를 그 아래에). */
    onPickedAt: (GlossAnchor?) -> Unit = {},
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val progress = remember(text) { Animatable(if (animate) 0f else 1f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(text, animate, go, layout?.lineCount) {
        val n = layout?.lineCount ?: return@LaunchedEffect
        if (!go) return@LaunchedEffect
        if (progress.value < 1f) progress.animateTo(1f, tween(lineMs * n, easing = LinearEasing))
        done()
    }
    Text(
        text, style = style, color = color, onTextLayout = { layout = it },
        modifier = modifier
            .glossSpot(picked, onPickedAt)
            .then(if ((onMark != null && marks.isNotEmpty()) || (onTap != null && taps.isNotEmpty())) Modifier.pointerInput(marks, taps) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos)
                    val m = if (onMark != null) marks.indexOfFirst { off in it.first..(it.last + 1) } else -1
                    if (m >= 0) onMark?.invoke(m)
                    else if (onTap != null) taps.indexOfFirst { !it.isEmpty() && off in it.first..(it.last + 1) }.takeIf { it >= 0 }?.let(onTap)
                }
            } else Modifier)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                val l0 = layout
                // 낭독 중인 마디: 글자 뒤에 옅은 바탕, 아래에 가는 밑줄
                if (spoken != null && l0 != null && !spoken.isEmpty()) {
                    val end = (spoken.last + 1).coerceAtMost(l0.layoutInput.text.length)
                    var a = spoken.first.coerceAtLeast(0)
                    while (a < end) {
                        val line = l0.getLineForOffset(a)
                        val lineEnd = minOf(end, l0.getLineEnd(line, visibleEnd = true))
                        val x1 = l0.getHorizontalPosition(a, true); val x2 = l0.getHorizontalPosition(lineEnd, true)
                        val top = l0.getLineTop(line); val base = l0.getLineBaseline(line)
                        drawRect(spokenColor.copy(alpha = 0.12f), Offset(minOf(x1, x2), top), Size(kotlin.math.abs(x2 - x1), l0.getLineBottom(line) - top))
                        drawLine(spokenColor, Offset(minOf(x1, x2), base + 4.dp.toPx()), Offset(maxOf(x1, x2), base + 4.dp.toPx()), 1.5.dp.toPx())
                        if (lineEnd <= a) break
                        a = lineEnd
                    }
                }
                // 펼친 낱말: 옅은 금빛 바탕
                if (picked != null && l0 != null && !picked.isEmpty()) {
                    val end = (picked.last + 1).coerceAtMost(l0.layoutInput.text.length)
                    val line = l0.getLineForOffset(picked.first)
                    val x1 = l0.getHorizontalPosition(picked.first, true); val x2 = l0.getHorizontalPosition(minOf(end, l0.getLineEnd(line, visibleEnd = true)), true)
                    val top = l0.getLineTop(line)
                    drawRect(Color(0x55D2A955), Offset(minOf(x1, x2) - 2.dp.toPx(), top), Size(kotlin.math.abs(x2 - x1) + 4.dp.toPx(), l0.getLineBottom(line) - top))
                }
                drawContent()
                val l = layout ?: return@drawWithContent
                // 눌러서 뜻을 볼 수 있는 낱말: 아주 옅은 점선 (오늘의 낱말 밑줄보다 흐리게)
                if (hints.isNotEmpty()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx()))
                    val faint = color.copy(alpha = 0.28f)
                    hints.forEach { r ->
                        if (r.isEmpty()) return@forEach
                        val end = (r.last + 1).coerceAtMost(l.layoutInput.text.length)
                        var a = r.first
                        while (a < end) {
                            val line = l.getLineForOffset(a)
                            val lineEnd = minOf(end, l.getLineEnd(line, visibleEnd = true))
                            val x1 = l.getHorizontalPosition(a, true); val x2 = l.getHorizontalPosition(lineEnd, true)
                            val y = l.getLineBaseline(line) + 3.dp.toPx()
                            drawLine(faint, Offset(minOf(x1, x2), y), Offset(maxOf(x1, x2), y), 0.8.dp.toPx(), pathEffect = dash)
                            if (lineEnd <= a) break
                            a = lineEnd
                        }
                    }
                }
                // 단어장 낱말: 점선 밑줄 (글자와 함께 잉크로 번진다)
                if (marks.isNotEmpty()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.5.dp.toPx()))
                    marks.forEach { r ->
                        val end = (r.last + 1).coerceAtMost(l.layoutInput.text.length)
                        var a = r.first
                        while (a < end) {
                            val line = l.getLineForOffset(a)
                            val lineEnd = minOf(end, l.getLineEnd(line, visibleEnd = true))
                            val x1 = l.getHorizontalPosition(a, true); val x2 = l.getHorizontalPosition(lineEnd, true)
                            val y = l.getLineBaseline(line) + 3.dp.toPx()
                            drawLine(markColor, Offset(minOf(x1, x2), y), Offset(maxOf(x1, x2), y), 1.2.dp.toPx(), pathEffect = dash)
                            if (lineEnd <= a) break
                            a = lineEnd
                        }
                    }
                }
                val pr = progress.value
                if (pr >= 1f) return@drawWithContent
                val n = l.lineCount
                val feather = 30.dp.toPx()
                for (i in 0 until n) {
                    val f = FastOutSlowInEasing.transform((pr * n - i).coerceIn(0f, 1f))
                    if (f >= 1f) continue
                    val left = l.getLineLeft(i); val right = l.getLineRight(i)
                    val top = l.getLineTop(i); val bottom = l.getLineBottom(i)
                    val x = left - feather + (right - left + feather) * f
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = x, endX = x + feather),
                        topLeft = Offset(x, top), size = Size(size.width - x + 1f, bottom - top), blendMode = BlendMode.DstOut,
                    )
                }
            },
    )
}

/**
 * 지금 [asset] 을 낭독하고 있으면 [text] 안에서 읽는 중인 마디의 글자 범위, 아니면 null.
 * 마디 시각 파일 (m<i>_<언어>.json) 이 없거나 마디가 하나면 문장 전체. 재생 위치를 화면마다 한 번 본다.
 */
@Composable
fun spokenRange(narrator: io.github.graviton94.todayletters.data.Narrator, asset: String, text: String): IntRange? {
    val on = narrator.now == asset
    if (!on) return null
    val chunks = remember(asset) { narrator.chunks(asset) }
    // 마디 안의 문장까지 나눈 토막 (core Spoken.timed): 한 마디에 문장이 여럿이어도 지금 읽는 문장만 칠한다
    val segs = remember(asset, text) { io.github.graviton94.todayletters.core.Spoken.timed(text, chunks.map { it.start }, chunks.map { it.end }, chunks.map { it.text }) }
    var k by remember(asset) { mutableStateOf(0) }
    LaunchedEffect(asset, segs) {
        k = 0
        if (segs.size < 2) return@LaunchedEffect
        val starts = segs.map { it.start }
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            val at = narrator.position()
            // 마디 시각은 TTS 조각의 시작(앞 쉼 포함)이라 실제 목소리보다 이르다: LEAD_MS 만큼 늦춰 칠한다
            if (at >= 0) io.github.graviton94.todayletters.core.Spoken.at(starts, at, io.github.graviton94.todayletters.core.Spoken.LEAD_MS).let { if (it != k) k = it }
        }
    }
    return segs.getOrNull(k)?.range?.takeIf { segs.size > 1 && !it.isEmpty() } ?: text.indices
}

/** [text] 에서 [range] 를 칠한 글 (낭독 중인 자리: 색과 밑줄). [range] 가 null 이면 그대로. */
fun spokenText(text: String, range: IntRange?, color: Color): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    append(text)
    if (range != null && !range.isEmpty()) addStyle(
        androidx.compose.ui.text.SpanStyle(color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        range.first.coerceIn(0, text.length), (range.last + 1).coerceIn(0, text.length),
    )
}

/** 잠깐 기다리기 (움직임 줄이기면 바로). */
suspend fun pause(ms: Int, still: Boolean) { if (!still && ms > 0) delay(ms.toLong()) }

/**
 * 맞힌 뒤 저절로 넘어가기 전: 적어도 [ms] 를 두고, 그 사이 정답 발음([narrator] 의 지금 소리)이 울리면 끝날 때까지 기다린다
 * ([io.github.graviton94.todayletters.core.Listen.holdAdvance], 최대 8초). 소리가 없거나 소리를 끈 설정이면 [ms] 만.
 */
suspend fun awaitAnswerSound(narrator: io.github.graviton94.todayletters.data.Narrator, ms: Int) {
    val t0 = android.os.SystemClock.uptimeMillis()
    while (io.github.graviton94.todayletters.core.Listen.holdAdvance(android.os.SystemClock.uptimeMillis() - t0, narrator.now != null, ms.toLong())) delay(50)
}

/**
 * 단어장 낱말이 문장 안 어디에 있는지: 글자 범위와 낱말 번호.
 * 낱말은 사전형으로 적혀 있어서(arbre · fleur) 문장의 꼴(arbres · fleurs)과 앞부분이 같으면 같은 낱말로 본다.
 */
fun wordMarks(text: String, words: List<String>): List<Pair<IntRange, Int>> {
    fun base(x: String) = java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace("\u2060", "")
    val keys = words.map { base(it) }
    val out = mutableListOf<Pair<IntRange, Int>>()
    Regex("[\\p{L}\u2060]+").findAll(text).forEach { m ->
        val tok = base(m.value)
        val k = keys.indexOfFirst { key ->
            when {
                key.isEmpty() -> false
                tok == key || tok == key + "s" || tok == key + "x" -> true
                key.length <= 4 -> false   // 짧은 낱말은 꼴이 같을 때만 (mois ≠ moi)
                else -> tok.startsWith(key.dropLast(1)) && tok.length in (key.length - 1)..(key.length + 2)
            }
        }
        if (k >= 0 && out.none { it.second == k }) out += m.range to k
    }
    return out
}
