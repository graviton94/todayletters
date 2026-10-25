package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Moment
import io.github.graviton94.todayletters.core.RoomFlow
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Palette
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/*
 * 편지 방의 부품 (시안 room src4, 2026-10-25 허락): 지금 문장 카드 · 낱말 따라가기 · 아래 플레이어 · 멈춘 띠 · 내 차례 단추 ·
 * 순간 카드 (누가 하는지에 따라 왼쪽 · 오른쪽) · 큐레이터 노트 카드 · 낱말 · 기능어 카드.
 */

/** 편지 방 색 (밝음 · 어둠): 시안의 금빛 · 번역 갈색 · 뜻 청회색. */
internal class RoomInk(p: Palette) {
    val dark = p.paper.luminance() < 0.5f
    val card = p.slip
    val hair = p.hair
    val ink = p.slipInk
    val soft = p.slipSoft
    /** 지금 낱말 (금빛) · 아직 안 나온 낱말 (옅게). */
    val gilt = if (dark) Color(0xFFD9B872) else Color(0xFFA57B2C)
    val faint = p.slipInk.copy(alpha = 0.38f)
    /** 뜻 (청회색). */
    val gloss = if (dark) Color(0xFF8FA6B8) else Color(0xFF4F6679)
    /** 지금 문장의 번역 (진한 갈색) · 읽은 문장의 번역. */
    val trans = if (dark) Color(0xFFD3C8B8) else Color(0xFF4F473D)
    val transRead = if (dark) Color(0xFFA99F92) else Color(0xFF6A6054)
    /** 어두운 칸 (멈춘 띠 · 내가 보낸 말풍선): 밝은 테마면 먹색, 어두운 테마면 종이색 (늘 둘레와 반대). */
    val deep = if (dark) Color(0xFFE6DFD2) else Color(0xFF1C1814)
    val onDeep = if (dark) Color(0xFF1C1814) else Color(0xFFEEE7DA)
    val onDeepSoft = if (dark) Color(0xFF6A6054) else Color(0xFFB9AFA1)
    val onDeepGilt = if (dark) Color(0xFF8A6420) else Color(0xFFE3C27A)
    val giltFill = if (dark) Color(0xFFC9A55E) else Color(0xFFA57B2C)
    val onGilt = if (dark) Color(0xFF1C1814) else Color(0xFFFBF8F2)
}

private val BubbleShape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
private val MineShape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)

/** 낱말 따라가기 한 칸의 상태: 말한 것 · 지금 ([frac] = 지금 낱말 밑줄이 그어진 몫) · 아직. */
internal data class Karaoke(val now: Int, val frac: Float, val all: Boolean) {
    companion object {
        /** 다 말한 상태 (읽은 문장 · 소리 없이 읽기). */
        val DONE = Karaoke(Int.MAX_VALUE, 1f, true)
        /** 아직 시작 전 (모두 옅게). */
        val BEFORE = Karaoke(-1, 0f, false)

        /** 재생 위치 [pos] 에서 ([times] 낱말 시각, [still] 이면 밑줄이 자라지 않고 다 그어짐). */
        fun at(pos: Int, times: List<IntRange>, still: Boolean): Karaoke {
            if (times.isEmpty()) return DONE
            val k = RoomFlow.wordAt(pos, times)
            if (k < 0) return BEFORE
            if (!RoomFlow.lingering(pos, times, k)) return Karaoke(k + 1, 0f, false)
            val w = times[k]
            val frac = if (still) 1f else ((pos - w.first).toFloat() / (w.last - w.first).coerceAtLeast(120)).coerceIn(0f, 1f)
            return Karaoke(k, frac, false)
        }
    }
}

/**
 * 글 한 토막 ([text] = 메시지 글 [offset] 부터) 을 낱말 따라가기로: 말한 낱말은 먹색, 지금 낱말은 금빛 + 그 길이만큼 자라는 밑줄, 아직은 옅게.
 * [words] 는 메시지 전체의 낱말 글자 범위 ([RoomFlow.wordRanges]). [dotted] 는 기능어 노트가 있는 자리 (가는 점선), [grouped] 는 묶음 (가는 선).
 * [onTap] 은 누른 글자 자리 (메시지 기준) 를 받아 쓸모가 있으면 true (그러면 부모의 누르기 = 다시 듣기 로 가지 않는다).
 */
