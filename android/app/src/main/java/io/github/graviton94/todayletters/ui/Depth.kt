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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Ladder
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.SpeechMatch
import io.github.graviton94.todayletters.core.Support
import io.github.graviton94.todayletters.core.TalkLine
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay

/*
 * 학습 깊이 (시안 depth.html 2): 편지 이해 확인 · 오늘의 회화 · 답장 말해 보기, 그리고 셋이 같이 쓰는 문장 말하기 칸.
 * 얼마나 도와줄지는 모두 core 의 Support (도움 사다리) 가 정한다: 언어 등급 ([AppState.support]) 과 낱말마다 익숙한 정도 ([AppState.familiarity]).
 * 글은 모두 배우는 말 · 읽는 말로 고른다 (원어 · 영어 · 한국어 어느 것을 배우든, 어느 말로 읽든).
 */

/** 앱 글자로 쓴 짧은 줄 (자리 · 이름): 읽는 말이 영어 · 한국어면 그 말, 아니면 앱 글자 언어. */
@Composable
private fun shortLang(read: Lang): Lang = if (read == Lang.EN || read == Lang.KO) read else uiLang()

/**
 * 편지 이해 확인 (읽기 시험 꼴): 편지를 다 읽은 뒤, 낱말 맞추기 앞. 문제마다
 * 안내 한 줄 → 지문 (근거 메시지의 배우는 말 전문, 줄이지 않음 · 듣기) → 질문 → 보기 A–D ([MeaningOptions]).
 * 질문 · 보기 언어는 [Support.check] (A1–A2 읽는 말, B1 질문만 배우는 말, B2 둘 다 배우는 말).
 * 지문 번역은 감춰 두고 A1 에서만 눌러서 본다. 맞히거나 두 번 틀리면 지문 속 그 대목이 옅은 금빛, 번역 줄이 열린다.
 */
