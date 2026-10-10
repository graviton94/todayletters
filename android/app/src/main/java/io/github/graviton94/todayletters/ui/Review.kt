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
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
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
/** 성장 단계 이름 · 다음 볼 때까지 (엔진 공통: [Memory.gaps]). */
private val boxNames = io.github.graviton94.todayletters.core.Growth.Stage.entries.map { it.label }
private val boxGaps = listOf(R.string.gap_today, R.string.gap_1, R.string.gap_3, R.string.gap_7, R.string.gap_16, R.string.gap_35)

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

/** 뜻 나누기: “study (sketch)” → [study, sketch], “country, land” → [country, land]. */
fun senses(x: String): List<String> = Breaks.plain(x).split(Regex("[,;/()]")).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(Breaks.plain(x)) }

/** 뜻이 여럿이면 첫 뜻 (쉼표 · 쌍반점 · 빗금 · 괄호 앞까지). */
fun firstSense(x: String): String = Breaks.plain(x).split(Regex("[,;/(]")).first().trim().ifEmpty { x }

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
    val sample = (0..Memory.TOP).map { b -> cards.firstOrNull { it.box == b }?.let { s.cardWord(it.key) }?.let { (w, cl, i) -> cl.second.words[i].text[s.room(w.series.id).learn] } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1, top = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caps("Mémoire", p.giltText, decorative = true)
                Text(stringResource(R.string.cards_title), style = Type.title.ui(), color = p.ink)
                Text(stringResource(R.string.words_count, cards.size), style = Type.small.ui(), color = p.inkSoft)
            }
            IconButton(stringResource(R.string.settings), onClick = { s.settingsOpen = true }) { Gear(p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            // 성장 곡선 여섯 단계: 만남 → 알아보기 → 떠올리기 → 쓰기 → 말하기 → 내 것 (단계마다 카드 묶음)
            listOf(0..2, 3..5).forEach { range ->
                Row(Modifier.fillMaxWidth()) {
                    range.forEach { b ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardStack(counts[b], sample[b], listOf(p.hair, p.gilt.copy(alpha = 0.6f), p.gilt, p.giltText, p.correct.copy(alpha = 0.75f), p.correct)[b])
                            Text("${counts[b]} " + stringResource(boxNames[b]), style = Type.small.ui(), color = p.ink, maxLines = 1)
                            Text(stringResource(boxGaps[b]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk)
                        }
                    }
                }
            }
            Text(stringResource(R.string.growth_note), style = Type.small.ui(), color = p.inkSoft)
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
                ModeRow(stringResource(R.string.kind_speak), stringResource(R.string.kind_speak_d), null, enabled = cards.isNotEmpty()) { s.go(Route.Session(ReviewKind.SPEAK)) }
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
                if (badge != null) Capsule(badge, CapsuleKind.FILLED)
            }
            Text(desc, style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
        }
        Chevron(p.hideInk)
    }
    Hair()
}

/** 문제 하나. */
private data class Q(val card: Card, val kind: ReviewKind, val word: String, val meaning: String, val ipa: String, val audio: String,
                     val sentence: String?, val sentenceRead: String?, val sentenceAudio: String?, val options: List<String>, val learn: Lang, val read: Lang,
                     /** 소리로 읽히는 전체 (“study (sketch)”). 그 안의 [typeFrom] 부터 [word] 길이만 쓰고 나머지는 미리 보여 준다. */
                     val full: String = word, val typeFrom: Int = 0,
                     /** 편지 문장 속 실제 꼴 (studies). 문장에서 표시 · 빈칸에 쓴다. */
                     val inText: String? = null)