@Composable
internal fun KaraokeText(
    text: String, offset: Int, words: List<IntRange>, state: Karaoke, style: TextStyle, ink: RoomInk,
    dotted: List<IntRange> = emptyList(), grouped: List<IntRange> = emptyList(), onTap: ((Int) -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val end = offset + text.length
    val shown = buildAnnotatedString {
        append(text)
        words.forEachIndexed { j, w ->
            val a = (w.first - offset).coerceAtLeast(0); val b = (w.last + 1 - offset).coerceAtMost(text.length)
            if (w.last < offset || w.first >= end || a >= b) return@forEachIndexed
            val c = when {
                state.all || j < state.now -> ink.ink
                j == state.now -> ink.gilt
                else -> ink.faint
            }
            addStyle(SpanStyle(color = c), a, b)
        }
    }
    val now = words.getOrNull(state.now)?.takeIf { !state.all && it.first < end && it.last >= offset }
    Text(
        shown, style = style, color = ink.ink, onTextLayout = { layout = it },
        modifier = modifier
            .drawBehind {
                val l = layout ?: return@drawBehind
                fun box(r: IntRange): androidx.compose.ui.geometry.Rect? {
                    val a = (r.first - offset).coerceIn(0, text.length); val b = (r.last + 1 - offset).coerceIn(0, text.length)
                    if (a >= b) return null
                    return runCatching { l.getPathForRange(a, b).getBounds() }.getOrNull()
                }
                dotted.forEach { r -> box(r)?.let { bx ->
                    drawLine(ink.gloss.copy(alpha = 0.6f), Offset(bx.left, bx.bottom - 1.dp.toPx()), Offset(bx.right, bx.bottom - 1.dp.toPx()), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6.dp.toPx(), 2.4.dp.toPx())))
                } }
                grouped.forEach { r -> box(r)?.let { bx ->
                    drawLine(ink.gloss.copy(alpha = 0.45f), Offset(bx.left, bx.bottom), Offset(bx.right, bx.bottom), 1.dp.toPx())
                } }
                if (now != null && state.frac > 0f) box(now)?.let { bx ->
                    val y = bx.bottom - 1.dp.toPx()
                    drawLine(ink.gilt, Offset(bx.left, y), Offset(bx.left + bx.width * state.frac, y), 2.dp.toPx())
                }
            }
            .then(if (onTap != null) Modifier.pointerInput(offset, text) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                    val l = layout ?: return@awaitEachGesture
                    val at = l.getOffsetForPosition(up.position).coerceIn(0, (text.length - 1).coerceAtLeast(0))
                    if (onTap(offset + at)) up.consume()
                }
            } else Modifier),
    )
}

