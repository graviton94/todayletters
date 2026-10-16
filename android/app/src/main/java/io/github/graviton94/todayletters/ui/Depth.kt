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
import io.github.graviton94.todayletters.core.TalkTurn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
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
    fun listen(from: Int = 0, speed: Float? = null) { clips.getOrNull(from)?.let { s.narrator.play(it, speed) { if (from + 1 < clips.size) listen(from + 1, speed) } } }

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
                    if (clips.isNotEmpty()) ListenButtons(play = { sp -> listen(0, sp) }, label = stringResource(R.string.check_listen))
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
 * 오늘의 회화 (메신저, 시안 talkchat.html): 윗줄은 초상 · 이름 (앱 글자 언어) · 장소 · 날짜 · ‘이 앱이 쓴 대화’ · 건너뛰기.
 * 가운데는 말풍선 (상대 왼쪽: 배우는 말 + 읽는 말 · 소리가 있으면 듣기, 나 오른쪽, 지금 내 차례는 점선), 새 말풍선으로 저절로 내려간다.
 * 아래 칸 (높이 고정, 자판 자리) 에 도움 · 녹음을 둔다. 내 차례 한 줄의 상태는 core 의 [TalkTurn] (도움 사다리 [Support.talk] 만큼).
 * 모든 줄을 마치면 아래 칸이 ‘마치기’ 단추가 된다.
 */
