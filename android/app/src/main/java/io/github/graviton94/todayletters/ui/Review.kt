package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Card
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Memory
import io.github.graviton94.todayletters.core.ReviewKind
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 칸 이름 (새 낱말 · 익히는 중 · 거의 · 기억). */
private val boxNames = listOf(R.string.box_new, R.string.box_learning, R.string.box_almost, R.string.box_known)
private val boxGaps = listOf(R.string.gap_today, R.string.gap_1, R.string.gap_4, R.string.gap_14)

/**
 * 영어 밖의 언어를 쓸 때의 글자 줄: 휴대폰 키보드에 바로 없는 글자를 한 번에 넣는다.
 * 언어가 늘면 여기에 더한다 (독일어 ä ö ü ß 등).
 */
fun inputHelpers(lang: Lang): List<String> = when (lang) {
    Lang.FR -> listOf("é", "è", "ê", "à", "ç", "ô", "û", "î", "ë", "ù", "â", "œ")
    Lang.DE -> listOf("ä", "ö", "ü", "ß", "Ä", "Ö", "Ü")
    Lang.NL -> listOf("é", "ë", "ï", "ü", "ĳ")
    else -> emptyList()
}

/**
 * 한국어 글자판: 자모로 쪼개지 않고 한 글자 단위. 정답의 글자에 다른 낱말의 글자를 섞어 12칸 (같은 문제는 늘 같은 배치).
 */
fun koSyllables(answer: String, pool: List<String>, seed: Int): List<String> {
    fun hangul(c: Char) = c in '\uAC00'..'\uD7A3'
    val need = answer.filter(::hangul).map { it.toString() }.distinct()
    val rnd = java.util.Random(answer.hashCode().toLong() + seed)
    val others = pool.flatMap { w -> w.filter(::hangul).map { it.toString() } }.distinct().filter { it !in need }.shuffled(rnd)
    val fill = (others + listOf("가", "나", "다", "마", "사", "아", "하", "지", "리", "고", "는", "에").filter { it !in need && it !in others }).take((12 - need.size).coerceAtLeast(0))
    return (need + fill).take(12).shuffled(rnd)
}

/**
 * 복습 (이름표): 낱말 카드 네 칸, 오늘의 복습, 문제 꼴별로 따로 하기, 내 구절, 모은 낱말 찾기.
 */