/**
 * 복습 한 판: 듣고 쓰기 · 빈칸 채우기 · 뜻 고르기. [Route.Session.kind] 가 없으면 오늘의 복습 (칸에 맞춰 섞어서).
 * 끝나면 그 자리에서 결과 (오늘의 복습): 맞힌 수 · 복습 완료 · 내일 다시 · 칸이 바뀐 낱말.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Session(s: AppState, r: Route.Session) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var asking by remember { mutableStateOf(false) }
    // 중간에 나갔다 오면 그 자리부터 (같은 날 · 같은 문제 순서)
    val sessionName = if (r.only != null) "again" else r.kind?.name ?: "today"
    val saved = remember(r) { if (r.only != null) null else s.store.session(sessionName)?.takeIf { it.day == s.today && it.at > 0 } }
    val questions = remember(r) { buildQuestions(s, r.kind, r.only ?: saved?.keys) }
    var at by remember { mutableIntStateOf((saved?.at ?: 0).coerceAtMost(questions.size)) }
    // 문제를 푸는 동안만 ‘그만할까요?’ (결과 화면에서는 결과 화면의 뒤로 가기)
    BackHandler(enabled = at < questions.size) { asking = true }
    var results by remember { mutableStateOf(saved?.let { sv -> questions.take(sv.at).zip(sv.oks) } ?: listOf<Pair<Q, Boolean>>()) }
    var mark by remember { mutableStateOf<Memory.Mark?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf(TextFieldValue("")) }
    var hint by remember { mutableStateOf(false) }
    // 틀렸을 때 한 번 더 (v18): 첫 번째로 틀리면 붉은 띠와 ‘다시 고르기’, 두 번째도 틀리면 정답
    var tries by remember { mutableIntStateOf(0) }
    var retry by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf(setOf<String>()) }
    var shakeKey by remember { mutableIntStateOf(0) }
    // 다시 듣기를 누를수록 힌트가 조금씩: 1번째 첫 글자, 2번째 문장 힌트, 그 뒤로 한 글자씩 (마지막 한 글자는 남김)
    var replays by remember { mutableIntStateOf(0) }
    // 글자판: 특수 문자를 고를 때는 시스템 키보드를 내리고 그 자리에 큰 글자판
    var pad by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun showKeyboard() { pad = false; runCatching { focus.requestFocus() }; keyboard?.show() }
    fun showPad() { keyboard?.hide(); pad = true }
    fun put(t: String) { typed = TextFieldValue(t, TextRange(t.length)) }

    if (questions.isEmpty()) { LaunchedEffect(Unit) { s.back() }; return }
    if (at >= questions.size) { LaunchedEffect(Unit) { s.store.clearSession(sessionName) }; SessionResult(s, r, results); return }
    val q = questions[at]
    // 이름표의 번호: 그 낱말이 나온 편지 (N° 027 · LETTRE I)
    val tag = remember(q.card.key) {
        s.cardWord(q.card.key)?.let { (w, cl, wi) ->
            val n = w.chapters.flatMap { it.letters }.indexOf(cl.second) + 1
            "N° ${"%03d".format(wi + 1)} · LETTRE ${roman(n)}"
        }.orEmpty()
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { s.narrator.stop() } }
    // 새 문제로 오면 앞 문제의 소리(빈칸 문장 등)는 멈추고, 듣는 문제만 새 소리를
    LaunchedEffect(at) { if (q.kind != ReviewKind.BLANK && q.kind != ReviewKind.SPEAK) s.narrator.play(q.audio) else s.narrator.stop() }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    fun judge(m: Memory.Mark) {
        mark = m
        // 답을 보면 키보드 · 글자판을 내려 문장과 ‘다음’ 단추가 가려지지 않게
        keyboard?.hide(); focusManager.clearFocus(); pad = false
        val ok = m != Memory.Mark.WRONG
        s.judged(q.kind, ok)
        s.cue(ok)
        // 맞으면 ‘확인’ 진동 (Android 11+), 아니면 길게 한 번
        if (s.app.haptics) {
            if (ok && android.os.Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            else haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        }
        if (q.kind == ReviewKind.BLANK && ok && q.sentenceAudio != null) s.narrator.play(q.sentenceAudio) else if (q.kind != ReviewKind.DICTATION || ok) s.narrator.play(q.audio)
    }

    /** 첫 번째로 틀림: 흔들고 붉은 띠. 두 번째면 정답을 보여 준다. */
    fun miss(o: String?) {
        if (tries == 0) {
            tries = 1; retry = true; if (o != null) missed = missed + o; shakeKey++
            buzzWrong(view, s.app.haptics); s.cue(false)
            keyboard?.hide(); pad = false
        } else judge(Memory.Mark.WRONG)
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f).imePadding()) {
        PracticeHeader(at, questions.size, onClose = { asking = true })
        // 키보드가 올라오면 위쪽을 한 줄로 접는다 (듣기 · 0.7× · 안내), 확인은 키보드 바로 위
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val ime = WindowInsets.isImeVisible
        val compact = ime && !pad && q.kind == ReviewKind.DICTATION
        val scroll = rememberScrollState()
        LaunchedEffect(mark) { if (mark != null) { kotlinx.coroutines.delay(250); scroll.animateScrollTo(scroll.maxValue) } }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = Tokens.Space.s5, vertical = if (compact) Tokens.Space.s2 else Tokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) Tokens.Space.s3 else Tokens.Space.s4),
        ) {
            if (!compact) Text(stringResource(when (q.kind) { ReviewKind.DICTATION -> R.string.kind_dictation; ReviewKind.BLANK -> R.string.kind_blank; ReviewKind.MEANING -> R.string.kind_meaning; ReviewKind.SPEAK -> R.string.kind_speak }), style = Type.small.ui(), color = p.giltText)
            when (q.kind) {
                ReviewKind.DICTATION -> {
                    fun replay() { if (mark == null) { replays++; if (replays >= 2) hint = true } }
                    val listenLabel = stringResource(R.string.a11y_listen_word); val slowLabel = stringResource(R.string.a11y_listen_slow)
                    if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(p.fill, CircleShape).semantics { contentDescription = listenLabel }.pressable { s.narrator.play(q.audio); replay() }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.onFill, 22.dp) }
                        Box(Modifier.size(48.dp).border(1.dp, p.giltText, CircleShape).semantics { contentDescription = slowLabel }.pressable { s.narrator.play(q.audio, speed = 0.7f); replay() }, contentAlignment = Alignment.Center) {
                            Text("0.7×", style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.giltText)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.kind_dictation), style = Type.small.ui(), color = p.giltText)
                            Text(listOfNotNull(q.ipa.takeIf { it.isNotEmpty() }?.let { "[$it]" }, stringResource(R.string.dict_letters, Breaks.plain(q.word).count { it != ' ' })).joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                        }
                    }
                    if (!compact) Text(stringResource(R.string.dict_prompt), style = Type.heading.ui(), color = p.ink)
                    // 큰 듣기 단추가 가운데: 오른쪽 0.7× 만큼 왼쪽을 비워 대칭을 맞춘다
                    if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(52.dp))
                        Box(Modifier.size(84.dp).background(p.fill, CircleShape).semantics { contentDescription = listenLabel }.pressable { s.narrator.play(q.audio); replay() }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.onFill, 34.dp) }
                        Box(Modifier.size(52.dp).border(1.dp, p.giltText, CircleShape).semantics { contentDescription = slowLabel }.pressable { s.narrator.play(q.audio, speed = 0.7f); replay() }, contentAlignment = Alignment.Center) {
                            Text("0.7×", style = Type.small, color = p.giltText)
                        }
                    }
                    // 글자 칸: 소리로 읽히는 전체를 보여 주고, 쓸 낱말만 칸 (괄호 · 다른 뜻은 미리 적혀 있음)
                    val answer = Breaks.plain(q.word)
                    val full = Breaks.plain(q.full)
                    val shown = (if (replays >= 1) 1 else 0) + (replays - 2).coerceAtLeast(0)   // 힌트로 보여 줄 글자 수
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.heightIn(min = 48.dp).pressable(haptic = false) { if (q.learn == Lang.KO) showPad() else showKeyboard() },
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        full.forEachIndexed { j, ch ->
                            val k = j - q.typeFrom
                            if (k !in answer.indices) {
                                // 문제 밖 글자: 그대로 흐리게
                                Box(Modifier.width(if (ch == ' ') 10.dp else 16.dp).height(40.dp), contentAlignment = Alignment.BottomCenter) {
                                    Text(ch.toString(), style = Type.title.copy(fontFamily = Faces.text), color = p.inkSoft.copy(alpha = 0.7f))
                                }
                                return@forEachIndexed
                            }
                            val c = typed.text.getOrNull(k)
                            val hinted = c == null && mark == null && k < shown.coerceAtMost(answer.length - 1)
                            Box(Modifier.width(24.dp).height(40.dp), contentAlignment = Alignment.BottomCenter) {
                                if (ch == ' ') Spacer(Modifier) else {
                                    Text((c ?: if (hinted || mark != null) ch else ' ').toString(), style = Type.title.copy(fontFamily = Faces.text),
                                        color = when {
                                            c == null && mark != null -> p.wrong.copy(alpha = 0.6f)
                                            c == null -> p.giltText.copy(alpha = 0.55f)
                                            mark == null -> p.ink
                                            mark == Memory.Mark.WRONG -> if (c.lowercaseChar() == ch.lowercaseChar()) p.ink else p.wrong
                                            else -> p.correct
                                        })
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
                    if (!compact) Text(listOfNotNull(q.ipa.takeIf { it.isNotEmpty() }?.let { "[$it]" }, stringResource(R.string.dict_letters, answer.count { it != ' ' })).joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                    if (compact && !hint && mark == null) Unit
                    else if (hint || mark != null) SentenceHint(q) else Text(stringResource(R.string.dict_hint), style = Type.small.ui(), color = p.giltText, modifier = Modifier.pressable { hint = true }.padding(Tokens.Space.s2))
                }
                ReviewKind.BLANK -> {
                    Text(stringResource(R.string.blank_prompt), style = Type.heading.ui(), color = p.ink)
                    BlankPaper(q, filled = mark != null, highlight = hint || mark != null)
                    // 번역에서 찾기: 빈칸 낱말에 해당하는 말을 번역 문장에서 금빛으로 (맞히면 그대로 맞은 것으로)
                    if (!hint && mark == null && q.sentenceRead != null && q.read != q.learn) Text(
                        stringResource(R.string.blank_hint), style = Type.small.ui(), color = p.giltText,
                        modifier = Modifier.align(Alignment.End).border(1.dp, p.giltText.copy(alpha = 0.5f)).pressable { hint = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                ReviewKind.SPEAK -> SpeakPane(s, q, mark) { judge(it) }
                ReviewKind.MEANING -> {
                    LabelCard(q, tag) { s.narrator.play(q.audio) }
                    Text(stringResource(R.string.meaning_prompt), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.fillMaxWidth())
                }
            }
            val choose: (String) -> Unit = { o ->
                if (mark == null && !retry) {
                    picked = o
                    if (o == (if (q.kind == ReviewKind.MEANING) q.meaning else q.word)) judge(Memory.Mark.RIGHT) else miss(o)
                }
            }
            if (q.kind == ReviewKind.MEANING) IndexOptions(q, picked, mark, missed, retry, shakeKey, choose)
            if (q.kind == ReviewKind.BLANK) TypeTiles(q, picked, mark, missed, retry, shakeKey, choose)
            if (mark == Memory.Mark.RIGHT || mark == Memory.Mark.ACCENT) CorrectBurst(still = s.reducedMotion)
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
        fun next() {
            val ok = mark != Memory.Mark.WRONG
            if (r.kind == null && r.only == null) s.answer(q.card, ok)   // 기억 칸은 오늘의 복습에서만 움직인다 (따로 연습 · 다시 하기는 기록하지 않음)
            results = results + (q to ok)
            mark = null; picked = null; typed = TextFieldValue(""); hint = false; pad = false; replays = 0
            tries = 0; retry = false; missed = emptySet()
            at++
            s.store.saveSession(sessionName, io.github.graviton94.todayletters.data.Store.SavedSession(s.today, questions.map { it.card.key }, at, results.map { it.second }))
        }
        val nextLabel = stringResource(if (at == questions.lastIndex) R.string.review_finish else R.string.ob_next)
        // 그 낱말의 뜻 · 발음 (뜻 고르기는 고른 것이 뜻이니 낱말을)
        val detail = listOfNotNull(q.word, q.ipa.takeIf { it.isNotEmpty() }?.let { "[$it]" }, q.meaning).joinToString("  ·  ") +
            (if (q.kind == ReviewKind.MEANING) snippet(q)?.let { "\n“$it” · $tag" }.orEmpty() else "")
        when {
            retry -> FeedbackBar(
                ok = false, title = stringResource(R.string.practice_retry_title),
                primary = stringResource(if (q.kind == ReviewKind.DICTATION) R.string.practice_retype else R.string.practice_retry),
                onPrimary = { retry = false; picked = null; if (q.kind == ReviewKind.DICTATION) showKeyboard() },
                secondary = stringResource(R.string.practice_reveal), onSecondary = { retry = false; judge(Memory.Mark.WRONG) },
            )
            mark == Memory.Mark.WRONG -> FeedbackBar(false, stringResource(R.string.practice_answer), detail, nextLabel, ::next)
            mark != null -> FeedbackBar(true,
                if (mark == Memory.Mark.RIGHT) stringResource(R.string.review_right) else stringResource(R.string.review_accent, q.word),
                detail, nextLabel, ::next)
            q.kind == ReviewKind.DICTATION -> Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
                Primary(stringResource(R.string.play_check), enabled = typed.text.isNotBlank()) {
                    val g = Memory.grade(q.word, typed.text)
                    if (g == Memory.Mark.WRONG) miss(null) else judge(g)
                }
            }
        }
    }
    if (asking) Ask(stringResource(R.string.review_leave), stringResource(R.string.review_stop), stringResource(R.string.review_keep),
        onYes = { asking = false; s.back() }, onNo = { asking = false }, body = stringResource(R.string.review_leave_d))
}

@Composable
private fun SentenceHint(q: Q, filled: Boolean = true, highlight: Boolean = false) {
    val p = Ink.palette
    if (q.sentence == null) return
    Slip(seed = 13, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(stringResource(R.string.dict_hint_title), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF7A5A20))
            val key = q.inText ?: q.word
            val blank = q.sentence.replace(key, if (filled) key else "_____", ignoreCase = true)
            // 답이 나오면 문장 속 그 낱말에 금빛 표식
            val marked = androidx.compose.ui.text.buildAnnotatedString {
                append(blank)
                if (filled) {
                    var from = 0
                    while (true) {
                        val i = blank.indexOf(key, from, ignoreCase = true); if (i < 0 || key.isEmpty()) break
                        addStyle(androidx.compose.ui.text.SpanStyle(background = Color(0x66D2A955), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), i, i + key.length)
                        from = i + key.length
                    }
                }
            }
            Text(marked, style = Type.target.of(q.learn), color = p.slipInk)
            if (q.sentenceRead != null) Text(
                if (highlight) meaningMarks(q.sentenceRead, q.meaning, p.slipInk) else androidx.compose.ui.text.AnnotatedString(q.sentenceRead),
                style = Type.base.of(q.read), color = p.slipSoft,
            )
        }
    }
}