@Composable
fun CheckStep(s: AppState, r: Route.Play, onClose: () -> Unit) {
    val p = Ink.palette
    val view0 = androidx.compose.ui.platform.LocalView.current
    val haptic = LocalHapticFeedback.current
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val view = s.room(id)
    val checks = letter.checks
    val n = checks.size
    var at by rememberSaveable(letter.id) { mutableIntStateOf(0) }
    val k = at.coerceIn(0, (n - 1).coerceAtLeast(0))
    val c = checks.getOrNull(k)
    val band = s.support(id).band
    val langs = Support.check(band, view.learn, view.read)
    var picked by remember(letter.id, k) { mutableStateOf<String?>(null) }
    var missed by remember(letter.id, k) { mutableStateOf(setOf<String>()) }
    var shakeKey by remember { mutableIntStateOf(0) }
    var showRead by remember(letter.id, k) { mutableStateOf(false) }
    val options = remember(letter.id, k, langs) { c?.options?.map { Breaks.plain(it[langs.options]) }.orEmpty() }
    val right = options.getOrNull(c?.answer ?: -1).orEmpty()
    val over = picked == right || missed.size >= 2
    DisposableEffect(letter.id) { onDispose { s.narrator.stop() } }

    // 지문 듣기: 근거 메시지의 낭독을 차례로 (없으면 단추 없음)
    val clips = remember(letter.id, k, view.learn) {
        c?.evidence.orEmpty().map { i -> s.narrator.path(id, chapter, letter.id, "m${i + 1}_${view.learn.code}") }.filter { s.narrator.has(it) }
    }
    fun listen(from: Int = 0) { clips.getOrNull(from)?.let { s.narrator.play(it) { if (from + 1 < clips.size) listen(from + 1) } } }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(if (over) k + 1 else k, n, onClose = onClose, count = "${k + 1} / $n")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4).padding(top = Tokens.Space.s1, bottom = Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle("CHECK", stringResource(R.string.check_title))
            if (c == null) return@Column
            Text(stringResource(R.string.check_instruction), style = Type.body.ui(), color = p.inkSoft)
            // 지문: 배우는 말 전문 (줄이지 않는다). 맞힌 뒤 그 대목에 옅은 금빛, 번역 줄
            val passage = c.evidence.map { letter.messages[it].text }
            if (passage.isNotEmpty()) Column(
                Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                val soft = p.gilt.copy(alpha = 0.22f)
                passage.forEach { m ->
                    val t = Breaks.plain(m[view.learn])
                    val mark = c.mark?.lines?.get(view.learn)?.let { Breaks.plain(it) }?.takeIf { it.isNotBlank() }
                    val at0 = mark?.let { t.indexOf(it, ignoreCase = true) } ?: -1
                    Text(buildAnnotatedString {
                        append(t)
                        if (over) {
                            if (at0 >= 0) addStyle(SpanStyle(background = soft, color = p.giltText, fontWeight = FontWeight.SemiBold), at0, at0 + mark!!.length)
                            else if (mark == null) addStyle(SpanStyle(background = soft), 0, t.length)
                        }
                    }, style = Type.target.copy(fontFamily = Faces.display).of(view.learn), color = p.ink)
                    if (over || showRead) Text(m[view.read], style = Type.small.of(view.read), color = p.inkSoft)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    if (clips.isNotEmpty()) SpeakerButton(false, stringResource(R.string.check_listen)) { listen() }
                    if (!over && !showRead && Support.checkTranslationTappable(band)) Text(stringResource(R.string.fade_show), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline),
                        color = p.giltText, modifier = Modifier.heightIn(min = 44.dp).pressable { showRead = true }.padding(vertical = Tokens.Space.s2))
                }
            }
            // 질문 · 보기
            Text(c.q[langs.question], style = Type.heading.of(langs.question), color = p.ink)
            MeaningOptions(options, right, langs.options, picked, over, missed, false, shakeKey) { o ->
                picked = o
                if (o == right) {
                    if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    s.cue(true)
                } else { missed = missed + o; shakeKey++; buzzWrong(view0, s.app.haptics); s.cue(false) }
            }
            if (!over && missed.isNotEmpty()) Text(stringResource(R.string.check_wrong), style = Type.small.ui(), color = p.wrong)
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (k < n - 1) Primary(stringResource(R.string.check_next), enabled = over) { s.narrator.stop(); at = k + 1 }
            else Primary(stringResource(R.string.play_finish), enabled = over) { s.narrator.stop(); s.replyAndContinue(r.room, ReplyMode.CHECK) }
            if (!(over && k == n - 1)) SkipLink { s.narrator.stop(); s.skipStep(r.room, ReplyMode.CHECK) }
        }
    }
}