/** 문장 사이 쉼 동안 카드 아래 가는 금선이 찬다 ([ms] 동안, [still] 이면 차오르지 않고 선만). [key] 가 바뀌면 처음부터. */
@Composable
internal fun HoldLine(key: Any, ms: Long, still: Boolean, ink: RoomInk, modifier: Modifier = Modifier) {
    val fill = remember(key) { Animatable(0f) }
    LaunchedEffect(key, ms, still) {
        if (!still && ms > 0) fill.animateTo(1f, tween(ms.toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    Box(modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(ink.hair)) {
        Box(Modifier.fillMaxWidth(fill.value).height(2.dp).background(ink.gilt))
    }
}

/**
 * 편지 한 통의 문장 말풍선. [active] 면 떠오른 카드 (21sp 원문, 뜻 · 낱말 따라가기 · 기능어 점선, 번역 13.5sp 진한 갈색 80% → 다 들으면 100%),
 * 아니면 17sp 로 조용히 (번역은 그대로 읽힌다). 누르면 [onTap] (다시 듣기). 길게 누르면 [onLong] (편지 카드).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomBubble(
    learn: String, learnLang: Lang, read: String?, readLang: Lang, readAlpha: Float,
    active: Boolean, heard: Boolean, words: List<IntRange>, karaoke: State<Karaoke>,
    il: IlLine?, dotted: List<IntRange>, grouped: List<IntRange>, marks: List<IntRange>,
    onWordAt: ((Int) -> Boolean)?, onTap: () -> Unit, tapLabel: String, onLong: (() -> Unit)?,
    saved: Boolean, onSave: (() -> Unit)?, signature: String?, orig: (@Composable () -> Unit)? = null,
    hold: (@Composable () -> Unit)? = null,
) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    val big = Type.target.copy(fontSize = if (active) 21.sp else 17.sp, lineHeight = if (active) 27.sp else 24.sp).of(learnLang)
    val cardLabel = stringResource(R.string.letter_card_send)
    Column(
        Modifier.fillMaxWidth(0.94f)
            .then(if (active) Modifier.shadow(14.dp, BubbleShape, ambientColor = Color(0x553C280A), spotColor = Color(0x593C280A)) else Modifier)
            .clip(BubbleShape).background(ink.card)
            .then(if (active) Modifier.border(1.dp, ink.hair, BubbleShape) else Modifier)
            .then(if (onLong != null) Modifier.longPressCard(cardLabel, onLong) else Modifier)
            .pressable(haptic = false, onClick = onTap).semantics { onClick(label = tapLabel) { onTap(); true } }
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val k = if (active) karaoke.value else Karaoke.DONE
                if (active && il != null && il.cells.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    il.cells.forEach { c ->
                        val us = c.units.mapNotNull { il.units.getOrNull(it) }
                        val gloss = us.mapNotNull { it.gloss }.filter { it.isNotEmpty() }.joinToString(" ")
                        Column {
                            KaraokeText(learn.substring(c.range.first, c.range.last + 1), c.range.first, words, k, big, ink,
                                dotted = dotted + marks, grouped = grouped, onTap = onWordAt)
                            if (gloss.isNotEmpty()) Text(gloss, style = Type.small.of(il.gloss).copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = ink.gloss,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
                        }
                    }
                }
                else if (active) KaraokeText(learn, 0, words, k, big, ink, dotted = dotted + marks, grouped = grouped, onTap = onWordAt)
                else Text(learn, style = big, color = ink.ink.copy(alpha = 0.62f))
                if (read != null) Text(read, style = Type.base.of(readLang).copy(fontSize = if (active) 13.5.sp else 12.5.sp, lineHeight = if (active) 21.sp else 19.sp),
                    color = (if (active) ink.trans else ink.transRead).copy(alpha = ((if (active && !heard) 0.8f else 1f) * readAlpha).coerceIn(0.35f, 1f)))
                orig?.invoke()
                if (signature != null) Text(signature, style = Type.signature, color = ink.soft, modifier = Modifier.align(Alignment.End))
            }
            val saveLabel = stringResource(if (saved) R.string.quote_saved else R.string.quote_save)
            if (onSave != null) Box(Modifier.size(40.dp, 48.dp).semantics { contentDescription = saveLabel }
                .pressable(haptic = false, onClick = onSave), contentAlignment = Alignment.TopCenter) { Bookmark(saved) }
            else if (saved) Box(Modifier.size(40.dp, 24.dp), contentAlignment = Alignment.TopCenter) { Bookmark(true) }
        }
        hold?.invoke()
    }
}

/** 쓰는 중: 점 셋 (움직임 줄이기면 멈춰 있음). */
@Composable
internal fun TypingDots(still: Boolean, label: String) {
    val ink = RoomInk(Ink.palette)
    val t = if (still) 0f else androidx.compose.animation.core.rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dot",
    ).value
    Row(Modifier.clip(BubbleShape).background(ink.card).semantics { contentDescription = label }.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { k ->
            val ph = ((t - k * 0.125f) % 1f + 1f) % 1f
            val up = if (ph < 0.3f) kotlin.math.sin(ph / 0.3f * Math.PI).toFloat() else 0f
            Box(Modifier.padding(top = (3 * (1 - up)).dp).size(6.dp).background(ink.faint.copy(alpha = 0.5f + 0.5f * up), CircleShape))
        }
    }
}

/** 아래 플레이어: n / N · 상태 한 줄 | 앞 · 재생/멈춤 (쉼 동안은 다음 문장으로) · 다음 | 빠르기 칩 1× · 0.8×. */
@Composable
internal fun RoomPlayer(
    count: String, state: String, playing: Boolean, holding: Boolean,
    onPrev: () -> Unit, onMain: () -> Unit, onNext: () -> Unit,
    slow: Boolean, normalLabel: String, onSlow: (Boolean) -> Unit,
) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ink.card).border(1.dp, ink.hair, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(count, style = Type.heading.copy(fontFamily = Faces.display, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 19.sp), color = ink.ink, maxLines = 1)
            Text(state, style = Type.small.ui().copy(fontSize = 11.5.sp, lineHeight = 14.sp), color = ink.soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val prev = stringResource(R.string.room_prev_line); val next = stringResource(R.string.room_next_line)
            Box(Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = prev }.pressable(haptic = false, onClick = onPrev), contentAlignment = Alignment.Center) { SkipGlyph(ink.ink, back = true) }
            val main = stringResource(if (holding) R.string.room_skip_hold else if (playing) R.string.narration_pause else R.string.narration_play)
            Box(Modifier.size(52.dp).clip(CircleShape).background(ink.ink).semantics { contentDescription = main }.pressable(onClick = onMain), contentAlignment = Alignment.Center) {
                PlayGlyph(ink.card, pause = playing && !holding, size = 18.dp)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).semantics { contentDescription = next }.pressable(haptic = false, onClick = onNext), contentAlignment = Alignment.Center) { SkipGlyph(ink.ink, back = false) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { RoomSpeedChip(slow, normalLabel, onSlow) }
    }
}