/** 번역 문장에서 뜻(“밝은, 환한”)의 말을 찾아 금빛 바탕으로. 한글은 글자 그대로, 다른 글은 어간(끝 한두 글자 뺀 것)으로 찾는다. */
private fun meaningMarks(text: String, meaning: String, ink: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        val plain = Breaks.plain(text)
        meaning.split(Regex("[,;/()]+")).map { Breaks.plain(it).trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val hangul = part.any { it in '\uAC00'..'\uD7A3' }
            val key = if (hangul || part.length <= 4) part else part.dropLast(if (part.length > 6) 2 else 1)
            var from = 0
            while (true) {
                val i = plain.indexOf(key, from, ignoreCase = true); if (i < 0) break
                // Breaks.keepAll 이 넣은 보이지 않는 글자 때문에 위치를 원문 기준으로 다시 센다
                var k = 0; var start = -1; var end = -1; var n = 0
                while (k < text.length) { if (text[k] != '\u2060') { if (n == i) start = k; if (n == i + key.length - 1) { end = k; break }; n++ }; k++ }
                if (start >= 0 && end >= start) addStyle(androidx.compose.ui.text.SpanStyle(background = Color(0x73D2A955), color = ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), start, end + 1)
                from = i + key.length
            }
        }
    }

@Composable
private fun OptionsGrid(q: Q, picked: String?, mark: Memory.Mark?, missed: Set<String>, retry: Boolean, shakeKey: Int, onPick: (String) -> Unit) {
    val p = Ink.palette
    val right = if (q.kind == ReviewKind.MEANING) q.meaning else q.word
    val lang = if (q.kind == ReviewKind.MEANING) q.read else q.learn
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        q.options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                row.forEach { o ->
                    // 0 고를 수 있음 · 1 정답 · 2 방금 틀림 · 3 흐리게 (이미 틀린 것 · 끝난 뒤 나머지)
                    val st = when {
                        mark == null && o in missed -> if (retry && o == picked) 2 else 3
                        mark == null -> 0; o == right -> 1; o == picked -> 2; else -> 3
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = 56.dp).then(if (o == picked && st == 2) Modifier.shake(shakeKey) else Modifier)
                            .background(when (st) { 1 -> p.correct; 2 -> p.wrong; else -> p.leaf })
                            .border(Tokens.Stroke.hair, if (st == 0) p.line else Color.Transparent)
                            .pressable(enabled = mark == null && !retry && o !in missed) { onPick(o) }.padding(Tokens.Space.s2),
                        contentAlignment = Alignment.Center,
                    ) { Text(o, style = Type.body.of(lang), color = if (st == 1 || st == 2) p.onFill else if (st == 3) p.hideInk else p.ink, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

/**
 * 말하기 단계: 뜻을 보고 그 낱말을 소리 내어 말한다. 폰의 음성 인식(그 언어)으로 맞는지 보고,
 * 인식을 쓸 수 없으면 원어민 소리를 들은 뒤 스스로 판정 (맞게 말했어요 · 다시).
 */
@Composable
private fun SpeakPane(s: AppState, q: Q, mark: Memory.Mark?, onJudge: (Memory.Mark) -> Unit) {
    val p = Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val available = remember { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) }
    var listening by remember(q) { mutableStateOf(false) }
    var heard by remember(q) { mutableStateOf<String?>(null) }
    var selfCheck by remember(q) { mutableStateOf(!available) }
    val tag = when (q.learn) { Lang.FR -> "fr-FR"; Lang.EN -> "en-US"; Lang.KO -> "ko-KR"; Lang.DE -> "de-DE"; Lang.NL -> "nl-NL"; Lang.JA -> "ja-JP" }
    val recognizer = remember(q) { if (available) android.speech.SpeechRecognizer.createSpeechRecognizer(ctx) else null }
    DisposableEffect(recognizer) { onDispose { runCatching { recognizer?.destroy() } } }
    fun listen() {
        val r = recognizer ?: run { selfCheck = true; return }
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(b: android.os.Bundle) {
                listening = false
                val all = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                heard = all.firstOrNull()
                // 들린 후보 가운데 하나라도 맞으면 맞음 (문장으로 들렸으면 낱말이 들어 있는지)
                val ok = all.any { cand -> Memory.grade(q.word, cand) != Memory.Mark.WRONG || cand.split(" ").any { Memory.grade(q.word, it) != Memory.Mark.WRONG } }
                if (mark == null) onJudge(if (ok) Memory.Mark.RIGHT else Memory.Mark.WRONG)
            }
            override fun onError(error: Int) { listening = false; selfCheck = true }
            override fun onReadyForSpeech(p0: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p0: android.os.Bundle?) {}
            override fun onEvent(p0: Int, p1: android.os.Bundle?) {}
        })
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // 가능하면 폰 안에서 알아듣기 (개인정보처리방침에 적은 대로)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        listening = true
        runCatching { r.startListening(i) }.onFailure { listening = false; selfCheck = true }
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() else selfCheck = true }
    // 뜻(내 언어)을 보고 배우는 언어로 말한다: 무엇을 어느 말로 할지 분명히 (v21)
    Text(stringResource(R.string.speak_prompt_lang, stringResource(langLabel(q.learn))), style = Type.heading.ui(), color = p.ink, textAlign = TextAlign.Center)
    Caps(stringResource(R.string.speak_meaning), p.giltText, small = true)
    Text("“${q.meaning}”", style = Type.display.of(q.read), color = p.ink, textAlign = TextAlign.Center)
    val plainWord = io.github.graviton94.todayletters.core.Breaks.plain(q.word)
    if (mark == null && !selfCheck) Text(stringResource(R.string.speak_hint_letters, plainWord.take(1), plainWord.count { it != ' ' }),
        style = Type.small.ui(), color = p.inkSoft)
    if (q.ipa.isNotEmpty() && (mark != null || selfCheck)) Text("[${q.ipa}]", style = Type.small, color = p.inkSoft)
    if (mark == null && !selfCheck) {
        Box(
            Modifier.size(96.dp).background(if (listening) p.wrong else p.fill, CircleShape).semantics { contentDescription = "mic" }
                .pressable {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) listen()
                    else ask.launch(android.Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(34.dp)) {
                val w = size.width
                drawRoundRect(p.onFill, Offset(w * 0.35f, 0f), androidx.compose.ui.geometry.Size(w * 0.3f, w * 0.62f), androidx.compose.ui.geometry.CornerRadius(w * 0.15f))
                drawArc(p.onFill, 0f, 180f, false, Offset(w * 0.2f, w * 0.25f), androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawLine(p.onFill, Offset(w / 2, w * 0.8f), Offset(w / 2, w), 2.dp.toPx())
            }
        }
        Text(stringResource(if (listening) R.string.speak_listening else R.string.speak_tap), style = Type.small.ui(), color = p.inkSoft)
    }
    heard?.let { Text(stringResource(R.string.speak_heard, it), style = Type.small.ui(), color = p.inkSoft) }
    if (mark == null && selfCheck) {
        // 스스로 판정: 먼저 말해 보고, 원어민 소리를 들은 뒤
        var heardIt by remember(q) { mutableStateOf(false) }
        Text(stringResource(R.string.speak_self), style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center)
        // 먼저 말해 본 뒤에야 원래 소리와 글자를 보여 준다
        if (!heardIt) { Secondary(stringResource(R.string.speak_reveal), small = true) { heardIt = true; s.narrator.play(q.audio) }; return }
        SpeakerButton(false, stringResource(R.string.a11y_listen_word)) { s.narrator.play(q.audio) }
        Text(q.word, style = Type.title.of(q.learn), color = p.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.speak_again), small = true) { onJudge(Memory.Mark.WRONG) } }
            Box(Modifier.weight(1f)) { Primary(stringResource(R.string.speak_ok), small = true) { onJudge(Memory.Mark.RIGHT) } }
        }
    }
    if (mark != null) LaunchedEffect(mark) { s.narrator.play(q.audio) }
}