/**
 * 오늘의 회화: 편지의 인물과 오늘 말투로 3~4줄 (앱이 쓴 대화). 상대 줄은 이름 · 듣기 · 배우는 말 · 읽는 말,
 * 내 줄은 도움 사다리 ([Support.talk]) 만큼 보여 주고 소리 내어 말한 뒤 ([SpeakLine]) 배우는 말 줄이 말풍선으로 열린다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TalkStep(s: AppState, r: Route.Play, onClose: () -> Unit) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val work = s.work(id)
    val view = s.room(id)
    val talk = letter.talk
    val lines = talk?.lines.orEmpty()
    val help = Support.talk(s.support(id).band)
    val known = remember(id, s.version) { s.familiarity(id) }
    // 지금까지 열린 줄 수 (상대 줄은 저절로, 내 줄은 말한 뒤)
    var shown by rememberSaveable(letter.id) { mutableIntStateOf(0) }
    LaunchedEffect(shown, lines.size) {
        // 상대 줄은 차례로 열린다 (내 줄 앞에서 멈춤)
        if (shown < lines.size && !lines[shown].me) { delay(if (shown == 0) 300 else 900); shown++ }
    }
    fun audio(i: Int): String? = talk?.audioName(i, view.learn)?.let { s.narrator.path(id, chapter, letter.id, it) }?.takeIf { s.narrator.has(it) }
    LaunchedEffect(shown) { val i = shown - 1; if (i >= 0 && !lines[i].me) audio(i)?.let { s.narrator.play(it) } }
    DisposableEffect(letter.id) { onDispose { s.narrator.stop() } }
    val done = shown >= lines.size && lines.isNotEmpty()
    val them = work.name[uiLang()]
    val scroll = rememberScrollState()
    LaunchedEffect(shown) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(lines.take(shown).count { it.me }, lines.count { it.me }, onClose = onClose)
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = Tokens.Space.s4).padding(top = Tokens.Space.s1, bottom = Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle("TALK", stringResource(R.string.talk_title, them))
            if (talk == null) return@Column
            // 자리 · 오늘 낱말 · 앱이 쓴 대화
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                talk.scene[shortLang(view.read)].takeIf { it.isNotBlank() }?.let { Text(it, style = Type.small.ui(), color = p.inkSoft) }
                val words = talk.wordsFor(view.learn, work.series.original)
                if (words.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.talk_words), style = Type.small.ui(), color = p.giltText)
                    words.forEach { Capsule(Breaks.plain(it)) }
                }
                Text(stringResource(R.string.talk_written), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk)
            }
            lines.take(shown).forEachIndexed { i, line ->
                if (line.me) MyBubble(line.text[view.learn], line.text[view.read], view.learn, view.read)
                else TheirBubble(them, line.text[view.learn], line.text[view.read], view.learn, view.read, audio(i)?.let { a -> { s.narrator.play(a); Unit } })
            }
            // 내 차례
            if (shown < lines.size && lines[shown].me) {
                val i = shown
                MyTurn(s, id, lines[i], i, help, view.learn, view.read, known, audio(i)) { shown = i + 1 }
            }
            if (done) Text(stringResource(R.string.talk_done), style = Type.small.ui(), color = p.correct, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Primary(stringResource(R.string.play_finish), enabled = done) { s.narrator.stop(); s.replyAndContinue(r.room, ReplyMode.TALK) }
            if (!done) SkipLink { s.narrator.stop(); s.skipStep(r.room, ReplyMode.TALK) }
        }
    }
}

/** 상대 말풍선 (왼쪽): 이름 · 듣기 (소리가 있을 때) · 배우는 말 · 읽는 말. */
@Composable
private fun TheirBubble(name: String, learnText: String, readText: String, learn: Lang, read: Lang, onListen: (() -> Unit)?) {
    val p = Ink.palette
    Column(Modifier.fillMaxWidth(0.88f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(name, style = Type.capsSm.ui(), color = p.giltText)
            if (onListen != null) SpeakerButton(false, stringResource(R.string.a11y_listen_word), onClick = onListen)
        }
        Column(Modifier.clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)).background(p.slip).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(learnText, style = Type.body.of(learn), color = p.slipInk)
            if (read != learn) Text(readText, style = Type.small.of(read), color = p.slipSoft)
        }
    }
}

/** 내 말풍선 (오른쪽): 말한 뒤 열린 배우는 말 줄과 읽는 말. */
@Composable
private fun MyBubble(learnText: String, readText: String, learn: Lang, read: Lang) {
    val p = Ink.palette
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.widthIn(max = 320.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.talk_me), style = Type.capsSm.ui(), color = p.giltText)
            Column(Modifier.clip(RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp)).background(p.fill).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(learnText, style = Type.body.of(learn), color = p.onFill)
                if (read != learn) Text(readText, style = Type.small.of(read), color = p.onFill.copy(alpha = 0.75f))
            }
        }
    }
}

