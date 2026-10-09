package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Seal
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import io.github.graviton94.todayletters.design.hangul
import io.github.graviton94.todayletters.design.uiHangul

/** 진동을 켰는가 (앱 설정). Root 가 넣어 준다. */
val LocalHaptics = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * 누를 수 있는 모든 것의 공통 손맛: 누르는 동안 살짝 작아지고, 손을 떼면 가벼운 진동.
 * 움직임 줄이기에서도 진동은 남는다 (설정에서 끌 수 있음).
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, haptic: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed && enabled) 0.97f else 1f, androidx.compose.animation.core.tween(90), label = "press")
    val h = androidx.compose.ui.platform.LocalHapticFeedback.current
    val on = LocalHaptics.current
    return this.graphicsLayer { scaleX = k; scaleY = k }
        .clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = role) {
            if (haptic && on) h.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** 앱 글자(UI)용 글자 모양: 앱 글자가 한국어면 한글 글꼴로. */
@Composable
fun TextStyle.ui(): TextStyle = hangul(uiHangul())

/** 한국어 조사: 마지막 글자에 받침이 있으면 [withFinal](이 · 을 · 은), 없으면 [without](가 · 를 · 는). 한글이 아니면 받침 없는 쪽. */
fun josa(word: String, withFinal: String, without: String): String {
    val c = word.trimEnd().replace("\u2060", "").lastOrNull() ?: return word + without
    val final = c in '\uAC00'..'\uD7A3' && (c.code - 0xAC00) % 28 != 0
    return word + if (final) withFinal else without
}

/** 앱 글자 언어를 편지 줄 언어로 (큐레이터 노트 · 이름처럼 앱 글자로 보여 줄 때). */
@Composable
fun uiLang(): Lang = if (uiHangul()) Lang.KO else Lang.EN

/** 편지 줄용: 그 줄의 언어가 한국어면 한글 글꼴로. 한국어는 어절 단위로 줄을 바꾼다. */
fun TextStyle.of(lang: Lang): TextStyle =
    if (lang == Lang.KO) hangul().copy(lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)) else this

@Composable
/** 대문자 머리말. [decorative] 면 장식 글자(프랑스어 표제 등)라 화면 읽기에서 건너뛴다. */
fun Caps(text: String, color: Color = Ink.palette.inkSoft, small: Boolean = false, modifier: Modifier = Modifier, decorative: Boolean = false) =
    Text(text.uppercase(), style = (if (small) Type.capsSm else Type.caps).hangul(text.any { it in '\uAC00'..'\uD7A3' }), color = color,
        modifier = if (decorative) modifier.clearAndSetSemantics { } else modifier)

@Composable
fun Rule(color: Color = Ink.palette.ink) = Box(Modifier.fillMaxWidth().height(Tokens.Stroke.rule).background(color))

@Composable
fun Hair() = Box(Modifier.fillMaxWidth().height(Tokens.Stroke.hair).background(Ink.palette.hair))

/** 봉랍: 보내는 사람마다 색. */
@Composable
fun SealMark(seal: Seal, letter: String, size: Dp = Tokens.Size.seal) =
    Box(
        Modifier.size(size).background(seal.wax, CircleShape).border(Tokens.Stroke.sealRing, seal.ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = Type.signature.copy(fontSize = (size.value * 0.48f).sp), color = seal.ink) }

/** 우체국 소인: 두 겹 원. 편지의 시간은 이렇게 글자로만 보여 준다. */
@Composable
fun Postmark(place: String, day: String, year: String, size: Dp = Tokens.Size.postmark) {
    val c = Ink.palette.post
    Box(
        Modifier.size(size).rotate(-10f).border(1.3.dp, c, CircleShape).padding(3.dp).border(1.dp, c, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(place.uppercase(), style = Type.capsSm.copy(fontSize = 7.5.sp, letterSpacing = 0.4.sp), color = c)
            Text(day.uppercase(), style = Type.capsSm.copy(fontSize = 9.sp, letterSpacing = 0.4.sp), color = c)
            Text(year, style = Type.capsSm.copy(fontSize = 7.5.sp, letterSpacing = 0.4.sp), color = c)
        }
    }
}

/** 편지 한 쌍: 배우는 언어 한 줄 + 작게 번역 한 줄. 편지지 위(늘 밝은 종이) 또는 답장(채운 바탕) 위에 놓인다. */
@Composable
fun Pair2(learn: String, learnLang: Lang, read: String?, readLang: Lang, onFill: Boolean = false, caret: Boolean = false) {
    val p = Ink.palette
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Text(learn + if (caret) "▏" else "", style = Type.target.of(learnLang), color = if (onFill) p.onFill else p.slipInk)
        if (read != null) Text(read, style = Type.base.of(readLang), color = if (onFill) p.onFillSoft else p.slipSoft)
    }
}

