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
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay

/**
 * 답장 사다리 (core 의 [Ladder]): 받는 사람이 되어 빈센트(모차르트)의 한 문장에 대답한다.
 * 위에는 집어 온 그 문장 (메신저의 ‘답장(인용)’), 아래에는 내 답장 말풍선. 빈칸은 언어 등급만큼:
 * 고르기 (낱말 셋) · 둘 고르기 (같은 낱말의 다른 꼴이 미끼) · 직접 쓰기 (틀리면 한 글자씩 힌트).
 * 다 채우면 보내기 → 답장 걸음 끝 (별자리 잇기와 같은 [ReplyMode.CONSTELLATION]), 답장은 복습 카드가 된다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyLadder(s: AppState, r: Route.Play, help: Ladder.Help, onClose: () -> Unit) {
    val p = Ink.palette
    val id = r.room.series
    val (_, letter) = s.letterOf(r.room)
    val work = s.work(id)
    val view = s.room(id)
    val (sentence, tri) = Exercises.replyFor(letter, view.learn)
    val tokens = remember(letter.id, view.learn) { Ladder.tokens(sentence) }
    val holes = remember(letter.id, view.learn, help) { Ladder.pick(tokens, Ladder.blanks(help), s.replyScore(id, letter)) }
    val answers = holes.map { Ladder.core(tokens[it]) }
    val choices = remember(letter.id, view.learn, help) {
        answers.mapIndexed { k, a ->
            val others = holes.filter { it != holes[k] }.map { Ladder.core(tokens[it]) } +
                tokens.indices.filter { it !in holes }.map { Ladder.core(tokens[it]) }.filter { s.replyScore(id, letter)(it) > 0 }
            val (near, pool) = s.replyDecoys(id, letter, a, others)
            Ladder.chips(a, if (help == Ladder.Help.CHOOSE_TWO) near else emptyList(), pool, letter.id.hashCode() + k)
        }
    }
    var filled by remember(letter.id, view.learn, help) { mutableStateOf(List(holes.size) { false }) }
    var typed by remember(letter.id, view.learn, help) { mutableStateOf("") }
    var misses by remember(letter.id, view.learn, help) { mutableStateOf(0) }
    var wrong by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(wrong) { if (wrong != null) { delay(900); wrong = null } }
    val at = filled.indexOfFirst { !it }
    val done = holes.isNotEmpty() && at < 0
    val recipient = work.recipientOf(letter)[uiLang()]

    fun accept() { filled = filled.toMutableList().also { it[at] = true }; typed = ""; misses = 0; s.cue(true) }
    fun miss(x: String) { wrong = x; misses++; s.cue(false) }

    // 직접 쓰기에서 키보드가 올라오면: 화면이 키보드 위로 줄고 (imePadding), 아래 띠는 ‘확인’ 하나만 남긴다
    val ime = WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f).imePadding()) {
        PracticeHeader(filled.count { it }, holes.size.coerceAtLeast(1), onClose = onClose, count = "${filled.count { it }} / ${holes.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            PracticeTitle("REPLY", stringResource(if (help == Ladder.Help.WRITE) R.string.ladder_write_mode else R.string.ladder_choose_mode))
            Text(stringResource(R.string.ladder_prompt, recipient), style = Type.body.ui(), color = p.inkSoft)
            // 집어 온 문장: 보낸 사람 이름 · 그 문장 (배우는 말, 길면 줄임)
            letter.messages.getOrNull(letter.replyQuote)?.let { m ->
                Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s3),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Box(Modifier.width(2.dp).heightIn(min = 14.dp).background(p.gilt))
                        Text(work.sender, style = Type.capsSm.ui(), color = p.giltText)
                    }
                    val line = io.github.graviton94.todayletters.core.Breaks.plain(m.text[view.learn])
                    Text(if (line.length > 140) line.take(138).trimEnd() + "…" else line, style = Type.body.of(view.learn), color = p.ink)
                }
            }
            // 답장의 뜻 (읽는 말)
            Text(quoted(tri[view.read], view.read), style = Type.small.of(view.read), color = p.inkSoft)
            // 내 답장 말풍선: 채운 빈칸은 글자로, 지금 빈칸은 밑줄 (직접 쓰기면 쓰는 칸)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                FlowRow(
                    Modifier.background(p.fill).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tokens.forEachIndexed { i, tok ->
                        val k = holes.indexOf(i)
                        val (pre, post) = Ladder.around(tok)
                        when {
                            k < 0 -> Text(tok, style = Type.heading.of(view.learn), color = p.onFill)
                            filled[k] -> Text(pre + answers[k] + post, style = Type.heading.of(view.learn).copy(textDecoration = TextDecoration.Underline), color = p.onFill)
                            k == at && help == Ladder.Help.WRITE -> Row(verticalAlignment = Alignment.Bottom) {
                                if (pre.isNotEmpty()) Text(pre, style = Type.heading.of(view.learn), color = p.onFill)
                                BasicTextField(
                                    typed, { typed = it.take(40) }, singleLine = true,
                                    textStyle = Type.heading.of(view.learn).copy(color = p.onFill),
                                    cursorBrush = SolidColor(p.onFill),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                                    keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) { if (Ladder.right(answers[k], typed)) accept() else miss(typed) } }),
                                    modifier = Modifier.width((answers[k].length * 14 + 24).dp).border(1.dp, p.onFill.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                                )
                                if (post.isNotEmpty()) Text(post, style = Type.heading.of(view.learn), color = p.onFill)
                            }
                            else -> Text(pre + "＿".repeat(answers[k].length.coerceIn(3, 6)) + post, style = Type.heading.of(view.learn),
                                color = if (k == at) p.onFill else p.onFill.copy(alpha = 0.5f))
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
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            when {
                done -> Primary(stringResource(R.string.play_send)) { s.replyAndContinue(r.room, ReplyMode.CONSTELLATION) }
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