/**
 * 내 차례 한 줄: 도움 사다리만큼 ([Support.Talk]).
 *  - SHADOW: 배우는 말 줄 + 번역 + 듣기 → 따라 말하기
 *  - BLANK: 줄에 빈칸 하나 (그 줄의 덩어리 [TalkLine.focus], 잘 아는 것 먼저 [Support.pickBlank]) + 칩 (정답 · 적힌 틀린 보기) → 채운 뒤 말하기
 *  - CHIPS: 번역 + 첫 낱말 + 섞은 낱말 칩 → 말하기 (힌트 한 번에 줄 전체)
 *  - MEANING: 번역만 (아직 모르는 낱말은 칩으로 남김) → 말하기 (힌트 두 단: 첫 낱말 → 줄 전체)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyTurn(s: AppState, series: String, line: TalkLine, index: Int, help: Support.Talk, learn: Lang, read: Lang,
                   known: (String) -> Int, audio: String?, onDone: () -> Unit) {
    val p = Ink.palette
    val sentence = remember(line, learn) { Breaks.plain(line.text[learn]).trim() }
    val words = remember(sentence) { sentence.split(Regex("\\s+")).filter { it.isNotBlank() } }
    var hints by remember(index) { mutableIntStateOf(0) }
    // 빈칸 (BLANK): 덩어리 가운데 잘 아는 것, 덩어리가 없으면 낱말 점수로 하나
    val hole = remember(sentence, help) {
        if (help != Support.Talk.BLANK) null else {
            val focus = Ladder.focusIn(sentence, line.focus[learn].orEmpty().map { Breaks.plain(it) })
            Support.pickBlank(focus, known)?.let { f -> Ladder.find(sentence, f) }
                ?: Ladder.holes(sentence, emptyList(), 1) { w -> Support.blankScore(if (w.length >= 2) 1 + w.length / 4 else 0, known(w)) }.firstOrNull()
        }
    }
    val answer = hole?.let { sentence.substring(it) }
    val chips = remember(sentence, hole) {
        answer?.let { a ->
            val k = line.focus[learn].orEmpty().map { Breaks.plain(it) }.indexOfFirst { Ladder.find(sentence, it) == hole }
            val authored = line.wrong[learn]?.getOrNull(k).orEmpty().map { Breaks.plain(it) }
            Ladder.chips(a, authored, words.map { Ladder.core(it) }.filter { it.isNotEmpty() && !it.equals(a, true) }, sentence.hashCode() + index)
        }.orEmpty()
    }
    var filled by remember(index) { mutableStateOf(hole == null) }
    var wrongChip by remember(index) { mutableStateOf<String?>(null) }
    LaunchedEffect(wrongChip) { if (wrongChip != null) { delay(900); wrongChip = null } }
    val showLine = Support.talkLineShown(help, hints) || (help == Support.Talk.BLANK && filled)

    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(stringResource(when (help) {
            Support.Talk.SHADOW -> R.string.talk_repeat
            Support.Talk.BLANK -> R.string.talk_fill
            else -> R.string.talk_turn
        }), style = Type.small.ui(), color = p.giltText)
        // 뜻 (읽는 말): 늘 보인다. 따라 말하기 · 빈칸에서는 배우는 말 줄 아래에
        if (help == Support.Talk.CHIPS || help == Support.Talk.MEANING) Text(quoted(line.text[read], read), style = Type.heading.of(read), color = p.ink)
        when {
            showLine -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                if (audio != null) SpeakerButton(false, stringResource(R.string.a11y_listen_word)) { s.narrator.play(audio) }
                Text(sentence, style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink, modifier = Modifier.weight(1f))
            }
            help == Support.Talk.BLANK && hole != null -> Text(buildAnnotatedString {
                append(sentence.substring(0, hole.first))
                append("＿".repeat((answer!!.length).coerceIn(3, 6)))
                append(sentence.substring(hole.last + 1))
            }, style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink)
            Support.talkFirstWord(help, hints) -> Text(words.firstOrNull().orEmpty() + " …", style = Type.title.copy(fontFamily = Faces.display).of(learn), color = p.ink)
        }
        if (help == Support.Talk.SHADOW || help == Support.Talk.BLANK) Text(line.text[read], style = Type.small.of(read), color = p.inkSoft)
        // 칩: 빈칸 고르기 (BLANK), 섞은 낱말 (CHIPS), 아직 모르는 낱말 (MEANING)
        when (help) {
            Support.Talk.BLANK -> if (!filled) FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                chips.forEach { c ->
                    val bad = wrongChip == c
                    Box(Modifier.heightIn(min = 44.dp).border(1.dp, if (bad) p.wrong else p.line)
                        .pressable(role = Role.Button) { if (Ladder.right(answer!!, c)) { filled = true; s.cue(true) } else { wrongChip = c; s.cue(false) } }
                        .padding(horizontal = Tokens.Space.s4), contentAlignment = Alignment.Center) {
                        Text(c, style = Type.body.of(learn), color = if (bad) p.wrong else p.ink)
                    }
                }
            }
            Support.Talk.CHIPS -> if (!showLine) WordChips(remember(sentence) { words.drop(1).map { Ladder.core(it) }.filter { it.isNotEmpty() }.let { w -> w.shuffled(java.util.Random(sentence.hashCode().toLong())).let { if (it == w && w.size > 1) w.drop(1) + w.first() else it } } }, learn)
            Support.Talk.MEANING -> if (!showLine) WordChips(remember(sentence, known) { Support.keepVisible(words.map { Ladder.core(it) }.filter { it.isNotEmpty() }, known).distinct() }, learn)
            else -> Unit
        }
        if (!showLine && Support.talkHints(help) > hints) Text(stringResource(R.string.talk_hint), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.giltText,
            modifier = Modifier.heightIn(min = 44.dp).pressable { hints++ }.padding(vertical = Tokens.Space.s2))
        // 말하기 (빈칸이면 채운 뒤)
        if (filled) SpeakLine(s, sentence, learn, audio, key = index, onDone = onDone)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(words: List<String>, learn: Lang) {
    if (words.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { words.forEach { Capsule(it) } }
}

/**
 * 문장 말하기 칸 (회화 내 차례 · 답장 말해 보기): 마이크를 누르고 [sentence] 를 말한다. 폰의 음성 인식(그 언어, 가능하면 폰 안에서) 으로
 * 문장 낱말의 60% 이상이 들리면 맞음 ([SpeechMatch]). 인식을 쓸 수 없으면 (복습 말하기와 같이) 먼저 말해 보고, 원래 소리를 들은 뒤 스스로 판정.
 * 어느 쪽이든 끝나면 [onDone] (틀려도 막지 않는다: 다시 말하기 또는 다음으로).
 */