@Composable
fun TalkStep(s: AppState, r: Route.Play, onClose: () -> Unit) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val work = s.work(id)
    val view = s.room(id)
    val talk = letter.talk
    val lines = talk?.lines.orEmpty()
    val help = Support.talk(s.support(id).band)
    val known = remember(id, s.version) { s.familiarity(id) }
    val todays = remember(letter.id, view.learn) { talk?.wordsFor(view.learn, work.series.original).orEmpty().map { Breaks.plain(it) } }
    // 열린 줄 수 (상대 줄은 저절로, 내 줄은 마친 뒤) · 내 줄마다 맞게 마쳤는가 ("1" · "0")
    var shown by rememberSaveable(letter.id) { mutableIntStateOf(0) }
    var marks by rememberSaveable(letter.id) { mutableStateOf("") }
    fun audio(i: Int): String? = talk?.audioName(i, view.learn)?.let { s.narrator.path(id, chapter, letter.id, it) }?.takeIf { s.narrator.has(it) }
    LaunchedEffect(shown, lines.size) {
        if (shown < lines.size && !lines[shown].me) { delay(if (shown == 0) 300 else 900); shown++ }
    }
    LaunchedEffect(shown) { val i = shown - 1; if (i >= 0 && !lines[i].me) audio(i)?.let { s.narrator.play(it) } }
    DisposableEffect(letter.id) { onDispose { s.narrator.stop() } }
    val done = shown >= lines.size && lines.isNotEmpty()

    // 지금 내 차례 줄의 상태
    val cur = shown.takeIf { it < lines.size && lines[it].me }
    var st by remember(letter.id, cur, help, view.learn) { mutableStateOf(cur?.let { startTurn(lines[it], it, help, view.learn, known) }) }
    var heard by remember(letter.id, cur) { mutableStateOf<String?>(null) }
    fun send(e: TalkTurn.Event) { st = st?.let { TalkTurn.reduce(it, e) } }
    val phase = st?.phase
    LaunchedEffect(phase, cur) {
        when (phase) {
            TalkTurn.Phase.PASSED -> { s.cue(true); delay(900); send(TalkTurn.Event.Continue) }
            TalkTurn.Phase.SELF -> cur?.let { audio(it) }?.let { s.narrator.play(it) }
            TalkTurn.Phase.REVEALED -> cur?.let { audio(it) }?.let { s.narrator.play(it) }
            TalkTurn.Phase.DONE -> { val t = st; if (cur != null && t != null) { marks += if (t.passed) "1" else "0"; shown = cur + 1 } }
            else -> Unit
        }
    }

    // 녹음: 폰의 음성 인식 (그 언어, 가능하면 폰 안에서). 없거나 마이크를 허락하지 않으면 스스로 판정
    val ear = rememberEar(view.learn)
    var denied by remember { mutableStateOf(false) }
    fun selfJudge() { send(TalkTurn.Event.SelfStart) }
    fun listen() {
        val t = st ?: return
        if (!t.canRecord) return
        ear.start(onResult = { all ->
            heard = all.firstOrNull()
            val ok = all.any { SpeechMatch.matches(t.sentence, it) }
            if (!ok) s.cue(false)
            send(TalkTurn.Event.Heard(ok))
        }, onError = { selfJudge() })
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else { denied = true; selfJudge() }
    }
    fun record() {
        val t = st ?: return
        if (!t.canRecord) return
        when {
            !ear.available || denied -> selfJudge()
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED -> listen()
            else -> ask.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(shown, phase, st?.placed, st?.filled, st?.hints) { scroll.animateScrollTo(scroll.maxValue) }
    val name = work.name[uiLang()]

    Column(Modifier.fillMaxSize().background(p.paper)) {
        // 윗줄: 닫기 · 초상 · 이름 · 장소 · 날짜 · 이 앱이 쓴 대화 · 건너뛰기
        Row(Modifier.fillMaxWidth().padding(end = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            IconButton(stringResource(R.string.close), onClick = onClose) { Text("×", style = Type.title, color = p.ink) }
            Portrait(work.portrait, name, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, maxLines = 1)
                Text(listOf(dateLine(letter.date, ""), stringResource(R.string.talk_written_short)).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                    style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 2)
            }
            if (!done) TextLink(stringResource(R.string.reply_speak_skip)) { s.narrator.stop(); s.skipStep(r.room, ReplyMode.TALK) }
        }
        Hair()
        // 말풍선
        Column(
            Modifier.weight(1f).fillMaxWidth().desk(p.paper, p.lamp, 0.35f).verticalScroll(scroll).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            talk?.scene?.get(shortLang(view.read))?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            var mine = 0
            lines.take(shown).forEachIndexed { i, line ->
                if (line.me) {
                    val ok = marks.getOrNull(mine++) == '1'
                    MyBubble(Breaks.plain(line.text[view.learn]), line.text[view.read], view.learn, view.read, todays, check = ok)
                } else TheirBubble(Breaks.plain(line.text[view.learn]), line.text[view.read], view.learn, view.read, todays,
                    audio(i)?.let { a -> @Composable { ListenButtons(play = { sp -> s.narrator.play(a, sp) }) } })
            }
            st?.let { t -> CurrentBubble(t, cur?.let { lines[it].text[view.read] }.orEmpty(), view.learn, view.read) }
        }
        // 아래 칸 (높이 고정)
        Column(
            Modifier.fillMaxWidth().height(TRAY).background(p.paper).border(Tokens.Stroke.hair, p.hair).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2, Alignment.CenterVertically),
        ) {
            val t = st
            when {
                done -> Primary(stringResource(R.string.talk_finish)) { s.narrator.stop(); s.replyAndContinue(r.room, ReplyMode.TALK) }
                t != null -> TalkTray(t, view.learn, ear.available && !denied, ear.listening, heard,
                    cur?.let { audio(it) }?.let { a -> @Composable { ListenButtons(play = { sp -> s.narrator.play(a, sp) }) } },
                    onPick = { i ->
                        val before = st
                        send(TalkTurn.Event.Pick(i))
                        val after = st
                        if (after != null && before != null) {
                            if (after.rejected == i && after.shake != before.shake) s.cue(false) else if (after.used.size > before.used.size) s.cue(true)
                        }
                    },
                    onHint = { send(TalkTurn.Event.Hint) }, onRecord = { record() },
                    onSelf = { ok -> if (!ok) s.cue(false); send(TalkTurn.Event.SelfJudge(ok)) }, onContinue = { send(TalkTurn.Event.Continue) })
                else -> Unit
            }
        }
    }
}

/** 아래 칸 높이 (자판 자리처럼 고정: 대화가 길어져도 화면이 흔들리지 않게). */
private val TRAY = 216.dp