@Composable
fun WordsTab(s: AppState) {
    val p = Ink.palette
    s.version
    val cards = s.cards()
    val counts = Memory.counts(cards)
    val due = s.dueCards()
    val sample = (0..3).map { b -> cards.firstOrNull { it.box == b }?.let { s.cardWord(it.key) }?.let { (w, cl, i) -> cl.second.words[i].text[s.room(w.series.id).learn] } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1, top = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caps("Mémoire", p.giltText)
                Text(stringResource(R.string.cards_title), style = Type.title.ui(), color = p.ink)
                Text(stringResource(R.string.words_count, cards.size), style = Type.small.ui(), color = p.inkSoft)
            }
            IconButton(stringResource(R.string.settings), onClick = { s.settingsOpen = true }) { Gear(p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            // 낱말 카드 네 칸: 칸마다 카드 묶음, 맨 위 카드에 그 칸의 낱말 하나
            Row(Modifier.fillMaxWidth()) {
                (0..3).forEach { b ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CardStack(counts[b], sample[b], listOf(p.hair, p.gilt, p.giltText, p.correct)[b])
                        Text("${counts[b]} " + stringResource(boxNames[b]), style = Type.small.ui(), color = p.ink, maxLines = 1)
                        Text(stringResource(boxGaps[b]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk)
                    }
                }
            }
            Slip(seed = 41, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.review_today), style = Type.small.ui(), color = Color(0xFF7A5A20))
                        Text(
                            if (due.isEmpty()) stringResource(if (cards.isEmpty()) R.string.review_empty else R.string.review_none_today)
                            else stringResource(R.string.review_count, due.size, (due.size / 4).coerceAtLeast(1)),
                            style = Type.heading.ui(), color = p.slipInk,
                        )
                    }
                    if (due.isNotEmpty()) Box(Modifier.heightIn(min = 46.dp).pressable { s.go(Route.Session()) }.background(p.slipInk).padding(horizontal = Tokens.Space.s4), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.review_start), style = Type.body.ui(), color = p.slip)
                    }
                }
            }
            Column {
                ModeRow(stringResource(R.string.kind_dictation), stringResource(R.string.kind_dictation_d), stringResource(R.string.badge_default), enabled = cards.isNotEmpty()) { s.go(Route.Session(ReviewKind.DICTATION)) }
                ModeRow(stringResource(R.string.kind_blank), stringResource(R.string.kind_blank_d), null, enabled = cards.isNotEmpty()) { s.go(Route.Session(ReviewKind.BLANK)) }
                ModeRow(stringResource(R.string.kind_meaning), stringResource(R.string.kind_meaning_d), null, enabled = cards.isNotEmpty()) { s.go(Route.Session(ReviewKind.MEANING)) }
                ModeRow(stringResource(R.string.quotes_title), stringResource(R.string.quotes_count, s.store.quotes.size), null, enabled = true) { s.go(Route.Quotes) }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 46.dp).background(p.leaf).border(Tokens.Stroke.hair, p.line).pressable { s.go(Route.WordList) }.padding(horizontal = Tokens.Space.s3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                    drawCircle(p.inkSoft, size.width * 0.32f, androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.width * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    drawLine(p.inkSoft, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.width * 0.66f), androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.width * 0.95f), 1.5.dp.toPx())
                }
                Text(stringResource(R.string.words_search), style = Type.body.ui(), color = p.hideInk)
            }
        }
    }
}

@Composable
private fun CardStack(n: Int, word: String?, accent: Color) {
    val p = Ink.palette
    val layers = (n / 3).coerceIn(if (n > 0) 1 else 0, 3)
    Box(Modifier.size(84.dp, 66.dp)) {
        repeat(layers) { k ->
            Box(Modifier.offset((6 + k * 3).dp, (16 - k * 4).dp).size(64.dp, 44.dp).background(p.leaf).border(Tokens.Stroke.hair, p.hair))
        }
        Box(
            Modifier.offset((6 + layers * 3).dp, (16 - layers * 4).dp).size(64.dp, 44.dp).background(if (n > 0) p.slip else Color.Transparent)
                .then(if (n > 0) Modifier.border(Tokens.Stroke.hair, p.line) else Modifier.dashed(p.line)),
            contentAlignment = Alignment.Center,
        ) {
            if (n > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(accent).align(Alignment.TopCenter))
            if (word != null) Text(word, style = Type.small.copy(fontFamily = Faces.text), color = p.slipInk, maxLines = 1)
        }
    }
}

@Composable
private fun ModeRow(title: String, desc: String, badge: String?, enabled: Boolean, onClick: () -> Unit) {
    val p = Ink.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).pressable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = Type.body.ui(), color = if (enabled) p.ink else p.hideInk)
                if (badge != null) Text(badge, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.onFill, modifier = Modifier.background(p.fill).padding(horizontal = 6.dp, vertical = 1.dp))
            }
            Text(desc, style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
        }
        Chevron(p.hideInk)
    }
    Hair()
}

/** 문제 하나. */
private data class Q(val card: Card, val kind: ReviewKind, val word: String, val meaning: String, val ipa: String, val audio: String,
                     val sentence: String?, val sentenceRead: String?, val sentenceAudio: String?, val options: List<String>, val learn: Lang, val read: Lang)

/**
 * 복습 한 판: 듣고 쓰기 · 빈칸 채우기 · 뜻 고르기. [Route.Session.kind] 가 없으면 오늘의 복습 (칸에 맞춰 섞어서).
 * 끝나면 그 자리에서 결과 (오늘의 복습): 맞힌 수 · 복습 완료 · 내일 다시 · 칸이 바뀐 낱말.
 */