/** 맞았을 때: 금빛 원이 튀어 오르며 체크가 그려지고, 고리가 퍼진다. */
@Composable
private fun CorrectBurst(still: Boolean) {
    val p = Ink.palette
    val k = remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    androidx.compose.foundation.Canvas(Modifier.size(84.dp)) {
        val t = k.value
        val c = center; val r = size.minDimension / 2
        // 퍼지는 고리 두 겹
        drawCircle(p.giltText.copy(alpha = (1f - t) * 0.6f), r * (0.5f + 0.5f * t), c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawCircle(p.giltText.copy(alpha = (1f - t) * 0.35f), r * (0.3f + 0.7f * t), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        // 튀어 오르는 원 (조금 넘쳤다가 제자리)
        val s = when { t < 0.55f -> t / 0.55f * 1.15f; else -> 1.15f - (t - 0.55f) / 0.45f * 0.15f }
        drawCircle(p.correct, r * 0.48f * s, c)
        // 체크: 원이 다 커진 뒤 그려짐
        val d = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val a = Offset(c.x - r * 0.2f, c.y + r * 0.0f); val b = Offset(c.x - r * 0.05f, c.y + r * 0.15f); val e = Offset(c.x + r * 0.22f, c.y - r * 0.15f)
        val st = 3.dp.toPx()
        if (d > 0f) drawLine(p.onFill, a, Offset(a.x + (b.x - a.x) * (d * 2).coerceAtMost(1f), a.y + (b.y - a.y) * (d * 2).coerceAtMost(1f)), st, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (d > 0.5f) { val u = (d - 0.5f) * 2; drawLine(p.onFill, b, Offset(b.x + (e.x - b.x) * u, b.y + (e.y - b.y) * u), st, cap = androidx.compose.ui.graphics.StrokeCap.Round) }
    }
}

/** 오늘의 복습 결과. */
@Composable
private fun SessionResult(s: AppState, r: Route.Session, results: List<Pair<Q, Boolean>>) {
    val p = Ink.palette
    LaunchedEffect(Unit) { if (r.kind == null && r.only == null) s.reviewDone() }
    BackHandler { s.back() }
    val practice = r.kind != null || r.only != null
    var detail by remember { mutableStateOf<String?>(null) }
    val right = results.count { it.second }
    val wrong = results.filter { !it.second }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s6), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Text(stringResource(R.string.result_caps), style = Type.caps, color = p.giltText, modifier = Modifier.clearAndSetSemantics { })
            Text(stringResource(when (r.kind) { null -> R.string.review_today; ReviewKind.DICTATION -> R.string.kind_dictation; ReviewKind.BLANK -> R.string.kind_blank; ReviewKind.MEANING -> R.string.kind_meaning; ReviewKind.SPEAK -> R.string.kind_speak }), style = Type.title.ui(), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s6)) {
                Big("$right/${results.size}", stringResource(R.string.result_right), p.giltText)
                if (!practice) Big("$right", stringResource(R.string.result_done), p.correct)
                if (!practice) Big("${wrong.size}", stringResource(R.string.result_tomorrow), p.giltText)
            }
            if (practice) Text(stringResource(R.string.result_practice_note), style = Type.small.ui(), color = p.inkSoft)
            if (!practice) Slip(seed = 17, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2)) {
                    Text(stringResource(R.string.result_moved), style = Type.small.ui(), color = Color(0xFF7A5A20), modifier = Modifier.padding(vertical = Tokens.Space.s2))
                    results.forEach { (q, ok) ->
                        val to = if (ok) (q.card.box + 1).coerceAtMost(Memory.TOP) else (q.card.box - 2).coerceAtLeast(0)
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable { detail = q.card.key }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Text(q.word, style = Type.body.of(q.learn), color = p.slipInk, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(stringResource(boxNames[q.card.box]), style = Type.small.ui(), color = p.slipSoft)
                            Text(if (ok) "→" else "↩", style = Type.body, color = if (ok) p.correct else p.wrong)
                            Text(stringResource(boxNames[to]), style = Type.small.ui(), color = if (ok) p.correct else p.wrong)
                        }
                    }
                }
            }
            if (!practice) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.result_note), style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            // 틀린 것 다시 (v21 F3): 틀린 낱말만 바로 한 판 더 (연습이라 기억 칸은 오늘 처음 결과대로)
            if (wrong.isNotEmpty()) {
                Primary(stringResource(R.string.result_again_n, wrong.size)) { s.back(); s.go(Route.Session(null, wrong.map { it.first.card.key }.distinct())) }
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable { s.go(Route.Inbox) }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.result_enough), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.inkSoft)
                }
            } else Primary(stringResource(R.string.done_home)) { s.go(Route.Inbox) }
        }
    }
    detail?.let { k -> WordDetail(s, k) { detail = null } }
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
private fun buildQuestions(s: AppState, kind: ReviewKind?, keys: List<String>? = null): List<Q> {
    val cards = s.cards()
    val pool = keys?.mapNotNull { k -> cards.firstOrNull { it.key == k } }?.takeIf { it.isNotEmpty() }
        ?: if (kind == null) s.dueCards() else cards.sortedWith(compareBy({ it.box }, { it.due })).take(Memory.DAILY)
    val all = s.cards().mapNotNull { c -> s.cardWord(c.key) }
    return pool.mapNotNull { card ->
        val (w, cl, i) = s.cardWord(card.key) ?: return@mapNotNull null
        val (ch, l) = cl
        val view = s.room(w.series.id)
        val word = l.words[i]
        val learnWord = word.text[view.learn]
        // “study (sketch)”, “country, land” 처럼 뜻이 여럿이면 뜻마다 편지 문장에서 찾는다
        val senses = senses(learnWord)
        val hit = l.messages.indexOfFirst { m -> wordMarks(m.text[view.learn], senses).isNotEmpty() }
        val sentenceLearn = l.messages.getOrNull(hit)?.text?.get(view.learn)
        val marks = sentenceLearn?.let { wordMarks(it, senses) }
        val formInText = marks?.firstOrNull()?.first?.let { sentenceLearn.substring(it.first, it.last + 1) }
        // 받아쓸 뜻: 문장에 나온 뜻, 없으면 첫 뜻. 소리는 전체를 읽으니 화면에도 전체를 두고 그 뜻만 칸으로
        val dictSense = marks?.firstOrNull()?.second?.let { senses.getOrNull(it) } ?: senses.firstOrNull() ?: learnWord
        val plainFull = Breaks.plain(learnWord)
        val typeFrom = plainFull.indexOf(dictSense).coerceAtLeast(0)
        var k = kind ?: Memory.kindFor(card)
        if (k == ReviewKind.BLANK && formInText == null) k = ReviewKind.MEANING
        // 듣고 쓰기는 첫 뜻 하나만 (“flower, blossom” → flower): 쉼표 · 괄호까지 치지 않게
        val answerWord = when (k) { ReviewKind.BLANK -> formInText!!; ReviewKind.DICTATION, ReviewKind.SPEAK -> dictSense; else -> learnWord }
        val others = all.filter { it.third != i || it.second.second.id != l.id }.map { (ow, ocl, oi) -> ocl.second.words[oi].text[if (k == ReviewKind.MEANING) view.read else view.learn] }
            .distinct().filter { it != answerWord && it != word.text[view.read] }.shuffled(java.util.Random(card.key.hashCode().toLong())).take(3)
        val right = if (k == ReviewKind.MEANING) word.text[view.read] else answerWord
        Q(card, k, answerWord, word.text[view.read], if (view.learn == w.series.original) word.ipa else "",
            s.narrator.path(w.series.id, ch, l.id, "w${i + 1}_${view.learn.code}"),
            sentenceLearn, l.messages.getOrNull(hit)?.text?.get(view.read),
            if (hit >= 0) s.narrator.path(w.series.id, ch, l.id, "m${hit + 1}_${view.learn.code}") else null,
            (others + right).shuffled(java.util.Random(card.key.hashCode().toLong() + 7)), view.learn, view.read,
            full = if (k == ReviewKind.DICTATION) plainFull else answerWord, typeFrom = if (k == ReviewKind.DICTATION) typeFrom else 0, inText = formInText)
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
            if (items.isEmpty()) EmptyState(stringResource(R.string.empty_quotes_t), stringResource(R.string.quotes_empty), stringResource(R.string.today_open)) { s.go(Route.Inbox) }
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

/** 편지 문장에서 그 낱말 둘레 한 토막 (앞뒤 30자 남짓). */
private fun snippet(q: Q): String? {
    val sen = q.sentence ?: return null
    val key = q.inText ?: q.word
    val i = sen.indexOf(key, ignoreCase = true).takeIf { it >= 0 } ?: return null
    val a = (i - 30).coerceAtLeast(0).let { k -> sen.lastIndexOf(' ', k).takeIf { it in 0 until i }?.plus(1) ?: k }
    val b = (i + key.length + 30).coerceAtMost(sen.length).let { k -> sen.indexOf(' ', k).takeIf { it >= 0 } ?: sen.length }
    return (if (a > 0) "…" else "") + sen.substring(a, b).trim() + (if (b < sen.length) "…" else "")
}

/** 뜻 고르기의 낱말: 작품 이름표처럼 (번호 · 어느 편지 · 큰 낱말 · 듣기 · 발음). */
@Composable
private fun LabelCard(q: Q, tag: String, onListen: () -> Unit) {
    val p = Ink.palette
    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        if (tag.isNotEmpty()) Caps(tag, p.inkSoft, small = true, decorative = true, modifier = Modifier.align(Alignment.End))
        Text(q.word, style = Type.display.copy(fontFamily = Faces.display).of(q.learn), color = p.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Box(Modifier.size(36.dp).border(1.dp, p.giltText, CircleShape).pressable { onListen() }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.giltText, 16.dp) }
            if (q.ipa.isNotEmpty()) Text("[${q.ipa}]", style = Type.small, color = p.inkSoft)
        }
    }
}