/** 내 차례 줄 하나를 시작: BLANK 이면 빈칸 (그 줄의 덩어리, 잘 아는 것 먼저) 과 보기 칩 (정답 · 적힌 틀린 보기) 을 고른다. */
private fun startTurn(line: TalkLine, index: Int, help: Support.Talk, learn: Lang, known: (String) -> Int): TalkTurn.State {
    val sentence = Breaks.plain(line.text[learn]).trim()
    val seed = sentence.hashCode() + index
    if (help != Support.Talk.BLANK) return TalkTurn.start(help, sentence, seed = seed)
    val focusAll = line.focus[learn].orEmpty().map { Breaks.plain(it) }
    val focus = Ladder.focusIn(sentence, focusAll)
    val chunk = Support.pickBlank(focus, known)
    val hole = chunk?.let { Ladder.find(sentence, it) }
        ?: Ladder.holes(sentence, emptyList(), 1) { w -> Support.blankScore(if (w.length >= 2) 1 + w.length / 4 else 0, known(w)) }.firstOrNull()
        ?: return TalkTurn.start(help, sentence, seed = seed)
    val answer = sentence.substring(hole)
    val k = focusAll.indexOfFirst { it == chunk }
    val authored = line.wrong[learn]?.getOrNull(k).orEmpty().map { Breaks.plain(it) }.filter { !it.equals(answer, ignoreCase = true) }
    val pool = Ladder.tokens(sentence).map { Ladder.core(it) }.filter { it.isNotEmpty() && !it.equals(answer, ignoreCase = true) }
    return TalkTurn.start(help, sentence, hole, Ladder.chips(answer, authored, pool, seed), seed)
}

/** 말풍선 글에 오늘 낱말 밑줄. */
private fun underlined(text: String, todays: List<String>) = buildAnnotatedString {
    append(text)
    if (todays.isNotEmpty()) wordMarks(text, todays).forEach { (rg, _) -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), rg.first, rg.last + 1) }
}

/** 상대 말풍선 (왼쪽): 배우는 말 (오늘 낱말 밑줄) · 읽는 말, 소리가 있으면 듣기. */
@Composable
internal fun TheirBubble(learnText: String, readText: String, learn: Lang, read: Lang, todays: List<String>, listen: (@Composable () -> Unit)?) {
    val p = Ink.palette
    Box(Modifier.fillMaxWidth(0.9f), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.clip(RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)).background(p.slip).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(underlined(learnText, todays), style = Type.body.of(learn), color = p.slipInk)
            if (read != learn) Text(readText, style = Type.small.of(read), color = p.slipSoft)
            // 듣기 단추 둘 (스피커 · 0.7×): 말풍선 안 아래 (시안 slow.html)
            if (listen != null) Box(Modifier.padding(top = 4.dp)) { listen() }
        }
    }
}

/** 마친 내 말풍선 (오른쪽): 배우는 말 · 읽는 말, 맞게 말했으면 금빛 체크. */
@Composable
private fun MyBubble(learnText: String, readText: String, learn: Lang, read: Lang, todays: List<String>, check: Boolean) {
    val p = Ink.palette
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp)).background(p.fill).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(underlined(learnText, todays), style = Type.body.of(learn), color = p.onFill, modifier = Modifier.weight(1f, fill = false))
                if (check) Text("✓", style = Type.body, color = p.gilt)
            }
            if (read != learn) Text(readText, style = Type.small.of(read), color = p.onFill.copy(alpha = 0.75f))
        }
    }
}

/**
 * 지금 내 차례 말풍선 (오른쪽, 점선): 상태 [t] 대로. SHADOW · 채운 BLANK 는 원문 + 번역, 빈 BLANK 는 빈칸 + 번역,
 * CHIPS · MEANING 은 뜻 + 쌓은 글 (첫 낱말 · 힌트). 맞으면 원문 + 금빛 체크.
 */