@Composable
fun Session(s: AppState, r: Route.Session) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    var asking by remember { mutableStateOf(false) }
    BackHandler { asking = true }
    val questions = remember(r) { buildQuestions(s, r.kind) }
    var at by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf<Pair<Q, Boolean>>()) }
    var mark by remember { mutableStateOf<Memory.Mark?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf(TextFieldValue("")) }
    var hint by remember { mutableStateOf(false) }
    // 글자판: 특수 문자를 고를 때는 시스템 키보드를 내리고 그 자리에 큰 글자판
    var pad by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun showKeyboard() { pad = false; runCatching { focus.requestFocus() }; keyboard?.show() }
    fun showPad() { keyboard?.hide(); pad = true }
    fun put(t: String) { typed = TextFieldValue(t, TextRange(t.length)) }

    if (questions.isEmpty()) { LaunchedEffect(Unit) { s.back() }; return }
    if (at >= questions.size) { SessionResult(s, r, results); return }
    val q = questions[at]
    LaunchedEffect(at) { if (q.kind != ReviewKind.BLANK) s.narrator.play(q.audio) }

    fun judge(m: Memory.Mark) {
        mark = m
        val ok = m != Memory.Mark.WRONG
        if (s.app.haptics) haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        if (q.kind == ReviewKind.BLANK && ok && q.sentenceAudio != null) s.narrator.play(q.sentenceAudio) else if (q.kind != ReviewKind.DICTATION || ok) s.narrator.play(q.audio)
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(end = Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(stringResource(R.string.close), onClick = { asking = true }) { Text("×", style = Type.title, color = p.ink) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                questions.indices.forEach { k -> Box(Modifier.weight(1f).height(4.dp).background(when { k < at -> p.giltText; k == at -> p.ink; else -> p.hair })) }
            }
            Text("  ${at + 1} / ${questions.size}", style = Type.small, color = p.inkSoft)
        }
        // 키보드가 올라오면 위쪽을 한 줄로 접는다 (듣기 · 0.7× · 안내), 확인은 키보드 바로 위
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val ime = androidx.compose.foundation.layout.WindowInsets.isImeVisible
        val compact = ime && q.kind == ReviewKind.DICTATION
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = if (compact) Tokens.Space.s2 else Tokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) Tokens.Space.s3 else Tokens.Space.s4),
        ) {
            if (!compact) Text(stringResource(when (q.kind) { ReviewKind.DICTATION -> R.string.kind_dictation; ReviewKind.BLANK -> R.string.kind_blank; ReviewKind.MEANING -> R.string.kind_meaning }), style = Type.small.ui(), color = p.giltText)
            when (q.kind) {
                ReviewKind.DICTATION -> {
                    if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(p.fill, CircleShape).pressable { s.narrator.play(q.audio) }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.onFill, 22.dp) }
                        Box(Modifier.size(40.dp).border(1.dp, p.giltText, CircleShape).pressable { s.narrator.play(q.audio, speed = 0.7f) }, contentAlignment = Alignment.Center) {
                            Text("0.7×", style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.giltText)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.kind_dictation), style = Type.small.ui(), color = p.giltText)
                            Text(stringResource(R.string.dict_letters, Breaks.plain(q.word).count { it != ' ' }), style = Type.small.ui(), color = p.inkSoft)
                        }
                    }
                    if (!compact) Text(stringResource(R.string.dict_prompt), style = Type.heading.ui(), color = p.ink)
                    // 큰 듣기 단추가 가운데: 오른쪽 0.7× 만큼 왼쪽을 비워 대칭을 맞춘다
                    if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(52.dp))
                        Box(Modifier.size(84.dp).background(p.fill, CircleShape).pressable { s.narrator.play(q.audio) }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.onFill, 34.dp) }
                        Box(Modifier.size(52.dp).border(1.dp, p.giltText, CircleShape).pressable { s.narrator.play(q.audio, speed = 0.7f) }, contentAlignment = Alignment.Center) {
                            Text("0.7×", style = Type.small, color = p.giltText)
                        }
                    }
                    // 글자 칸: 낱말 길이만큼 밑줄, 쓴 글자가 채워진다
                    val answer = Breaks.plain(q.word)
                    Row(Modifier.pressable(haptic = false) { showKeyboard() }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        answer.forEachIndexed { k, ch ->
                            val c = typed.text.getOrNull(k)
                            Box(Modifier.width(24.dp).height(40.dp), contentAlignment = Alignment.BottomCenter) {
                                if (ch == ' ') Spacer(Modifier) else {
                                    Text((c ?: ' ').toString(), style = Type.title.copy(fontFamily = Faces.text), color = when (mark) { null -> p.ink; Memory.Mark.WRONG -> if (c?.lowercaseChar() == ch.lowercaseChar()) p.ink else p.wrong; else -> p.correct })
                                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (c != null) p.ink else p.line))
                                }
                            }
                        }
                    }
                    // 한국어를 배우면 한 글자 글자판을 먼저 (한글 키보드가 없을 수 있어서), 그 밖에는 키보드
                    LaunchedEffect(at) { if (q.learn == Lang.KO) { pad = true; keyboard?.hide() } else { pad = false; runCatching { focus.requestFocus() } } }
                    BasicTextField(
                        typed, { if (mark == null) typed = it.copy(text = it.text.take(answer.length + 2)) },
                        singleLine = true, textStyle = Type.body.copy(color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (mark == null && typed.text.isNotBlank()) judge(Memory.grade(q.word, typed.text)) }),
                        modifier = Modifier.size(1.dp).focusRequester(focus),
                    )
                    Text(listOfNotNull(q.ipa.takeIf { it.isNotEmpty() }?.let { "[$it]" }, stringResource(R.string.dict_letters, answer.count { it != ' ' })).joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                    if (compact && !hint && mark == null) Unit
                    else if (hint || mark != null) SentenceHint(q) else Text(stringResource(R.string.dict_hint), style = Type.small.ui(), color = p.giltText, modifier = Modifier.pressable { hint = true }.padding(Tokens.Space.s2))
                }
                ReviewKind.BLANK -> {
                    Text(stringResource(R.string.blank_prompt), style = Type.heading.ui(), color = p.ink)
                    SentenceHint(q, filled = mark != null)
                }
                ReviewKind.MEANING -> {
                    Text(q.word, style = Type.display.of(q.learn), color = p.ink)
                    if (q.ipa.isNotEmpty()) Text("[${q.ipa}]", style = Type.small, color = p.inkSoft)
                    SpeakerButton(false, stringResource(R.string.listen_line), onDark = true) { s.narrator.play(q.audio) }
                }
            }
            if (q.kind != ReviewKind.DICTATION) OptionsGrid(q, picked, mark) { o ->
                if (mark == null) { picked = o; judge(if (o == (if (q.kind == ReviewKind.MEANING) q.meaning else q.word)) Memory.Mark.RIGHT else Memory.Mark.WRONG) }
            }
            when (mark) {
                Memory.Mark.RIGHT -> Text(stringResource(R.string.review_right), style = Type.body.ui(), color = p.correct)
                Memory.Mark.ACCENT -> Text(stringResource(R.string.review_accent, q.word), style = Type.body.ui(), color = p.correct, textAlign = TextAlign.Center)
                Memory.Mark.WRONG -> Text(stringResource(R.string.review_wrong, q.word, q.meaning), style = Type.body.ui(), color = p.wrong, textAlign = TextAlign.Center)
                null -> {}
            }
        }
        if (q.kind == ReviewKind.DICTATION && mark == null) {
            val helpers = if (q.learn == Lang.KO) remember(at) { koSyllables(Breaks.plain(q.word), s.cards().mapNotNull { c -> s.cardWord(c.key)?.let { (_, cl, i) -> cl.second.words[i].text[Lang.KO] } }, at) } else inputHelpers(q.learn)
            val label = stringResource(langLabel(q.learn))
            if (helpers.isNotEmpty() && !pad) Row(
                Modifier.fillMaxWidth().background(p.hide).padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 키보드 위 한 줄: 자주 쓰는 몇 글자 + 글자판 열기
                helpers.take(5).forEach { h ->
                    Box(Modifier.size(40.dp).background(p.leaf).border(Tokens.Stroke.hair, p.line).pressable { put(typed.text + h) }, contentAlignment = Alignment.Center) {
                        Text(h, style = Type.body.copy(fontFamily = Faces.text), color = p.ink)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.heightIn(min = 40.dp).border(1.dp, p.giltText).pressable { showPad() }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.pad_open, label), style = Type.small.ui(), color = p.giltText)
                }
            }
            if (helpers.isNotEmpty() && pad) Column(
                Modifier.fillMaxWidth().background(p.hide).padding(Tokens.Space.s3),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.input_helper, label), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.giltText)
                // 가로 4 × 세로 3: 키보드 자리를 차지하는 큰 글자판
                helpers.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { h ->
                            Box(Modifier.weight(1f).height(54.dp).background(p.leaf).border(Tokens.Stroke.hair, p.line).pressable { put(typed.text + h) }, contentAlignment = Alignment.Center) {
                                Text(h, style = Type.title.copy(fontFamily = Faces.text), color = p.ink)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(2f).height(48.dp).border(1.dp, p.line).pressable { showKeyboard() }, contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.pad_keyboard), style = Type.small.ui(), color = p.ink)
                    }
                    Box(Modifier.weight(2f).height(48.dp).border(1.dp, p.line).pressable { put(typed.text.dropLast(1)) }, contentAlignment = Alignment.Center) {
                        Text("⌫  " + stringResource(R.string.pad_delete), style = Type.small.ui(), color = p.ink)
                    }
                }
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (mark == null && q.kind == ReviewKind.DICTATION) Primary(stringResource(R.string.play_check), enabled = typed.text.isNotBlank()) { judge(Memory.grade(q.word, typed.text)) }
            else if (mark != null) Primary(stringResource(if (at == questions.lastIndex) R.string.review_finish else R.string.ob_next)) {
                val ok = mark != Memory.Mark.WRONG
                s.answer(q.card, ok); results = results + (q to ok)
                mark = null; picked = null; typed = TextFieldValue(""); hint = false; pad = false
                at++
            }
        }
    }
    if (asking) Ask(stringResource(R.string.review_leave), stringResource(R.string.review_stop), stringResource(R.string.review_keep),
        onYes = { asking = false; s.back() }, onNo = { asking = false }, body = stringResource(R.string.review_leave_d))
}