@Composable
fun SpeakLine(s: AppState, sentence: String, learn: Lang, audio: String?, key: Any, onDone: () -> Unit) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val available = remember { runCatching { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false) }
    var listening by remember(key) { mutableStateOf(false) }
    var heard by remember(key) { mutableStateOf<String?>(null) }
    var ok by remember(key) { mutableStateOf<Boolean?>(null) }
    var selfCheck by remember(key) { mutableStateOf(!available) }
    var revealed by remember(key) { mutableStateOf(false) }
    val tag = when (learn) { Lang.FR -> "fr-FR"; Lang.EN -> "en-US"; Lang.KO -> "ko-KR"; Lang.DE -> "de-DE"; Lang.NL -> "nl-NL"; Lang.JA -> "ja-JP" }
    val recognizer = remember(key) { if (available) runCatching { android.speech.SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull() else null }
    DisposableEffect(recognizer) { onDispose { runCatching { recognizer?.destroy() } } }
    fun listen() {
        val r = recognizer ?: run { selfCheck = true; return }
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(b: android.os.Bundle) {
                listening = false
                val all = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                heard = all.firstOrNull()
                val hit = all.any { SpeechMatch.matches(sentence, it) }
                ok = hit; s.cue(hit)
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
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> if (granted) listen() else selfCheck = true }
    val micLabel = stringResource(R.string.speak_mic)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        when {
            ok == true -> {
                Text(stringResource(R.string.speak_line_ok), style = Type.small.ui(), color = p.correct)
                LaunchedEffect(key) { delay(700); onDone() }
            }
            !selfCheck -> {
                Box(
                    Modifier.size(64.dp).background(if (listening) p.wrong else p.fill, CircleShape).semantics { contentDescription = micLabel }
                        .pressable {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) listen()
                            else ask.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                        val w = size.width
                        drawRoundRect(p.onFill, Offset(w * 0.35f, 0f), androidx.compose.ui.geometry.Size(w * 0.3f, w * 0.62f), androidx.compose.ui.geometry.CornerRadius(w * 0.15f))
                        drawArc(p.onFill, 0f, 180f, false, Offset(w * 0.2f, w * 0.25f), androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        drawLine(p.onFill, Offset(w / 2, w * 0.8f), Offset(w / 2, w), 2.dp.toPx())
                    }
                }
                Text(stringResource(if (listening) R.string.speak_listening else R.string.speak_mic), style = Type.small.ui(), color = p.inkSoft)
                heard?.let { Text(stringResource(R.string.speak_heard, it), style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center) }
                if (ok == false) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.speak_line_retry), small = true) { ok = null; heard = null; listen() } }
                    Box(Modifier.weight(1f)) { Primary(stringResource(R.string.speak_line_next), small = true) { onDone() } }
                }
            }
            else -> {
                // 스스로 판정: 먼저 말해 보고, 원래 소리를 들은 뒤 (복습 말하기와 같은 순서)
                Text(stringResource(R.string.speak_self), style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center)
                if (!revealed) Secondary(stringResource(R.string.speak_reveal), small = true) { revealed = true; audio?.let { s.narrator.play(it) } }
                else {
                    Text(sentence, style = Type.body.of(learn), color = p.ink, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.speak_again), small = true) { s.cue(false); onDone() } }
                        Box(Modifier.weight(1f)) { Primary(stringResource(R.string.speak_ok), small = true) { s.cue(true); onDone() } }
                    }
                }
            }
        }
    }
}