@Composable
private fun CurrentBubble(t: TalkTurn.State, readText: String, learn: Lang, read: Lang) {
    val p = Ink.palette
    val meaningFirst = (t.help == Support.Talk.CHIPS || t.help == Support.Talk.MEANING) && !t.fullLine
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.widthIn(max = 320.dp).dashedRound(p.giltText).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (meaningFirst) Text(readText, style = Type.body.of(read), color = p.ink)
            val style = Type.body.copy(fontFamily = Faces.display).of(learn)
            val blank = t.blank
            when {
                t.fullLine -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t.sentence, style = style, color = p.ink, modifier = Modifier.weight(1f, fill = false))
                    if (t.passed) Text("✓", style = Type.body, color = p.giltText)
                }
                t.help == Support.Talk.BLANK && blank != null -> Text(buildAnnotatedString {
                    append(t.sentence.substring(0, blank.first))
                    withStyle(SpanStyle(color = p.giltText)) { append("＿".repeat((t.answer?.length ?: 3).coerceIn(3, 8))) }
                    append(t.sentence.substring(blank.last + 1))
                }, style = style, color = p.ink)
                t.built.isNotEmpty() -> Text(t.built + " …", style = style, color = p.ink)
                else -> Text("…", style = style, color = p.hideInk)
            }
            if (!meaningFirst && read != learn) Text(readText, style = Type.small.of(read), color = p.inkSoft)
        }
    }
}

