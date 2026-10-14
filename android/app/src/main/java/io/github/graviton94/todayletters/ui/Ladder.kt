package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.Ladder
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.data.recipientOf
import io.github.graviton94.todayletters.data.registerOf
import io.github.graviton94.todayletters.data.grammarOf
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.ReplyTurn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay

/**
 * 답장 사다리 (core 의 [Ladder]): 받는 사람이 되어 빈센트(모차르트)의 문장에 대답한다. 턴이 여럿이면 차례로 (“턴 1 / 2”),
 * 턴마다 위에는 그 턴이 집어 온 문장 (메신저의 ‘답장(인용)’), 그 위에 오늘의 문법 한 줄 (점선), 아래에는 내 답장 말풍선.
 * 빈칸은 언어 등급만큼, 그 턴의 문법 덩어리 ([ReplyTurn.focus]) 먼저: 고르기 (덩어리면 같은 낱말의 다른 꼴이 미끼) · 둘 고르기 · 직접 쓰기 (틀리면 한 글자씩 힌트).
 * 한국어를 배우면 해요체로 쓰고, 맞힌 뒤 말투 노트 (반말 · 높임 · 실제 사이) 가 펼쳐진다.
 * 다 채우면 [onDone] (다음 턴, 마지막 턴이면 보내기 → 답장 걸음 끝). 턴마다 복습 카드가 된다 ([AppState.replyTurnDone]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyLadder(s: AppState, r: Route.Play, help: Ladder.Help, turn: Int, turns: Int, onDone: () -> Unit, onClose: () -> Unit) {
    val p = Ink.palette
    val id = r.room.series
    val (_, letter) = s.letterOf(r.room)
    val work = s.work(id)
    val view = s.room(id)
    val t = letter.replies.getOrNull(turn)
    val (line, tri) = Exercises.replyFor(letter, view.learn, turn)
    // 비교 · 빈칸은 이음표 없는 글자로 (낱말 묶음마다 따로 그리니 한국어도 어절 가운데서 줄이 바뀌지 않는다)
    val sentence = remember(line) { Breaks.plain(line).trim() }
    val focus = remember(t, view.learn) { t?.focus?.get(view.learn).orEmpty().map { Breaks.plain(it) } }
    val score = remember(letter.id, view.learn) { s.replyScore(id, letter) }
    val holes = remember(letter.id, view.learn, help, turn) { Ladder.holes(sentence, focus, Ladder.blanks(help), score) }
    val answers = holes.map { sentence.substring(it) }
    val units = remember(sentence, holes) { Ladder.layout(sentence, holes) }
    val choices = remember(letter.id, view.learn, help, turn) {
        val spans = Ladder.spans(sentence)
        answers.mapIndexed { k, a ->
            val fromFocus = focus.any { f -> Ladder.find(sentence, f) == holes[k] }
            val others = answers.filterIndexed { j, _ -> j != k } +
                spans.filter { sp -> !sp.isEmpty() && holes.none { h -> h.first <= sp.last && sp.first <= h.last } }.map { sentence.substring(it) }.filter { score(it) > 0 }
            val (near, pool) = s.replyDecoys(id, letter, a, others, focus = fromFocus)
            Ladder.chips(a, if (fromFocus || help == Ladder.Help.CHOOSE_TWO) near else emptyList(), pool, letter.id.hashCode() + k + turn * 7)
        }
    }
    var filled by remember(letter.id, view.learn, help, turn) { mutableStateOf(List(holes.size) { false }) }
    var typed by remember(letter.id, view.learn, help, turn) { mutableStateOf("") }
    var misses by remember(letter.id, view.learn, help, turn) { mutableStateOf(0) }
    var wrong by remember { mutableStateOf<String?>(null) }
    var noteOpen by remember(turn) { mutableStateOf(true) }
    LaunchedEffect(wrong) { if (wrong != null) { delay(900); wrong = null } }
    val at = filled.indexOfFirst { !it }
    val done = holes.isNotEmpty() && at < 0
    val recipient = work.recipientOf(letter)[uiLang()]

    fun accept() { filled = filled.toMutableList().also { it[at] = true }; typed = ""; misses = 0; s.cue(true) }
    fun miss(x: String) { wrong = x; misses++; s.cue(false) }

    // 직접 쓰기에서 키보드가 올라오면: 화면이 키보드 위로 줄고 (imePadding), 아래 띠는 ‘확인’ 하나만 남긴다
    val ime = WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f).imePadding()) {
        // 턴이 여럿이면 막대는 턴마다 한 칸 (“턴 1 / 2”), 하나면 빈칸마다
        if (turns > 1) PracticeHeader(turn, turns, onClose = onClose, count = stringResource(R.string.ladder_turn, turn + 1, turns))
        else PracticeHeader(filled.count { it }, holes.size.coerceAtLeast(1), onClose = onClose, count = "${filled.count { it }} / ${holes.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            PracticeTitle("REPLY", stringResource(if (help == Ladder.Help.WRITE) R.string.ladder_write_mode else R.string.ladder_choose_mode))
            if (turn == 0) Text(stringResource(R.string.ladder_prompt, recipient), style = Type.body.ui(), color = p.inkSoft)
            if (t != null) GrammarNote(s, id, t, view.learn)
            // 집어 온 문장: 보낸 사람 이름 · 그 문장 (배우는 말, 길면 줄임) · 읽는 말 한 줄
            t?.quote?.let { letter.messages.getOrNull(it) }?.let { m ->
                Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Box(Modifier.width(2.dp).heightIn(min = 14.dp).background(p.gilt))
                        Text(work.sender, style = Type.capsSm.ui(), color = p.giltText)
                    }
                    fun short(x: String) = Breaks.plain(x).let { if (it.length > 140) it.take(138).trimEnd() + "…" else it }
                    Text(short(m.text[view.learn]), style = Type.body.of(view.learn), color = p.ink)
                    if (view.read != view.learn) Text(short(m.text[view.read]), style = Type.small.of(view.read), color = p.inkSoft)
                }
            }
            // 답장의 뜻 (읽는 말)
            Text(quoted(tri[view.read], view.read), style = Type.small.of(view.read), color = p.inkSoft)
            // 내 답장 말풍선: 채운 빈칸은 글자로, 지금 빈칸은 밑줄 (직접 쓰기면 쓰는 칸). 띄어 쓴 묶음마다 줄이 바뀐다
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                FlowRow(
                    Modifier.background(p.fill).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val style = Type.heading.of(view.learn)
                    units.forEach { unit ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            unit.forEach { piece ->
                                val k = piece.hole
                                when {
                                    k < 0 -> Text(piece.text, style = style, color = p.onFill)
                                    filled[k] -> Text(answers[k], style = style.copy(textDecoration = TextDecoration.Underline), color = p.onFill)
                                    k == at && help == Ladder.Help.WRITE -> BasicTextField(
                                        typed, { typed = it.take(40) }, singleLine = true,
                                        textStyle = style.copy(color = p.onFill),
                                        cursorBrush = SolidColor(p.onFill),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                        keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) { if (Ladder.right(answers[k], typed)) accept() else miss(typed) } }),
                                        modifier = Modifier.width((answers[k].length * 14 + 24).dp).border(1.dp, p.onFill.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                                    )
                                    else -> Text("＿".repeat(answers[k].length.coerceIn(3, 6)), style = style,
                                        color = if (k == at) p.onFill else p.onFill.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
            if (!done && at >= 0) when (help) {
                Ladder.Help.WRITE -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(stringResource(R.string.ladder_write), style = Type.small.ui(), color = p.inkSoft)
                    if (misses > 0) Text(stringResource(R.string.ladder_hint, Ladder.hint(answers[at], misses)), style = Type.small.of(view.learn), color = p.giltText)
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(stringResource(R.string.ladder_choose), style = Type.small.ui(), color = p.inkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        choices.getOrNull(at).orEmpty().forEach { c ->
                            val bad = wrong == c
                            Box(
                                Modifier.heightIn(min = 44.dp).border(1.dp, if (bad) p.wrong else p.line)
                                    .pressable(role = Role.Button) { if (Ladder.right(answers[at], c)) accept() else miss(c) }
                                    .padding(horizontal = Tokens.Space.s4),
                                contentAlignment = Alignment.Center,
                            ) { Text(c, style = Type.body.of(view.learn), color = if (bad) p.wrong else p.ink) }
                        }
                    }
                }
            }
            when {
                done -> Text(stringResource(R.string.play_right, work.name[uiLang()]), style = Type.small.ui(), color = p.correct)
                wrong != null -> Text(stringResource(R.string.ladder_wrong), style = Type.small.ui(), color = p.wrong)
            }
            // 한국어를 배우면: 맞힌 뒤 그 문장의 말투 노트 (반말 · 높임 · 실제 사이)
            if (done && view.learn == Lang.KO && t != null && t.styled) StyleNote(s, id, letter, t, noteOpen) { noteOpen = !noteOpen }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            when {
                done -> Primary(stringResource(if (turn < turns - 1) R.string.ladder_next_turn else R.string.play_send)) { onDone() }
                help == Ladder.Help.WRITE -> {
                    Primary(stringResource(R.string.ladder_check), enabled = typed.isNotBlank()) { if (Ladder.right(answers[at], typed)) accept() else miss(typed) }
                    if (!ime) {
                        Secondary(stringResource(R.string.play_show)) { filled = List(holes.size) { true } }
                        SkipLink { s.skipStep(r.room, ReplyMode.CONSTELLATION) }
                    }
                }
                else -> {
                    Secondary(stringResource(R.string.play_show)) { filled = List(holes.size) { true } }
                    SkipLink { s.skipStep(r.room, ReplyMode.CONSTELLATION) }
                }
            }
        }
    }
}

/** 점선 테두리 (오늘의 문법 줄). */
private fun Modifier.dashedRound(c: androidx.compose.ui.graphics.Color) = drawBehind {
    drawRoundRect(c, cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
}

/**
 * 오늘의 문법 한 줄 (점선): “오늘의 문법 · 복합과거 — 지난 일을 말해요”. 이름 · 설명은 앱 글자 언어로, 그 턴의 문법 ([ReplyTurn.grammar]) 의
 * 배우는 말 칸 ([io.github.graviton94.todayletters.core.Grammar.line]). 문법이 없거나 그 말의 칸이 없으면 그리지 않는다.
 */
@Composable
internal fun GrammarNote(s: AppState, series: String, t: ReplyTurn, learn: Lang) {
    val g = remember(series, t.grammar, learn) { s.work(series).grammarOf(t.grammar)?.line(learn) } ?: return
    val p = Ink.palette
    val ui = uiLang()
    val label = stringResource(R.string.ladder_grammar)
    val name = g.name[ui]
    val hint = g.hint[ui]
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = p.giltText, fontWeight = FontWeight.SemiBold)) { append(label) }
        append("  ·  ")
        append(name)
        if (hint.isNotBlank()) withStyle(SpanStyle(color = p.inkSoft)) { append(" — $hint") }
    }
    Text(text, style = Type.small.ui(), color = p.ink,
        modifier = Modifier.fillMaxWidth().dashedRound(p.giltText).padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2))
}

/**
 * 말투 노트 (한국어를 배울 때, 맞힌 뒤): 같은 뜻의 반말 · 높임 한 줄씩과, 이 편지를 주고받는 두 사람의 실제 사이 한 줄 ([registerOf]).
 * 눌러서 접고 편다. 문장 소리는 없다 (앱의 소리는 녹음된 편지 · 낱말 낭독뿐이고 기기 TTS 는 쓰지 않는다).
 */
@Composable
private fun StyleNote(s: AppState, series: String, letter: Letter, t: ReplyTurn, open: Boolean, onToggle: () -> Unit) {
    val p = Ink.palette
    val relation = remember(series, letter.id) { s.work(series).registerOf(letter) }
    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s1)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable(role = Role.Button) { onToggle() }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.style_note), style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", style = Type.heading, color = p.giltText)
        }
        if (open) Column(Modifier.padding(bottom = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            listOf(R.string.style_low to t.koLow, R.string.style_high to t.koHigh).forEach { (label, line) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(label), style = Type.small.ui(), color = p.inkSoft)
                    Text(line, style = Type.body.of(Lang.KO), color = p.ink)
                }
            }
            relation?.get(uiLang())?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.small.ui(), color = p.giltText) }
        }
    }
}