/** 받은 편지 한 조각 (편지지 조각 위). */
@Composable
fun Incoming(seed: Int = 1, content: @Composable ColumnScope.() -> Unit) {
    Slip(seed, modifier = Modifier.widthIn(max = Tokens.Size.bubbleMax + 24.dp)) {
        Column(
            Modifier.padding(start = Tokens.Space.s4, end = Tokens.Space.s3, top = Tokens.Space.s4, bottom = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) { content() }
    }
}

/** 보낸 답장: 오른쪽, 채운 바탕. */
@Composable
fun Outgoing(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.widthIn(max = Tokens.Size.replyMax).background(Ink.palette.fill)
            .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
    ) { content() }

/** 입력 중: 반짝이는 별 세 개 (움직임 줄이기면 멈춤). */
@Composable
fun Twinkle(still: Boolean) {
    val p = Ink.palette
    val t = rememberInfiniteTransition(label = "twinkle")
    Row(
        Modifier.background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(Tokens.Motion.twinkleMs / 2, delayMillis = i * 250), RepeatMode.Reverse), label = "s$i")
            Box(Modifier.size(6.dp).alpha(if (still) 0.8f else a).background(p.gilt, CircleShape))
        }
    }
}

@Composable
/** 단추 크기는 두 가지뿐: 큰 것 54 (글 17), 작은 것 46 (글 15). 같은 줄의 단추는 같은 크기. */
fun Primary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    val p = Ink.palette
    Box(
        modifier.fillMaxWidth().heightIn(min = if (small) Tokens.Size.buttonSm else Tokens.Size.button).pressable(enabled, onClick = onClick).background(if (enabled) p.fill else p.hide),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.body.ui().copy(fontSize = if (small) 15.sp else 17.sp), color = if (enabled) p.onFill else p.hideInk) }
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val p = Ink.palette
    Box(
        modifier.fillMaxWidth().heightIn(min = if (small) Tokens.Size.buttonSm else Tokens.Size.button).pressable(onClick = onClick).border(Tokens.Stroke.hair, p.ink),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.body.ui().copy(fontSize = if (small) 15.sp else 17.sp), color = p.ink) }
}

/** 선택지 줄 (테마 · 언어 · 하루 편지 수 …). */
@Composable
fun <T> Choices(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.hair)) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            if (i > 0) Box(Modifier.width(Tokens.Stroke.hair).heightIn(min = Tokens.Size.touch).background(p.hair))
            Box(
                Modifier.weight(1f).heightIn(min = Tokens.Size.touch).background(if (on) p.fill else Color.Transparent)
                    .pressable(role = Role.RadioButton) { onPick(value) }.padding(horizontal = Tokens.Space.s1),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Type.label.ui(), color = if (on) p.onFill else p.ink) }
        }
    }
}

/** 맨 위 줄: [뒤로] 제목 … [(?)] [⚙]. (?) 는 그 화면 도움말만 다시. ⚙ 는 이름표 맨 위 화면에서만. */
@Composable
fun TopBar(title: String, s: AppState, help: String?, showBack: Boolean, showSettings: Boolean, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = Ink.palette
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2, vertical = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(p.ink) }
            else Box(Modifier.width(Tokens.Space.s4))
            Text(title, style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
            trailing()
            if (help != null) IconButton(stringResource(R.string.help), onClick = { s.coachAgain(help) }) {
                // (?) 는 폰 글꼴 배율과 상관없이 같은 크기
                Box(Modifier.size(22.dp).border(1.dp, p.ink, CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(10.dp)) {
                        val w = size.width
                        drawArc(p.ink, 200f, 250f, false, topLeft = Offset(0f, 0f), size = Size(w, w * 0.8f), style = Stroke(1.6.dp.toPx()))
                        drawCircle(p.ink, 1.2.dp.toPx(), Offset(w / 2, w * 1.15f))
                    }
                }
            }
            if (showSettings) IconButton(stringResource(R.string.settings), onClick = { s.settingsOpen = true }) { Gear(p.ink) }
        }
        Rule()
    }
}

@Composable
fun IconButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) =
    Box(
        Modifier.size(Tokens.Size.touch).semantics { contentDescription = label }.pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }

@Composable
fun Chevron(c: Color) = Canvas(Modifier.size(Tokens.Size.icon)) {
    val w = size.width
    drawLine(c, Offset(w * 0.65f, w * 0.15f), Offset(w * 0.3f, w * 0.5f), 1.6.dp.toPx())
    drawLine(c, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.65f, w * 0.85f), 1.6.dp.toPx())
}

@Composable
fun Gear(c: Color) = Canvas(Modifier.size(Tokens.Size.icon)) {
    val r = size.width / 2
    drawCircle(c, r * 0.32f, style = Stroke(1.5.dp.toPx()))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val o = Offset(r + (r * 0.62f) * kotlin.math.cos(a).toFloat(), r + (r * 0.62f) * kotlin.math.sin(a).toFloat())
        val e = Offset(r + (r * 0.95f) * kotlin.math.cos(a).toFloat(), r + (r * 0.95f) * kotlin.math.sin(a).toFloat())
        drawLine(c, o, e, 2.dp.toPx())
    }
    drawCircle(c, r * 0.64f, style = Stroke(1.5.dp.toPx()))
}

/** 그림: assets/plates/<파일>. 아직 고르지 않았으면 빈 액자에 "그림 준비 중" (임시 그림은 그리지 않는다). */
@Composable
fun PlateImage(file: String, modifier: Modifier) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val bmp: ImageBitmap? = remember(file) {
        if (file.isBlank()) null else runCatching {
            ctx.assets.open("plates/$file").use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull()
    }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(p.hide).border(Tokens.Stroke.hair, p.hair), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.plate_soon), style = Type.small.ui(), color = p.hideInk)
    }
}