/** 빠르기 칩 “1× · 0.8×” (고른 쪽 금빛). */
@Composable
private fun RoomSpeedChip(slow: Boolean, normalLabel: String, onSlow: (Boolean) -> Unit) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    val group = stringResource(R.string.room_speed_group)
    Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, ink.hair, RoundedCornerShape(50)).semantics { contentDescription = group }) {
        listOf(false to normalLabel, true to SLOW_TEXT).forEach { (v, label) ->
            val on = slow == v
            Box(Modifier.heightIn(min = 48.dp).background(if (on) ink.gilt.copy(alpha = 0.24f) else Color.Transparent)
                .semantics { contentDescription = "$group $label"; selected = on }
                .pressable(haptic = false, role = Role.RadioButton) { onSlow(v) }.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                Text(label, style = Type.small.copy(fontFamily = Faces.display, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) ink.ink else ink.soft, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 앞 · 다음 표시: 세로 막대 + 세모. */
@Composable
private fun SkipGlyph(c: Color, back: Boolean) = Canvas(Modifier.size(18.dp)) {
    val w = size.width
    val bar = if (back) w * 0.2f else w * 0.8f
    drawLine(c, Offset(bar, w * 0.18f), Offset(bar, w * 0.82f), 1.8.dp.toPx())
    val path = Path().apply {
        if (back) { moveTo(w * 0.85f, w * 0.18f); lineTo(w * 0.32f, w * 0.5f); lineTo(w * 0.85f, w * 0.82f) }
        else { moveTo(w * 0.15f, w * 0.18f); lineTo(w * 0.68f, w * 0.5f); lineTo(w * 0.15f, w * 0.82f) }
        close()
    }
    drawPath(path, c)
}

/** 멈춘 띠: “2번째 문장을 다시 듣는 중 · [이어서 듣기 · 3번째부터]”. */
@Composable
internal fun RoomPill(text: String, action: String?, onAction: () -> Unit) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(ink.deep).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = Type.small.ui().copy(fontSize = 12.5.sp), color = ink.onDeep, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (action != null) Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(ink.onDeep).pressable(onClick = onAction)
            .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(action, style = Type.small.ui().copy(fontSize = 12.sp), color = ink.deep, maxLines = 1, softWrap = false)
        }
    }
}

/** 내 차례 (받는 사람이 하는 일): 금빛 큰 단추 “테오로서 빈센트에게 50 francs 보내기 · 보내기 →”. */
@Composable
internal fun RoomAsk(label: String, go: String, onClick: () -> Unit) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(16.dp)).background(ink.giltFill)
            .semantics(mergeDescendants = true) { contentDescription = label }.pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.body.ui().copy(fontSize = 14.sp), color = ink.onGilt, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(go, style = Type.body.ui().copy(fontSize = 14.sp), color = ink.onGilt, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        ArrowGlyph(ink.onGilt)
    }
}

/** 순간 카드 아래 [who] 한 줄 (작게). */
@Composable
private fun Who(text: String, end: Boolean = false) {
    val ink = RoomInk(Ink.palette)
    Text(text, style = Type.small.ui().copy(fontSize = 10.5.sp, letterSpacing = 0.06.sp), color = ink.soft, maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = if (end) androidx.compose.ui.text.style.TextAlign.End else null)
}

/** 보낸 사람 이름에 조사 (한국어면 이/가). */
@Composable
private fun subject(name: String) = if (uiLang() == Lang.KO) josa(name, "이", "가") else name

/**
 * 편지 방의 순간 (누가 하는지에 따라): 보내는 사람이 보내고 알리는 것 (사진 · 스케치 · 위치 · 날씨 · 알림 · 보낸 사람이 낸 돈) 은 왼쪽,
 * 내가 (받는 사람이) 한 것 ([Moment.Transfer.byReader]) 은 오른쪽 어두운 말풍선. 누르면 [onOpen] (크게 보기 · 지도 · 곡).
 */
