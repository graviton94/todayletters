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

/** 편지지 조각: 찢은 가장자리 · 종이 결 · 그림자 · 살짝 기운 각도. */
@Composable
fun Slip(seed: Int, tilt: Float = 0f, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = Ink.palette
    val shape = remember(seed) { DeckleShape(seed) }
    Box(
        modifier
            .graphicsLayer {
                rotationZ = tilt
                shadowElevation = 5.dp.toPx()
                this.shape = shape
                clip = true
                ambientShadowColor = p.shadow
                spotShadowColor = p.shadow
            }
            .paperGrain(p.slip, p.slipLaid, p.slipAge),
        content = content,
    )
}

/** 동그란 초상: assets/portraits/<파일>. 없으면 이름 첫 글자. 채팅 앱의 프로필 사진처럼 어디서나 같은 모양. */
@Composable
fun Portrait(file: String, name: String, size: Dp = Tokens.Size.avatar, modifier: Modifier = Modifier) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val bmp: ImageBitmap? = remember(file) {
        if (file.isBlank()) null else runCatching {
            ctx.assets.open("portraits/$file").use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull()
    }
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

/**
 * 잉크 번짐: 글자가 줄마다 차례로, 펜으로 쓰듯 왼쪽에서 오른쪽으로 번지며 나타난다.
 * [animate] 가 꺼져 있으면(이미 읽은 편지 · 바로 보기 · 움직임 줄이기) 처음부터 다 보인다.
 * [go] 가 켜질 때 시작하고, 다 나타나면 [onDone].
 */
@Composable
fun InkText(
    text: String, style: TextStyle, color: Color, animate: Boolean, modifier: Modifier = Modifier,
    go: Boolean = true, lineMs: Int = Tokens.Motion.inkLineMs, onDone: () -> Unit = {},
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
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
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

/** 잠깐 기다리기 (움직임 줄이기면 바로). */
suspend fun pause(ms: Int, still: Boolean) { if (!still && ms > 0) delay(ms.toLong()) }