/** 뜻 고르기의 보기: 색인 카드 목록 A–D. 맞으면 초록 줄 + ✓, 틀리면 붉은 취소선 (그 줄은 다시 못 고름). */
@Composable
private fun IndexOptions(q: Q, picked: String?, mark: Memory.Mark?, missed: Set<String>, retry: Boolean, shakeKey: Int, onPick: (String) -> Unit) {
    val p = Ink.palette
    val right = q.meaning
    Column(Modifier.fillMaxWidth()) {
        Hair()
        q.options.forEachIndexed { k, o ->
            val st = when {
                mark == null && o in missed -> if (retry && o == picked) 2 else 3
                mark == null -> 0; o == right -> 1; o == picked -> 2; else -> 3
            }
            val c = when (st) { 1 -> p.correct; 2 -> p.wrong; 3 -> p.hideInk; else -> p.ink }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (st == 2 && o == picked) Modifier.shake(shakeKey) else Modifier)
                    .background(if (st == 1) p.correct.copy(alpha = 0.12f) else Color.Transparent)
                    .pressable(enabled = mark == null && !retry && o !in missed) { onPick(o) }.padding(horizontal = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                Text("ABCD".getOrElse(k) { ' ' }.toString(), style = Type.caps, color = if (st == 0) p.giltText else c)
                Text(o, style = Type.body.of(q.read).copy(textDecoration = if (st == 2) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                    color = c, modifier = Modifier.weight(1f))
                if (st == 1) Text("✓", style = Type.body, color = p.correct)
            }
            Hair()
        }
    }
}