@Composable
internal fun RoomMoment(s: AppState, work: Work, recipient: String, m: Moment, onOpen: () -> Unit, hold: (@Composable () -> Unit)? = null) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    val lang = uiLang()
    val sender = work.name[lang]
    when (m) {
        is Moment.Photo -> Column(Modifier.fillMaxWidth(0.8f).widthIn(max = 320.dp)) {
            Who(stringResource(if (m.sketch) R.string.room_sketch_sent else R.string.room_photo_sent, subject(sender)))
            val path = (if (m.sketch) "sketches/" else "plates/") + m.image
            val bmp = rememberPicture(path).bmp
            Box(Modifier.fillMaxWidth().aspectRatio(if (m.sketch) 1.3f else 1.25f).clip(BubbleShape).background(p.hide)
                .semantics { contentDescription = m.caption[lang] }.pressable(onClick = onOpen)) {
                if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxWidth().aspectRatio(if (m.sketch) 1.3f else 1.25f),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = if (m.sketch) Alignment.TopCenter else Alignment.Center)
            }
            Text(m.caption[lang], style = Type.small.ui().copy(fontSize = 11.5.sp), color = ink.soft, modifier = Modifier.padding(top = 6.dp), maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            hold?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
        }
        is Moment.Location -> Column(Modifier.fillMaxWidth(0.8f).widthIn(min = 230.dp, max = 320.dp)) {
            Column(Modifier.fillMaxWidth().clip(BubbleShape).background(ink.card).border(1.dp, ink.hair, BubbleShape).pressable(onClick = onOpen)) {
                MapStripe(ink, p)
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.room_place_shared, subject(sender)), style = Type.small.ui().copy(fontSize = 13.sp), color = ink.ink, maxLines = 1)
                    Text(stringResource(R.string.room_place_line, listOf(m.title[lang], m.address).filter { it.isNotBlank() }.joinToString(" · ")),
                        style = Type.small.ui().copy(fontSize = 11.5.sp, lineHeight = 15.sp), color = ink.soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            hold?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
        }
        is Moment.Transfer -> if (m.byReader) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.widthIn(min = 220.dp, max = 300.dp).clip(MineShape).background(ink.deep).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.room_sent_by_me, recipient, sender), style = Type.small.ui().copy(fontSize = 11.sp, letterSpacing = 0.08.sp), color = ink.onDeepSoft, maxLines = 1)
                Text(m.amount, style = Type.title.copy(fontFamily = Faces.display, fontSize = 30.sp, lineHeight = 33.sp), color = ink.onDeep, maxLines = 1)
                Text(m.memo[lang], style = Type.small.ui().copy(fontSize = 12.sp), color = ink.onDeepSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.room_sent_reply, subject(sender)), style = Type.small.ui().copy(fontSize = 12.sp), color = ink.onDeepGilt,
                    maxLines = 1, modifier = Modifier.padding(top = 8.dp))
            }
        } else Column(Modifier.widthIn(min = 220.dp, max = 300.dp)) {
            Column(Modifier.clip(BubbleShape).background(ink.card).border(1.dp, ink.hair, BubbleShape).padding(horizontal = 16.dp, vertical = 14.dp)) {
                val k = if (m.outgoing) stringResource(R.string.room_paid_by, subject(sender)) + " · " + m.label[lang] else m.label[lang]
                Text(k, style = Type.small.ui().copy(fontSize = 11.sp, letterSpacing = 0.06.sp), color = ink.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.amount, style = Type.title.copy(fontFamily = Faces.display, fontSize = 30.sp, lineHeight = 33.sp), color = ink.ink, maxLines = 1)
                Text(m.memo[lang], style = Type.small.ui().copy(fontSize = 12.sp), color = ink.soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            hold?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
        }
        is Moment.Music -> Column(Modifier.fillMaxWidth()) {
            MomentCard(s, work, m, onMap = {}, onPhoto = {}, onMusic = { onOpen() })
            hold?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
        }
        else -> Column(Modifier.fillMaxWidth()) {
            MomentCard(s, work, m, onMap = {}, onPhoto = {})
            hold?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
        }
    }
}

