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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Ladder
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Support
import io.github.graviton94.todayletters.core.TalkLine
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

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
    val answeredAt by rememberClock(letter.id to k to over, s.reducedMotion || !over, 1600, start = over)
    // 소리의 주인: 이 화면의 지금 질문 (화면 안의 단계, 시안 하루 흐름 ①)
    var me by remember(letter.id) { mutableStateOf<Any?>(s.narrator.scene) }
    DisposableEffect(letter.id) { onDispose { s.narrator.stop(me) } }

    // 지문 듣기: 근거 메시지의 낭독을 차례로 (없으면 단추 없음)
    val clips = remember(letter.id, k, view.learn) {
        c?.evidence.orEmpty().map { i -> s.narrator.path(id, chapter, letter.id, "m${i + 1}_${view.learn.code}") }.filter { s.narrator.has(it) }
    }
    // 다음 메시지는 앞 메시지를 끝까지 들었을 때만 (멈추거나 화면을 옮기면 잇지 않는다)
    fun listen(from: Int = 0, speed: Float? = null, end: (Boolean) -> Unit = {}) {
        val clip = clips.getOrNull(from) ?: return end(true)
        s.narrator.playThen(clip, speed, owner = me) { ok -> if (ok && from + 1 < clips.size) listen(from + 1, speed, end) else end(ok) }
    }
    // 질문이 뜨면 지문을 한 번 저절로 (자동 낭독 설정 · 기본 듣기 속도, 소리 파일이 없으면 조용히)
    // 질문마다 그 질문의 소리만 받고, 덮개 (보상 · 묻는 창 · 도움말) 가 걷힌 뒤에
    LaunchedEffect(letter.id, k) { val o = s.narrator.step("check" to k); me = o; if (s.app.sound) s.autoPlay(o) { end -> listen(end = end) } }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(if (over) k + 1 else k, n, onClose = onClose, count = "${k + 1} / $n",
            onSkip = if (over && k == n - 1) null else ({ s.narrator.stop(); s.skipStep(r.room, ReplyMode.CHECK) }))
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
                passage.forEach { m ->
                    val t = Breaks.plain(m[view.learn])
                    val mark = c.mark?.lines?.get(view.learn)?.let { Breaks.plain(it) }?.takeIf { it.isNotBlank() }
                    val at0 = mark?.let { t.indexOf(it, ignoreCase = true) } ?: -1
                    // 답이 나면 (시안 rw2 1): 근거 대목에 불이 들고 (나머지는 옅게) 그 아래 금선 밑줄이 왼쪽에서 오른쪽으로. 근거 표시가 없는 문제면 지문 전체가 근거
                    // 근거 표시가 있는데 이 메시지에 없으면 이 메시지는 옅게만
                    val range = if (at0 >= 0) at0 until at0 + mark!!.length else if (mark == null) t.indices else IntRange(0, -1)
                    EvidenceText(androidx.compose.ui.text.AnnotatedString(t), if (mark == null) null else range, over,
                        Type.target.copy(fontFamily = Faces.display).of(view.learn), p.ink, p.inkSoft.copy(alpha = 0.8f), p.giltText, s.reducedMotion)
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
                // 맞힘 · 틀림 알림은 다른 퀴즈 (복습 뜻 고르기 · 낱말 맞추기) 와 같게: 효과음 · 진동, 맞으면 금빛 원 + ‘맞았어요.’
                // 지문 낭독이 흐르는 중이면 먼저 멈춘다 (효과음이 낭독에 묻혀 맞혀도 아무 일 없는 것처럼 들리지 않게)
                s.narrator.stop()
                if (o == right) {
                    s.cue(true)
                    if (s.app.haptics) {
                        if (android.os.Build.VERSION.SDK_INT >= 30) view0.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } else { missed = missed + o; shakeKey++; buzzWrong(view0, s.app.haptics); s.cue(false) }
            }
            // 맞음: 고른 보기 옆의 금선 체크 + 한 줄 (근거가 지문에 있으면 ‘근거는 위의 밑줄’)
            if (picked == right && right.isNotEmpty()) Text(stringResource(if (passage.isNotEmpty()) R.string.verdict_evidence else R.string.review_right),
                style = Type.body.ui(), color = p.giltText, modifier = Modifier.rise(win(answeredAt, 700, 900), blur = 0f))
            if (!over && missed.isNotEmpty()) Text(stringResource(R.string.check_wrong), style = Type.small.ui(), color = p.wrong)
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (k < n - 1) MainButton(stringResource(R.string.check_next), enabled = over, reason = stringResource(R.string.need_answer)) { s.narrator.stop(); at = k + 1 }
            else MainButton(stringResource(R.string.play_finish), enabled = over, reason = stringResource(R.string.need_answer)) { s.narrator.stop(); s.replyAndContinue(r.room, ReplyMode.CHECK) }
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
    // 회화를 다 한 뒤 ‘이번엔 내 이야기로’ (ui/Mine.kt, 데이터가 있을 때만): 그 한 장을 마치거나 건너뛰면 걸음을 마친다
    var mineOpen by rememberSaveable(letter.id) { mutableStateOf(false) }
    if (mineOpen) { MineStep(s, r) { s.replyAndContinue(r.room, ReplyMode.TALK) }; return }
    val known = remember(id, s.version) { s.familiarity(id) }
    val todays = remember(letter.id, view.learn) { talk?.wordsFor(view.learn, work.series.original).orEmpty().map { Breaks.plain(it) } }
    // 열린 줄 수 (상대 줄은 저절로, 내 줄은 마친 뒤) · 내 줄마다 맞게 마쳤는가 ("1" · "0")
    var shown by rememberSaveable(letter.id) { mutableIntStateOf(0) }
    var marks by rememberSaveable(letter.id) { mutableStateOf("") }
    fun audio(i: Int): String? = talk?.audioName(i, view.learn)?.let { s.narrator.path(id, chapter, letter.id, it) }?.takeIf { s.narrator.has(it) }
    // 소리의 주인: 이 화면의 지금 차례 (화면 안의 단계, 시안 하루 흐름 ①). 차례가 바뀌면 앞 차례의 늦은 소리는 받지 않는다
    var me by remember(letter.id) { mutableStateOf<Any?>(s.narrator.scene) }
    // 저절로 들은 상대 줄 수: 상대 줄 [shown]−1 의 소리가 끝나면 (끝까지 · 멈춤 · 소리 없음 모두) [shown] 이 된다.
    // 내 줄의 먼저 듣기는 이것을 기다린다 (2026-10-25 폰 시험: 내 줄 먼저 듣기가 막 시작한 상대 줄 소리를 바로 잘라 상대 줄이 들리지 않았다)
    var spoken by rememberSaveable(letter.id) { mutableIntStateOf(0) }
    LaunchedEffect(shown, lines.size) {
        // 앞 줄 소리(상대 줄 · 내 줄 정답 소리)가 끝난 뒤에 다음 상대 줄 (그 줄의 소리가 앞 소리를 자르지 않게).
        // 무언가 화면을 덮고 있으면 (보상 · 걸음 마침 한 장 · 묻는 창 · 도움말 · 설정) 걷힐 때까지 기다린다
        if (shown < lines.size && !lines[shown].me) { awaitAnswerSound(s.narrator, if (shown == 0) 300 else 900); s.uncovered(); shown++ }
    }
    // 상대 줄은 저절로: 이 화면 · 이 차례의 소리로만 (화면을 떠난 뒤 다음 줄 차례가 와도 새 화면 소리를 자르지 않게),
    // 덮개가 걷힌 뒤에 (걷히는 사이 다시 덮이면 다시 걷힐 때, AppState.autoPlay), 끝날 때까지 기다렸다가 [spoken]
    LaunchedEffect(shown) {
        val owner = s.narrator.step("talk" to shown)
        me = owner
        val i = shown - 1
        val a = if (i >= 0 && !lines[i].me) audio(i) else null
        if (a != null) s.autoPlay(owner) { end -> s.narrator.playThen(a, owner = owner, onEnd = end) }
        spoken = maxOf(spoken, shown)
    }
    DisposableEffect(letter.id) { onDispose { s.pcm.onFrame = null; s.pcm.stop(); s.recorder.release(); s.narrator.stop(me) } }
    val done = shown >= lines.size && lines.isNotEmpty()

    // 지금 내 차례 줄의 상태 (원래 소리가 있으면 문장이 다 보일 때 먼저 듣기, core 의 TalkTurn.Phase.LISTEN)
    val cur = shown.takeIf { it < lines.size && lines[it].me }
    var st by remember(letter.id, cur, help, view.learn) { mutableStateOf(cur?.let { startTurn(lines[it], it, help, view.learn, known, listen = audio(it) != null) }) }
    fun send(e: TalkTurn.Event) { st = st?.let { TalkTurn.reduce(it, e) } }
    val phase = st?.phase

    // 말하기: 따라 읽기와 같은 부품 ([ShadowTake]): 원래 소리 듣기 · 마이크 녹음 (기기 안) · 억양 · 리듬 채점, 편지 걸음처럼 목소리가 들어가면 통과
    // (원래 소리가 없는 줄도 같이: 채점 없이 목소리만). 폰의 음성 인식 서비스는 쓰지 않는다
    val take = cur?.let { i ->
        val file = remember(letter.id, i) { s.talkTake(id, chapter, letter.id, i).also { it.delete() } }
        val line = Breaks.plain(lines[i].text[view.learn]).trim()
        rememberShadowTake(s, audio(i), 0, Int.MAX_VALUE, file, step = true, key = Triple(letter.id, i, view.learn),
            onVoice = { v -> send(TalkTurn.Event.Heard(v)) }, sayLike = s.sayLike(id, line), text = line, lang = view.learn)
    }
    LaunchedEffect(phase, cur) {
        when (phase) {
            // 먼저 듣기: 상대 줄 소리가 끝난 뒤 (덮개가 걷히면) 내 문장을 한 번 (끝까지 들으면 녹음이 열린다)
            TalkTurn.Phase.LISTEN -> {
                val at = cur ?: return@LaunchedEffect
                androidx.compose.runtime.snapshotFlow { spoken }.first { it >= at }
                val o = me
                take?.let { tk -> s.autoPlay(o) { end -> tk.listen(s, o, end) } }
            }
            TalkTurn.Phase.REVEALED -> { s.uncovered(); cur?.let { audio(it) }?.let { s.narrator.play(it, owner = me) } }
            TalkTurn.Phase.DONE -> { val t = st; if (cur != null && t != null) { marks += if (t.passed) "1" else "0"; shown = cur + 1 } }
            else -> Unit
        }
    }
    // 끝까지 들었으면 녹음을 연다 (먼저 듣기를 마침)
    LaunchedEffect(take, take?.heard) { if (take?.heard == true) send(TalkTurn.Event.Listened) }

    val scroll = rememberScrollState()
    // 새 말풍선 · 칩이 생기면 끝까지 내려간다 (판은 아래 고정 칸이라 내용만 굴린다)
    LaunchedEffect(shown, phase, st?.placed, st?.filled, st?.hints) {
        androidx.compose.runtime.snapshotFlow { scroll.maxValue }.collectLatest { scroll.animateScrollTo(it) }
    }
    val name = work.name[uiLang()]

    Column(Modifier.fillMaxSize().background(p.paper)) {
        // 윗줄: 닫기 · 초상 · 이름 · 장소 · 날짜 · 이 앱이 쓴 대화 · 건너뛰기 (위 오른쪽 글자 하나)
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
        // 말풍선 (이것만 굴린다)
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
                    audio(i)?.let { a -> @Composable { ListenButtons(play = { sp -> s.narrator.play(a, sp, owner = me) }) } })
            }
            st?.let { t ->
                CurrentBubble(t, cur?.let { lines[it].text[view.read] }.orEmpty(), view.learn, view.read)
                // 칩 · 힌트 (고르기 · 쌓기 단계): 내 말풍선 바로 아래 (아래 칸은 높이 고정)
                TalkPicks(t, view.learn,
                    onPick = { i ->
                        val before = st
                        send(TalkTurn.Event.Pick(i))
                        val after = st
                        if (after != null && before != null) {
                            if (after.rejected == i && after.shake != before.shake) s.cue(false) else if (after.used.size > before.used.size) s.cue(true)
                        }
                    },
                    onHint = { send(TalkTurn.Event.Hint) })
            }
        }
        // 판 · 아래 칸 (말하기 일곱 곳 공통, 높이 고정): 녹음 전 원래 소리 점선은 녹음할 수 있고 원래 소리를 들을 수 있을 때만
        val t = st
        val listenOk = t != null && (t.help == Support.Talk.SHADOW || t.fullLine)
        SpeakPanel(s, take, preview = listenOk, canListen = listenOk, canRecord = t?.canRecord == true || t?.phase == TalkTurn.Phase.LISTEN,
            why = when (t?.phase) {
                TalkTurn.Phase.BUILD -> stringResource(if (t?.help == Support.Talk.BLANK) R.string.talk_need_blank else R.string.talk_need_build)
                TalkTurn.Phase.REVEALED -> stringResource(R.string.talk_revealed)
                TalkTurn.Phase.PASSED, TalkTurn.Phase.DONE -> stringResource(R.string.shadow_check)
                else -> null
            }) {
            val next = { s.narrator.stop(); s.recorder.stopPlaying(); send(TalkTurn.Event.Continue) }
            when {
                done -> MainButton(stringResource(R.string.talk_finish)) {
                    s.narrator.stop()
                    if (s.mineDue(r.room)) mineOpen = true else s.replyAndContinue(r.room, ReplyMode.TALK)
                }
                t != null -> MainButton(stringResource(R.string.speak_line_next),
                    enabled = t.phase == TalkTurn.Phase.PASSED || t.phase == TalkTurn.Phase.REVEALED,
                    reason = stringResource(if (take?.scoring == true) R.string.need_scoring else R.string.need_record)) { next() }
                else -> MainButton(stringResource(R.string.speak_line_next), enabled = false) {}
            }
        }
    }
}