/** 빈칸 채우기의 문장: 편지지 위에, 빈칸은 금빛 밑줄. 맞으면 그 자리에 낱말이 끼워지고 번역 쪽 말도 금빛. */
@Composable
private fun BlankPaper(q: Q, filled: Boolean, highlight: Boolean) {
    val p = Ink.palette
    if (q.sentence == null) return
    val key = q.inText ?: q.word
    val gap = "\u2007".repeat(key.length.coerceIn(4, 10))
    val text = androidx.compose.ui.text.buildAnnotatedString {
        val i = q.sentence.indexOf(key, ignoreCase = true)
        if (i < 0) { append(q.sentence); return@buildAnnotatedString }
        append(q.sentence.substring(0, i))
        withStyle(androidx.compose.ui.text.SpanStyle(color = p.giltText, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Normal, fontWeight = if (filled) androidx.compose.ui.text.font.FontWeight.SemiBold else null)) {
            append(if (filled) q.sentence.substring(i, i + key.length) else gap)
        }
        append(q.sentence.substring(i + key.length))
    }
    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(text, style = Type.target.copy(fontFamily = Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic).of(q.learn), color = p.ink)
        if (q.sentenceRead != null) Text(
            if (highlight) meaningMarks(q.sentenceRead, q.meaning, p.ink) else androidx.compose.ui.text.AnnotatedString(q.sentenceRead),
            style = Type.small.of(q.read), color = p.inkSoft,
        )
    }
}