/** 위치 카드 위의 작은 지도 무늬 (비스듬한 결 + 금빛 핀). */
@Composable
private fun MapStripe(ink: RoomInk, p: Palette) = Canvas(Modifier.fillMaxWidth().height(92.dp).background(p.mapPaper)) {
    val step = 10.dp.toPx()
    var x = -size.height
    var k = 0
    while (x < size.width + size.height) {
        if (k % 2 == 0) drawLine(p.mapRoad.copy(alpha = 0.6f), Offset(x, size.height), Offset(x + size.height * 0.7f, 0f), step)
        x += step; k++
    }
    val c = Offset(size.width / 2, size.height * 0.45f)
    drawCircle(ink.gilt.copy(alpha = 0.25f), 10.dp.toPx(), c)
    drawCircle(ink.gilt, 6.dp.toPx(), c)
}

/** 큐레이터 노트 (말풍선이 아닌 미술관 카드): 머리말 · 노트 · 그 그림 작게 · “그림 자세히 보기”. */
@Composable
internal fun RoomNoteCard(text: String, lang: Lang, plate: io.github.graviton94.todayletters.core.Plate?, artist: String, onPlate: () -> Unit) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.card).border(1.dp, ink.hair, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.note_title), style = Type.small.ui().copy(fontSize = 11.sp, letterSpacing = 0.14.sp), color = ink.gilt)
        Text(text, style = Type.body.of(lang).copy(fontSize = 13.5.sp, lineHeight = 23.sp), color = ink.trans)
        val img = plate?.image.orEmpty()
        if (plate != null && img.isNotBlank()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.hair))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable(onClick = onPlate), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlateImage(img, Modifier.size(56.dp, 42.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(plate.title[lang], style = Type.small.ui().copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = ink.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val year = Regex("\\d{4}").find(plate.date)?.value
                    Text(listOfNotNull(artist.takeIf { it.isNotBlank() }, year, stringResource(R.string.room_note_more)).joinToString(" · "),
                        style = Type.small.ui().copy(fontSize = 11.5.sp), color = ink.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 낱말 · 기능어 · 표현 카드 한 장의 내용. [word] 는 그 메시지의 띄어 쓴 낱말 번호 (이어 듣기 자리), [lex] 는 사전 열쇠 (카드에 담기 · 자세히). */
internal data class RoomPick(
    val msg: Int, val word: Int, val surface: String, val tag: String, val head: String, val short: String, val note: String,
    val lex: String?, val function: Boolean, val token: Int,
)

/** 아래에서 올라오는 작은 카드: 꼬리표 · 낱말 (또는 묶음) · 짧은 뜻 · 노트 · ‘“du” 부터 이어 듣기’ · 카드에 담기 · 자세히 · 닫기. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomWordCard(
    pick: RoomPick, lang: Lang, glossLang: Lang, canResume: Boolean, inCards: Boolean, canAdd: Boolean,
    onResume: () -> Unit, onAdd: () -> Unit, onMore: (() -> Unit)?, onClose: () -> Unit,
) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(ink.card)
            .border(1.dp, ink.hair, RoundedCornerShape(22.dp)).pressable(haptic = false) {}.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(pick.tag, style = Type.small.ui().copy(fontSize = 10.5.sp, letterSpacing = 0.12.sp), color = ink.gloss, maxLines = 1)
        Text(pick.head, style = Type.title.copy(fontFamily = Faces.display, fontSize = 28.sp, lineHeight = 31.sp).of(lang), color = ink.ink)
        if (pick.short.isNotEmpty()) Text(pick.short, style = Type.body.of(glossLang).copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = ink.ink)
        if (pick.note.isNotEmpty()) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(ink.hair))
            Text(pick.note, style = Type.small.ui().copy(fontSize = 13.sp, lineHeight = 22.sp), color = ink.trans, modifier = Modifier.padding(top = 8.dp))
        }
        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canResume) RoomCardButton(stringResource(R.string.room_from_word, pick.surface), primary = true, onClick = onResume)
            if (inCards) Capsule(stringResource(R.string.gloss_in_cards), CapsuleKind.DONE)
            else if (canAdd) RoomCardButton(stringResource(R.string.gloss_add), onClick = onAdd)
            if (onMore != null) RoomCardButton(stringResource(R.string.room_card_more), onClick = onMore)
            RoomCardButton(stringResource(R.string.close), onClick = onClose)
        }
    }
}

@Composable
private fun RoomCardButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val p = Ink.palette
    val ink = remember(p) { RoomInk(p) }
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50))
            .then(if (primary) Modifier.background(ink.ink) else Modifier.border(1.dp, ink.hair, RoundedCornerShape(50)))
            .pressable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.small.ui().copy(fontSize = 12.5.sp), color = if (primary) ink.card else ink.ink, maxLines = 1) }
}
