package io.github.graviton94.todayletters.ui

import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Interlinear
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Reading
import io.github.graviton94.todayletters.core.Token
import io.github.graviton94.todayletters.core.Vocab
import io.github.graviton94.todayletters.data.learnVocab
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 낱말 아래 뜻 한 메시지: 토큰 · 글자 자리 · 칸 ([Interlinear.units]) · 화면 묶음 ([Interlinear.cells]) · 뜻 언어. */
data class IlLine(val tokens: List<Token>, val ranges: List<IntRange>, val units: List<Interlinear.Unit>, val cells: List<Interlinear.Cell>, val gloss: Lang)

/**
 * 편지 한 통의 낱말 아래 뜻 (메시지마다, 토큰이 없는 메시지는 null). 뜻은 읽는 말로 ([glossLangFor]), 내 것이 된 낱말 ([AppState.mine]) 은 빼고,
 * 기능어는 도움 등급이 A2 이하일 때만. 그 메시지의 짧은 표현 카드 · 작품의 닻 표현은 한 칸.
 */
fun interlinearLines(s: AppState, series: String, letter: Letter, tokens: List<List<Token>?>): List<IlLine?> {
    val work = s.work(series)
    val view = s.room(series)
    val learn = work.learnVocab
    val gl = s.glossLangFor(series)
    val mine = s.mine(view.learn)
    val function = s.support(series).band <= Reading.Band.A2
    val anchors = s.anchors(series)
    val known = s.anchorKnownFn(series)
    return letter.messages.mapIndexed { i, m ->
        val ts = tokens.getOrNull(i) ?: return@mapIndexed null
        val text = m.text[view.learn]
        // 한국어 글은 낱말 안에 줄바꿈 막기 글자 (Breaks.keepAll) 가 끼어 있다: 그 글자를 뺀 글에서 자리를 찾고 원래 글의 자리로 옮긴다
        val at = text.indices.filter { text[it] != Breaks.JOIN }
        val ranges = Vocab.align(Breaks.plain(text), ts).map { r -> if (r.isEmpty()) r else at[r.first]..at[r.last] }
        val phrases = letter.phrases.filter { it.msg == i }.map { it.text[learn] to it.text[gl] }
        val units = Interlinear.units(ts, { work.lexicon?.get(it) }, gl, { it in mine }, function, phrases, anchors, known)
        IlLine(ts, ranges, units, Interlinear.cells(text, ranges, units), gl)
    }
}

/** 편지 방 맨 위: ‘낱말 아래 뜻’ 켜기 · 끄기 (금빛 점 = 켜짐), 예열을 했으면 왼쪽에 ‘아까 본 말’ (금빛 점선 보기). */
@Composable
fun InterlinearToggle(on: Boolean, seenLegend: Boolean, onToggle: () -> Unit) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (seenLegend) Text(stringResource(R.string.il_seen), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.giltText,
            modifier = Modifier.dotted(p.giltText, 1.4f).padding(bottom = 3.dp))
        Box(Modifier.weight(1f))
        Row(
            Modifier.heightIn(min = 48.dp).toggleable(on, role = Role.Switch) { onToggle() }.padding(horizontal = Tokens.Space.s1),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.il_toggle), style = Type.small.ui(), color = if (on) p.giltText else p.inkSoft)
            Box(Modifier.size(10.dp).then(if (on) Modifier.background(p.giltText, CircleShape) else Modifier.border(1.dp, p.inkSoft, CircleShape)))
        }
    }
}

/** 아래 끝에 점선 (금빛 = 아까 본 말, 옅은 잉크 = 뜻 없음). */
private fun Modifier.dotted(c: Color, width: Float, dash: Float = 1.6f, gap: Float = 2.4f) = drawBehind {
    val y = size.height - width.dp.toPx() / 2
    drawLine(c, Offset(0f, y), Offset(size.width, y), width.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.dp.toPx(), gap.dp.toPx())))
}

private fun Modifier.solid(c: Color, width: Float) = drawBehind {
    val y = size.height - width.dp.toPx() / 2
    drawLine(c, Offset(0f, y), Offset(size.width, y), width.dp.toPx())
}

private fun IntRange.meets(o: IntRange?) = o != null && !o.isEmpty() && !isEmpty() && first <= o.last && o.first <= last