/** 빈칸 채우기의 보기: 활자 조각. 맞은 조각은 빈칸으로 들어가 자리만 남고, 틀린 조각은 비틀리며 붉게 (다시 못 고름). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TypeTiles(q: Q, picked: String?, mark: Memory.Mark?, missed: Set<String>, retry: Boolean, shakeKey: Int, onPick: (String) -> Unit) {
    val p = Ink.palette
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        q.options.forEach { o ->
            val used = mark != null && o == q.word                       // 빈칸으로 들어감
            val bad = o in missed && (mark != null || (retry && o == picked)) || (mark == Memory.Mark.WRONG && o == picked)
            val gone = (o in missed && !bad) || used
            val alpha by androidx.compose.animation.core.animateFloatAsState(if (gone) 0.18f else 1f, androidx.compose.animation.core.tween(if (calm()) 0 else Tokens.Motion.fadeMs), label = "tile")
            val c = if (bad) p.wrong else p.ink
            Box(
                Modifier.then(if (bad) Modifier.shake(shakeKey) else Modifier).graphicsLayer { this.alpha = alpha; rotationZ = if (bad) -3f else 0f }
                    .background(p.paper).border(1.dp, c).drawBehind { drawRect(c, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
                    .pressable(enabled = mark == null && !retry && o !in missed) { onPick(o) }
                    .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
            ) { Text(o, style = Type.heading.copy(fontFamily = Faces.display).of(q.learn), color = c) }
        }
    }
}