@Composable
private fun SentenceHint(q: Q, filled: Boolean = true) {
    val p = Ink.palette
    if (q.sentence == null) return
    Slip(seed = 13, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(stringResource(R.string.dict_hint_title), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF7A5A20))
            val blank = q.sentence.replace(q.word, if (filled) q.word else "_____", ignoreCase = true)
            Text(blank, style = Type.target.of(q.learn), color = p.slipInk)
            if (q.sentenceRead != null) Text(q.sentenceRead, style = Type.base.of(q.read), color = p.slipSoft)
        }
    }
}

@Composable
private fun OptionsGrid(q: Q, picked: String?, mark: Memory.Mark?, onPick: (String) -> Unit) {
    val p = Ink.palette
    val right = if (q.kind == ReviewKind.MEANING) q.meaning else q.word
    val lang = if (q.kind == ReviewKind.MEANING) q.read else q.learn
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        q.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                row.forEach { o ->
                    val st = when { mark == null -> 0; o == right -> 1; o == picked -> 2; else -> 3 }
                    Box(
                        Modifier.weight(1f).heightIn(min = 56.dp)
                            .background(when (st) { 1 -> p.correct; 2 -> p.wrong; else -> p.leaf })
                            .border(Tokens.Stroke.hair, if (st == 0) p.line else Color.Transparent)
                            .pressable(enabled = mark == null) { onPick(o) }.padding(Tokens.Space.s2),
                        contentAlignment = Alignment.Center,
                    ) { Text(o, style = Type.body.of(lang), color = if (st == 1 || st == 2) p.onFill else if (st == 3) p.hideInk else p.ink, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

/** 오늘의 복습 결과. */
@Composable
private fun SessionResult(s: AppState, r: Route.Session, results: List<Pair<Q, Boolean>>) {
    val p = Ink.palette
    LaunchedEffect(Unit) { if (r.kind == null) s.reviewDone() }
    val right = results.count { it.second }
    val wrong = results.filter { !it.second }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s6), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Caps("Révision · terminée", p.giltText)
            Text(stringResource(R.string.review_today), style = Type.title.ui(), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s6)) {
                Big("$right/${results.size}", stringResource(R.string.result_right), p.giltText)
                Big("$right", stringResource(R.string.result_done), p.correct)
                Big("${wrong.size}", stringResource(R.string.result_tomorrow), p.giltText)
            }
            Slip(seed = 17, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2)) {
                    Text(stringResource(R.string.result_moved), style = Type.small.ui(), color = Color(0xFF7A5A20), modifier = Modifier.padding(vertical = Tokens.Space.s2))
                    results.forEach { (q, ok) ->
                        val to = if (ok) (q.card.box + 1).coerceAtMost(3) else 0
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Text(q.word, style = Type.body.of(q.learn), color = p.slipInk, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(stringResource(boxNames[q.card.box]), style = Type.small.ui(), color = p.slipSoft)
                            Text(if (ok) "→" else "↩", style = Type.body, color = if (ok) p.correct else p.wrong)
                            Text(stringResource(boxNames[to]), style = Type.small.ui(), color = if (ok) p.correct else p.wrong)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.result_note), style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (wrong.isNotEmpty() && r.kind == null) Secondary(stringResource(R.string.result_again)) { s.back(); s.go(Route.Session(ReviewKind.MEANING)) }
            Primary(stringResource(R.string.done_home)) { s.go(Route.Inbox) }
        }
    }
}

@Composable
private fun Big(v: String, label: String, c: Color) {
    val p = Ink.palette
    Column {
        Text(v, style = Type.display.copy(fontFamily = Faces.display), color = c)
        Text(label, style = Type.small.ui(), color = p.inkSoft)
    }
}

@Composable
fun SpeakerGlyph(c: Color, size: androidx.compose.ui.unit.Dp) = androidx.compose.foundation.Canvas(Modifier.size(size)) {
    val w = this.size.width
    val st = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    drawPath(androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.12f, w * 0.38f); lineTo(w * 0.3f, w * 0.38f); lineTo(w * 0.52f, w * 0.18f); lineTo(w * 0.52f, w * 0.82f); lineTo(w * 0.3f, w * 0.62f); lineTo(w * 0.12f, w * 0.62f); close()
    }, c, style = st)
    drawArc(c, -50f, 100f, false, androidx.compose.ui.geometry.Offset(w * 0.5f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.36f, w * 0.4f), style = st)
    drawArc(c, -50f, 100f, false, androidx.compose.ui.geometry.Offset(w * 0.46f, w * 0.16f), androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.68f), style = st)
}