/** 내 차례 줄 하나를 시작: BLANK 이면 빈칸 (그 줄의 덩어리, 잘 아는 것 먼저) 과 보기 칩 (정답 · 적힌 틀린 보기) 을 고른다. */
private fun startTurn(line: TalkLine, index: Int, help: Support.Talk, learn: Lang, known: (String) -> Int, listen: Boolean = false): TalkTurn.State {
    val sentence = Breaks.plain(line.text[learn]).trim()
    val seed = sentence.hashCode() + index
    if (help != Support.Talk.BLANK) return TalkTurn.start(help, sentence, seed = seed, listen = listen)
    val focusAll = line.focus[learn].orEmpty().map { Breaks.plain(it) }
    val focus = Ladder.focusIn(sentence, focusAll)
    val chunk = Support.pickBlank(focus, known)
    val hole = chunk?.let { Ladder.find(sentence, it) }
        ?: Ladder.holes(sentence, emptyList(), 1) { w -> Support.blankScore(if (w.length >= 2) 1 + w.length / 4 else 0, known(w)) }.firstOrNull()
        ?: return TalkTurn.start(help, sentence, seed = seed, listen = listen)
    val answer = sentence.substring(hole)
    val k = focusAll.indexOfFirst { it == chunk }
    val authored = line.wrong[learn]?.getOrNull(k).orEmpty().map { Breaks.plain(it) }.filter { !it.equals(answer, ignoreCase = true) }
    val pool = Ladder.tokens(sentence).map { Ladder.core(it) }.filter { it.isNotEmpty() && !it.equals(answer, ignoreCase = true) }
    return TalkTurn.start(help, sentence, hole, Ladder.chips(answer, authored, pool, seed), seed, listen)
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
 * 내 차례의 칩 (BLANK 보기 · CHIPS 조각) · 힌트: 내 말풍선 바로 아래 (아래 칸은 말하기 판 · 단추로 높이 고정, [SpeakPanel]).
 * 녹음은 [TalkTurn.State.canRecord] 일 때만 (그 전에는 아래 칸의 안내 한 줄이 까닭).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalkPicks(t: TalkTurn.State, learn: Lang, onPick: (Int) -> Unit, onHint: () -> Unit) {
    val p = Ink.palette
    if (t.phase == TalkTurn.Phase.BUILD) {
        Text(stringResource(when (t.help) {
            Support.Talk.SHADOW -> R.string.talk_tray_shadow
            Support.Talk.BLANK -> R.string.talk_tray_blank
            Support.Talk.CHIPS -> R.string.talk_tray_chips
            Support.Talk.MEANING -> R.string.talk_tray_meaning
        }), style = Type.capsSm.ui(), color = p.giltText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2, Alignment.End), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
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
    }
    if (t.hintable) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(Modifier.heightIn(min = 48.dp).border(1.dp, p.line).pressable(role = Role.Button) { onHint() }.padding(horizontal = Tokens.Space.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BulbGlyph(p.giltText)
            Text(stringResource(R.string.talk_hint), style = Type.small.ui(), color = p.ink)
        }
    }
}

/** 점선 둥근 테두리 (지금 내 차례 말풍선). */
private fun Modifier.dashedRound(c: Color) = drawBehind {
    drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/**
 * 답장 말해 보기 (답장 한 턴을 보낸 뒤 한 번, 건너뛰기 있음): 방금 쓴 배우는 말 문장을 소리 내어.
 * 문장은 도움 사다리만큼 ([Support.replySpeak]): A1–A2 보이고, B1 흐리게, B2 감추고 힌트로. 뜻(읽는 말)은 늘 보인다.
 * 말하기 판 · 아래 칸은 일곱 곳 공통 ([SpeakPanel]). 원래 소리가 있고 문장이 보이면 먼저 듣기. × 는 나가기 ([onClose], 한 번 묻는다).
 */
@Composable
fun ReplySpeak(s: AppState, r: Route.Play, turn: Int, turns: Int, onNext: () -> Unit, onClose: () -> Unit = onNext) {
    val p = Ink.palette
    val id = r.room.series
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(id)
    val (line, tri) = io.github.graviton94.todayletters.core.Exercises.replyFor(letter, view.learn, turn)
    val file = remember(letter.id, turn) { s.replyTake(id, chapter, letter.id, turn).also { it.delete() } }
    val sentence = remember(line) { Breaks.plain(line).trim() }
    // 원래 소리: 그 턴의 답장 낭독 r<턴>_<언어> (대신 고른 메시지면 그 낭독), 이 기기에 없으면 null → 듣기 단추를 숨긴다
    val audio = remember(letter.id, turn, view.learn) {
        io.github.graviton94.todayletters.core.Exercises.replyAudioName(letter, view.learn, turn)
            ?.let { s.narrator.path(id, chapter, letter.id, it) }?.takeIf { s.narrator.has(it) }
    }
    val how = Support.replySpeak(s.support(id).band)
    var hinted by remember(turn) { mutableStateOf(false) }
    var spoken by remember(turn) { mutableStateOf(false) }
    val visible = how != Support.Speak.HIDDEN || hinted || spoken
    // 감춘 문장 (B2) 은 먼저 들으면 답을 듣는 셈: 보일 때만 먼저 듣기
    val take = rememberShadowTake(s, audio, 0, Int.MAX_VALUE, file, step = true, key = Triple(letter.id, turn, view.learn),
        onVoice = { v -> if (v) spoken = true }, sayLike = s.sayLike(id, sentence), listenFirst = how == Support.Speak.SHOWN, text = sentence, lang = view.learn)
    LaunchedEffect(take) {
        val me = s.narrator.step("reply" to turn)
        if (how == Support.Speak.SHOWN && !take.have) s.autoPlay(me) { end -> take.listen(s, me, end) }
    }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(turn + 1, turns, onClose = onClose, count = if (turns > 1) stringResource(R.string.ladder_turn, turn + 1, turns) else null,
            onSkip = if (spoken) null else ({ s.narrator.stop(); onNext() }))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            PracticeTitle("SPEAK", stringResource(R.string.reply_speak_title))
            Text(stringResource(R.string.reply_speak_d), style = Type.body.ui(), color = p.inkSoft)
            Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(quoted(tri[view.read], view.read), style = Type.heading.of(view.read), color = p.ink)
                if (visible) Text(sentence, style = Type.title.copy(fontFamily = Faces.display).of(view.learn),
                    color = if (how == Support.Speak.FADED && !spoken) p.ink.copy(alpha = 0.35f) else p.ink)
                else Text(stringResource(R.string.talk_hint), style = Type.small.ui().copy(textDecoration = TextDecoration.Underline), color = p.giltText,
                    modifier = Modifier.heightIn(min = 44.dp).pressable { hinted = true }.padding(vertical = Tokens.Space.s2))
            }
        }
        SpeakPanel(s, take, preview = visible, canListen = audio != null && (visible || take.have)) {
            MainButton(stringResource(if (turn < turns - 1) R.string.ladder_next_turn else R.string.speak_line_next), enabled = spoken,
                reason = stringResource(if (take.scoring) R.string.need_scoring else R.string.need_record)) { s.narrator.stop(); onNext() }
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