/**
 * 낱말 아래 뜻으로 쓴 원문 (편지지 한 조각의 배우는 말 줄): 낱말 묶음마다 위에 원문, 아래에 짧은 뜻 (없으면 빈 줄로 높이를 맞춤).
 * [sub] 가 있으면 그 글자 범위 (문장 짝 하나) 의 묶음만. 새로 온 조각은 잉크 번짐 대신 천천히 나타난다 ([go] 뒤 [onDone]).
 *  - 오늘의 카드 낱말: 갈색 끊은 밑줄, 누르면 낱말 카드 ([onWord]). 그 밖의 낱말을 누르면 눌러서 뜻 보기 ([onTap], 토큰 번호).
 *  - 덩어리 (표현 · 닻): 금빛 밑줄과 금빛 뜻. 익힌 닻은 뜻 없이 금빛 바탕.
 *  - 예열에서 본 말 ([seen]): 금빛 점선. 뜻이 사전에 없으면 옅은 점선만 (지어내지 않음).
 *  - 낭독 중인 자리 ([spoken]): 그 묶음의 뜻이 금빛으로 밝아지고 원문에 옅은 바탕.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IlText(
    text: String, line: IlLine, sub: IntRange?, lang: Lang, animate: Boolean, go: Boolean, lineMs: Int, onDone: () -> Unit,
    marks: List<Pair<IntRange, Int>>, onWord: (Int) -> Unit, spoken: IntRange?, onTap: ((Int) -> Unit)?,
    picked: IntRange?, onPickedAt: (GlossAnchor?) -> Unit, seen: Set<String>,
) {
    val p = Ink.palette
    val cells = remember(line, sub) { line.cells.filter { sub == null || it.range.first in sub } }
    val alpha = remember(text, sub) { Animatable(if (animate) 0f else 1f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(text, sub, go) {
        if (!go) return@LaunchedEffect
        if (alpha.value < 1f) alpha.animateTo(1f, tween((lineMs * (cells.size / 5 + 1)).coerceIn(300, 4000), easing = LinearEasing))
        done()
    }
    val markInk = Color(0xFF8F6A27)
    val word = Type.target.of(lang)
    val small = Type.small.of(line.gloss).copy(fontSize = Tokens.Text.caps, lineHeight = Tokens.Text.caps * 1.25f)
    val pickedHere = picked?.takeIf { pk -> cells.any { it.range.meets(pk) } }
    val tap = remember { MutableInteractionSource() }
    FlowRow(
        Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha.value }.glossSpot(pickedHere, onPickedAt),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        cells.forEach { c ->
            val us = c.units.mapNotNull { line.units.getOrNull(it) }
            val first = us.firstOrNull() ?: return@forEach
            val gloss = us.mapNotNull { it.gloss }.filter { it.isNotEmpty() }.joinToString(" ")
            val chunk = us.size == 1 && first.kind != Interlinear.Kind.WORD
            val anchorKnown = chunk && first.kind == Interlinear.Kind.ANCHOR && first.known
            val missing = us.any { it.gloss == "" }
            val mark = marks.firstOrNull { (r, _) -> c.range.meets(r) }?.second
            val isSeen = us.any { u ->
                (u.kind == Interlinear.Kind.ANCHOR && "a${u.anchorId}" in seen) ||
                    (u.from..u.to).any { j -> line.tokens.getOrNull(j)?.let { "w:${it.key}" in seen } == true }
            }
            val lit = c.range.meets(spoken)
            val on = c.range.meets(pickedHere)
            val bg = when {
                on -> Color(0x55D2A955)
                anchorKnown -> Color(0x33D9AE55)
                lit -> markInk.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
            val under = when {
                chunk -> Modifier.solid(p.giltText, 1.5f)
                isSeen -> Modifier.dotted(p.giltText, 1.6f)
                mark != null -> Modifier.dotted(markInk, 1.2f, 2.5f, 2.5f)
                missing -> Modifier.dotted(p.slipInk.copy(alpha = 0.3f), 0.8f, 1.5f, 2.5f)
                else -> Modifier
            }
            Column(
                Modifier.clickable(interactionSource = tap, indication = null, enabled = mark != null || onTap != null) {
                    if (mark != null) onWord(mark) else onTap?.invoke(first.from)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text.substring(c.range.first, c.range.last + 1), style = word, color = p.slipInk,
                    modifier = Modifier.background(bg).then(under).padding(bottom = 2.dp))
                Text(gloss.ifEmpty { " " }, style = small, maxLines = 1, softWrap = false,
                    color = if (lit || (chunk && !anchorKnown)) p.giltText else glossInk(p))
            }
        }
    }
}

/** 낱말 아래 뜻의 색: 문장 번역(잉크색)과 갈래가 다른 청회색 (시안 glosscolor A, 2026-10-20). 금빛은 덩어리 · 닻 · 읽는 낱말에 씀. */
private fun glossInk(p: io.github.graviton94.todayletters.design.Palette) =
    if (p.slip.luminance() < 0.5f) androidx.compose.ui.graphics.Color(0xFF8FA6B8) else androidx.compose.ui.graphics.Color(0xFF4F6679)