/** 문제 만들기: 카드 → 문제. 문장은 그 낱말이 나온 편지 문장 (없으면 빈칸 대신 뜻 고르기). */
private fun buildQuestions(s: AppState, kind: ReviewKind?): List<Q> {
    val pool = if (kind == null) s.dueCards() else s.cards().sortedWith(compareBy({ it.box }, { it.due })).take(Memory.DAILY)
    val all = s.cards().mapNotNull { c -> s.cardWord(c.key) }
    return pool.mapNotNull { card ->
        val (w, cl, i) = s.cardWord(card.key) ?: return@mapNotNull null
        val (ch, l) = cl
        val view = s.room(w.series.id)
        val word = l.words[i]
        val learnWord = word.text[view.learn]
        val hit = l.messages.indexOfFirst { m -> wordMarks(m.text[view.learn], listOf(learnWord)).isNotEmpty() }
        val sentenceLearn = l.messages.getOrNull(hit)?.text?.get(view.learn)
        val marks = sentenceLearn?.let { wordMarks(it, listOf(learnWord)) }
        val formInText = marks?.firstOrNull()?.first?.let { sentenceLearn.substring(it.first, it.last + 1) }
        var k = kind ?: Memory.kindFor(card)
        if (k == ReviewKind.BLANK && formInText == null) k = ReviewKind.MEANING
        val answerWord = if (k == ReviewKind.BLANK) formInText!! else learnWord
        val others = all.filter { it.third != i || it.second.second.id != l.id }.map { (ow, ocl, oi) -> ocl.second.words[oi].text[if (k == ReviewKind.MEANING) view.read else view.learn] }
            .distinct().filter { it != answerWord && it != word.text[view.read] }.shuffled(java.util.Random(card.key.hashCode().toLong())).take(3)
        val right = if (k == ReviewKind.MEANING) word.text[view.read] else answerWord
        Q(card, k, answerWord, word.text[view.read], if (view.learn == w.series.original) word.ipa else "",
            s.narrator.path(w.series.id, ch, l.id, "w${i + 1}_${view.learn.code}"),
            sentenceLearn, l.messages.getOrNull(hit)?.text?.get(view.read),
            if (hit >= 0) s.narrator.path(w.series.id, ch, l.id, "m${hit + 1}_${view.learn.code}") else null,
            (others + right).shuffled(java.util.Random(card.key.hashCode().toLong() + 7)), view.learn, view.read)
    }
}