/**
 * 답장 말해 보기 (답장 한 턴을 보낸 뒤 한 번, 건너뛰기 있음): 방금 쓴 배우는 말 문장을 소리 내어.
 * 문장은 도움 사다리만큼 ([Support.replySpeak]): A1–A2 보이고, B1 흐리게, B2 감추고 힌트로. 뜻(읽는 말)은 늘 보인다.
 */
@Composable
fun ReplySpeak(s: AppState, r: Route.Play, turn: Int, turns: Int, onNext: () -> Unit) {
    val p = Ink.palette
    val id = r.room.series
    val (_, letter) = s.letterOf(r.room)
    val view = s.room(id)
    val (line, tri) = io.github.graviton94.todayletters.core.Exercises.replyFor(letter, view.learn, turn)
    val sentence = remember(line) { Breaks.plain(line).trim() }
    val how = Support.replySpeak(s.support(id).band)
    var hinted by remember(turn) { mutableStateOf(false) }
    var spoken by remember(turn) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(turn + 1, turns, onClose = onNext, count = if (turns > 1) stringResource(R.string.ladder_turn, turn + 1, turns) else null)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            PracticeTitle("SPEAK", stringResource(R.string.reply_speak_title))
            Text(stringResource(R.string.reply_speak_d), style = Type.body.ui(), color = p.inkSoft)
            Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(quoted(tri[view.read], view.read), style = Type.heading.of(view.read), color = p.ink)
                val visible = how != Support.Speak.HIDDEN || hinted || spoken
                if (visible) Text(sentence, style = Type.title.copy(fontFamily = Faces.display).of(view.learn),
                    color = if (how == Support.Speak.FADED && !spoken) p.ink.copy(alpha = 0.35f) else p.ink)
                else Text(stringResource(R.string.talk_hint), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.giltText,
                    modifier = Modifier.heightIn(min = 44.dp).pressable { hinted = true }.padding(vertical = Tokens.Space.s2))
            }
            if (!spoken) SpeakLine(s, sentence, view.learn, null, key = "reply$turn") { spoken = true }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (spoken) Primary(stringResource(if (turn < turns - 1) R.string.ladder_next_turn else R.string.speak_line_next)) { onNext() }
            else Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(role = Role.Button) { onNext() }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reply_speak_skip), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.inkSoft)
            }
        }
    }
}

/** 발음 줄과 한국어 소리 규칙 줄 (낱말 이름표 · 낱말 카드). */
@Composable
internal fun PronLines(line: String, note: String) {
    val p = Ink.palette
    if (line.isNotEmpty()) Text(line, style = Type.small, color = p.inkSoft)
    if (note.isNotEmpty()) Text(note, style = Type.small.ui(), color = p.giltText)
}