/**
 * 아래 칸: 도움 한 줄 · 칩 (BLANK 보기 · CHIPS 조각) · 듣기 · 힌트 · 녹음. 녹음은 [TalkTurn.State.canRecord] 일 때만 (막히면 회색 + 까닭 한 줄).
 * 틀리면 ‘다시 해 봐요’, 두 번 틀리면 원문과 ‘다음으로’, 인식이 없으면 스스로 판정 (맞게 말했어요 / 다시).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalkTray(t: TalkTurn.State, learn: Lang, recognizer: Boolean, listening: Boolean, heard: String?, onListen: (@Composable () -> Unit)?,
                     onPick: (Int) -> Unit, onHint: () -> Unit, onRecord: () -> Unit, onSelf: (Boolean) -> Unit, onContinue: () -> Unit) {
    val p = Ink.palette
    when (t.phase) {
        TalkTurn.Phase.PASSED, TalkTurn.Phase.DONE -> Text(stringResource(R.string.speak_line_ok), style = Type.body.ui(), color = p.correct,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        TalkTurn.Phase.REVEALED -> {
            Text(stringResource(R.string.talk_revealed), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                onListen?.invoke()
                Box(Modifier.weight(1f)) { Primary(stringResource(R.string.speak_line_next)) { onContinue() } }
            }
        }
        TalkTurn.Phase.SELF -> {
            Text(stringResource(R.string.speak_self), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                onListen?.invoke()
                Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.speak_again), small = true) { onSelf(false) } }
                Box(Modifier.weight(1f)) { Primary(stringResource(R.string.speak_ok), small = true) { onSelf(true) } }
            }
        }
        else -> {
            Text(stringResource(when (t.help) {
                Support.Talk.SHADOW -> R.string.talk_tray_shadow
                Support.Talk.BLANK -> R.string.talk_tray_blank
                Support.Talk.CHIPS -> R.string.talk_tray_chips
                Support.Talk.MEANING -> R.string.talk_tray_meaning
            }), style = Type.capsSm.ui(), color = p.giltText)
            // 칩: 고르기 · 쌓기가 남았을 때만
            if (t.phase == TalkTurn.Phase.BUILD) FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                t.chips.forEachIndexed { i, c ->
                    if (i in t.used) return@forEachIndexed
                    val bad = t.rejected == i
                    val lit = t.hinted == i
                    Box(Modifier.heightIn(min = 44.dp).then(if (bad) Modifier.shake(t.shake) else Modifier)
                        .background(if (bad) p.wrong.copy(alpha = 0.10f) else if (lit) p.gilt.copy(alpha = 0.18f) else Color.Transparent)
                        .border(if (lit) 2.dp else 1.dp, if (bad) p.wrong else if (lit) p.giltText else p.line)
                        .pressable(role = Role.Button) { onPick(i) }.padding(horizontal = Tokens.Space.s3),
                        contentAlignment = Alignment.Center) {
                        Text(c, style = Type.body.of(learn), color = if (bad) p.wrong else p.ink)
                    }
                }
            }
            if (t.phase == TalkTurn.Phase.RETRY) Text(listOfNotNull(stringResource(R.string.talk_retry), heard?.let { stringResource(R.string.speak_heard, it) }).joinToString(" · "),
                style = Type.small.ui(), color = p.wrong, maxLines = 2)
            // 듣기 · 힌트 · 녹음
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3, Alignment.CenterHorizontally)) {
                val canListen = onListen != null && (t.help == Support.Talk.SHADOW || (t.fullLine && t.canRecord))
                if (canListen) onListen!!.invoke()
                if (t.hintable) Box(Modifier.heightIn(min = 48.dp).border(1.dp, p.line).pressable(role = Role.Button) { onHint() }.padding(horizontal = Tokens.Space.s3),
                    contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BulbGlyph(p.giltText)
                        Text(stringResource(R.string.talk_hint), style = Type.small.ui(), color = p.ink)
                    }
                }
                RecordButton(t.canRecord, listening, stringResource(when {
                    !recognizer -> R.string.talk_said
                    listening -> R.string.speak_listening
                    t.help == Support.Talk.SHADOW -> R.string.talk_record_repeat
                    else -> R.string.speak_mic
                }), onRecord)
            }
            if (!t.canRecord) Text(stringResource(if (t.help == Support.Talk.BLANK) R.string.talk_need_blank else R.string.talk_need_build),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

/** 녹음 단추 (마이크): [enabled] 가 아니면 회색 (보이되 눌리지 않음). */
@Composable
private fun RecordButton(enabled: Boolean, listening: Boolean, label: String, onClick: () -> Unit) {
    val p = Ink.palette
    Row(Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
        .background(if (!enabled) p.hide else if (listening) p.wrong else p.fill)
        .semantics { contentDescription = label }
        .pressable(enabled = enabled, role = Role.Button) { onClick() }.padding(horizontal = Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        val c = if (enabled) p.onFill else p.hideInk
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val w = size.width
            drawRoundRect(c, Offset(w * 0.35f, 0f), androidx.compose.ui.geometry.Size(w * 0.3f, w * 0.62f), androidx.compose.ui.geometry.CornerRadius(w * 0.15f))
            drawArc(c, 0f, 180f, false, Offset(w * 0.2f, w * 0.25f), androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawLine(c, Offset(w / 2, w * 0.8f), Offset(w / 2, w), 2.dp.toPx())
        }
        Text(label, style = Type.body.ui(), color = c, maxLines = 1)
    }
}

/** 점선 둥근 테두리 (지금 내 차례 말풍선). */
private fun Modifier.dashedRound(c: Color) = drawBehind {
    drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/** 폰의 음성 인식 한 벌 (그 언어, 가능하면 폰 안에서). [available] 이 false 면 쓰지 않는다 (스스로 판정). */
private class Ear(private val ctx: android.content.Context, private val tag: String) {
    val available: Boolean = runCatching { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)
    var listening by mutableStateOf(false)
        private set
    private var rec: android.speech.SpeechRecognizer? = null

    fun start(onResult: (List<String>) -> Unit, onError: () -> Unit) {
        val r = rec ?: runCatching { android.speech.SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull()?.also { rec = it } ?: run { onError(); return }
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(b: android.os.Bundle) {
                listening = false
                onResult(b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            }
            override fun onError(error: Int) { listening = false; onError() }
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
        runCatching { r.startListening(i) }.onFailure { listening = false; onError() }
    }

    fun destroy() { runCatching { rec?.destroy() }; rec = null }
}

@Composable
private fun rememberEar(learn: Lang): Ear {
    val ctx = LocalContext.current
    val tag = when (learn) { Lang.FR -> "fr-FR"; Lang.EN -> "en-US"; Lang.KO -> "ko-KR"; Lang.DE -> "de-DE"; Lang.NL -> "nl-NL"; Lang.JA -> "ja-JP" }
    val ear = remember(learn) { Ear(ctx, tag) }
    DisposableEffect(ear) { onDispose { ear.destroy() } }
    return ear
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
                    if (audio != null) ListenButtons(play = { sp -> s.narrator.play(audio, sp) })
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

/** 힌트 표시 (그린 전구): 글자 그림(이모지) 대신. */
@Composable
internal fun BulbGlyph(c: Color) = androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
    val w = size.width
    val st = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(c, w * 0.3f, Offset(w / 2, w * 0.38f), style = st)
    drawLine(c, Offset(w * 0.38f, w * 0.8f), Offset(w * 0.62f, w * 0.8f), 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(c, Offset(w * 0.42f, w * 0.95f), Offset(w * 0.58f, w * 0.95f), 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
}