/** 내 구절: 담아 둔 문장들. 듣기 · 엽서로 보내기. */
@Composable
fun QuotesScreen(s: AppState) {
    val p = Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    s.version
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.quotes_title), s, help = null, showBack = true, showSettings = false)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            val items = s.store.quotes.mapNotNull { k ->
                val (sid, ch, lid, mi) = k.split(":").takeIf { it.size == 4 } ?: return@mapNotNull null
                val w = s.works.firstOrNull { it.series.id == sid } ?: return@mapNotNull null
                val l = w.chapters.firstOrNull { it.id == ch }?.letters?.firstOrNull { it.id == lid } ?: return@mapNotNull null
                val m = mi.toIntOrNull()?.let { l.messages.getOrNull(it) } ?: return@mapNotNull null
                Triple(k, Triple(w, ch, l), m to mi.toInt())
            }
            if (items.isEmpty()) Text(stringResource(R.string.quotes_empty), style = Type.body.ui(), color = p.inkSoft)
            items.forEach { (k, wcl, mm) ->
                val (w, ch, l) = wcl; val (m, mi) = mm
                val view = s.room(w.series.id)
                Slip(seed = k.hashCode(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            PlateThumb(l.plate?.image.orEmpty(), true, Modifier.size(36.dp))
                            Text(dateLine(l.date, l.place), style = Type.small.ui(), color = p.slipSoft, modifier = Modifier.weight(1f))
                            IconButton(stringResource(R.string.quote_saved), onClick = { s.toggleQuote(k) }) { Bookmark(true) }
                        }
                        Text(m.text[view.learn], style = Type.target.of(view.learn), color = p.slipInk)
                        Text(m.text[view.read], style = Type.base.of(view.read), color = p.slipSoft)
                        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, p.slipInk.copy(alpha = 0.5f)).pressable { s.narrator.play(s.narrator.path(w.series.id, ch, l.id, "m${mi + 1}_${view.learn.code}")) }, contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.listen_line), style = Type.small.ui(), color = p.slipInk)
                            }
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).background(p.slipInk).pressable { sharePostcard(ctx, w, l, m, view.learn, view.read) }, contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.postcard_send), style = Type.small.ui(), color = p.slip)
                            }
                        }
                    }
                }
            }
        }
    }
}
