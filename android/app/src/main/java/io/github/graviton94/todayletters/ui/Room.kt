package io.github.graviton94.todayletters.ui

import io.github.graviton94.todayletters.data.learnVocab

import io.github.graviton94.todayletters.data.Playback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Plays
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.data.recipientOf
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 편지 = 대화방. 문장마다 편지지 한 조각이 도착하고, 글자는 줄마다 잉크가 번지며 한 번만 나타난다.
 * 소리는 저절로 나지 않는다: 조각의 듣기 단추나 위의 "전체 듣기"를 누를 때만 (미디어 볼륨).
 * 진도는 문장 단위로 저장되어, 나갔다 와도 그 자리에서 이어진다 (이미 받은 조각은 다 써진 채로).
 */
@Composable
fun Room(s: AppState, r: Route.Letter) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r)
    val id = r.series
    val work = s.work(id)
    val view = s.room(id)
    s.version
    val total = letter.messages.size
    val already = remember(letter.id) { s.progress(id, chapter, letter.id).shown }
    var arrived by remember(letter.id) { mutableIntStateOf(already) }   // 화면에 놓인 조각 수
    var written by remember(letter.id) { mutableIntStateOf(already) }   // 다 써진 조각 수
    var writing by remember(letter.id) { mutableStateOf(false) }
    var words by remember { mutableStateOf(false) }
    var wordFocus by remember { mutableStateOf<Int?>(null) }
    // 펼친 낱말은 [AppState.gloss] (화면 맨 위의 GlossHost 가 카드를 띄운다). 배우는 말이 이 작품 어휘 자료의 언어일 때만
    val glossTokens = remember(id, letter.id, view.learn, work.deck) {
        letter.messages.indices.map { s.glossTokens(id, letter.id, it) }.takeIf { t -> t.any { it != null } }
    }
    val glossAt = s.gloss?.takeIf { it.series == id && it.letter.id == letter.id }?.let { it.msg to it.token }
    // 낱말 아래 뜻 (시안 beginner.html ①, core Interlinear): 아직 내 것이 아닌 낱말마다 그 아래 짧은 뜻. 위의 켜고 끄기 (기본 켜짐)
    val ilOn = glossTokens != null && s.interlinearOn(id)
    val il = remember(s.version, letter.id, view.learn, view.read, ilOn) { if (!ilOn) null else interlinearLines(s, id, letter, glossTokens!!) }
    // 예열에서 본 말 (‘아까 본 말’, 금빛 점선): 사전 열쇠 · 닻 · 표현
    val seen = remember(s.version, letter.id) { s.warmSeen(id, chapter, letter.id) }
    var playing by remember(letter.id) { mutableStateOf<Int?>(null) }
    var all by remember { mutableStateOf<Job?>(null) }
    val controlsRef = remember { arrayOfNulls<Playback.Controls>(1) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val pace = when (view.pace) { TypingPace.CALM -> 1f; TypingPace.QUICK -> 0.55f; TypingPace.INSTANT -> 0f }
    val animate = pace > 0f && !s.reducedMotion
    fun audio(i: Int) = s.narrator.path(id, chapter, letter.id, "m${i + 1}_${view.learn.code}")
    // 낭독이 아직 없는 편지 (모차르트 등): 듣기 단추를 숨기고, 자동 낭독 · 알림창 재생도 시작하지 않는다 (읽기는 그대로)
    val narrated = remember(letter.id, view.learn) { total > 0 && s.narrator.has(audio(0)) }

    // 낭독 이어 듣기: 앱을 벗어나도 알림창 · 잠금 화면 · 이어폰 단추로 멈추고 넘긴다 (data/Playback)
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    fun fg() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val key = s.key(id, chapter, letter.id)
    val jump = remember(letter.id) { intArrayOf(0) }   // 단추로 넘길 곳: 1 다음 문장, -1 이전 문장, 2 이 문장 처음부터
    var muted by remember(letter.id) { mutableStateOf(false) }   // 알림을 밀어 지우면 이 방에서는 더 읽지 않는다
    val nowTitle = dateLine(letter.date, letter.place)
    val toWhom = remember(letter.id) { work.recipientOf(letter) }   // 이 편지를 받는 사람 (편지에 적혀 있으면 그 사람)
    val nowSub = stringResource(R.string.narration_sub, work.name[uiLang()], toWhom[uiLang()],
        work.chapters.firstOrNull { it.id == chapter }?.title?.get(uiLang()).orEmpty())
    fun session() {
        if (Playback.now == null || Playback.detached) Playback.start(appCtx, s.narrator,
            Playback.Now(nowTitle, nowSub, letter.plate?.image?.takeIf { it.isNotBlank() }?.let { "plates/$it" }, 0, total), controlsRef[0]!!)
    }

    fun stopAll() { all?.cancel(); all = null; s.narrator.stop(); playing = null; Playback.end(appCtx) }
    // 이어 읽을 끝 (앱을 밀어 닫았을 때 여기까지 Playback 이 이어 읽는다): 도착 낭독은 편지 끝, 모두 듣기는 쓰인 데까지, 한 조각은 그 조각
    val queue = remember(letter.id) { intArrayOf(total, 1) }   // [끝, 들은 자리 남기기 1/0]
    /**
     * [from] 번째부터 [until] 앞까지 차례로 읽는다. 멈춰 두면 기다리고, 단추로 앞뒤 문장을 오간다.
     * [extend] 면 ‘다음’ 단추로 [until] 너머(쓰인 조각까지)로도 간다. [mark] 면 듣다 멈춘 자리를 남긴다.
     */
    suspend fun say(from: Int, until: Int, extend: Boolean = false, mark: Boolean = true, speed: Float? = null) {
        var k = from
        var end = until
        while (k < end && !muted) {
            snapshotFlow { Playback.paused }.first { !it }
            session()
            playing = k; Playback.update(k); if (mark) s.store.setHeard(key, k)
            jump[0] = 0
            suspendCancellableCoroutine { c ->
                // 앱을 밀어 닫아 Playback 이 넘겨받았으면 소리를 끊지 않는다
                c.invokeOnCancellation { if (!Playback.detached) s.narrator.stop() }
                if (!s.narrator.play(audio(k), speed) { if (c.isActive) c.resume(Unit) } && c.isActive) c.resume(Unit)
            }
            val j = jump[0]
            k = when (j) { -1 -> (k - 1).coerceAtLeast(0); 2 -> k; else -> k + 1 }
            if (extend && j == 1 && k >= end && k < written) end = k + 1
        }
        if (playing != null) playing = null
    }
    /**
     * 조각 하나 듣기: 이것도 낭독 세션으로 (알림창 · 잠금 화면 · 이어폰 단추에 재생/멈춤 · 이전/다음, v22).
     * 알림에서 ‘다음’을 누르면 다음 조각으로 이어진다 (쓰인 조각까지). [speed] 는 0.7× 단추 (없으면 이 작품의 기본 빠르기).
     * 같은 조각을 기본 빠르기로 듣는 중에 스피커를 다시 누르면 멈춘다 (천천히 듣는 중이면 기본 빠르기로 다시).
     */
    fun speak(i: Int, speed: Float? = null) {
        if (playing == i && speed == null && s.narrator.slowBy == null) { stopAll(); return }
        stopAll(); muted = false
        queue[0] = i + 1; queue[1] = 0
        all = scope.launch {
            say(i, i + 1, extend = true, mark = false, speed = speed)
            all = null
            Playback.end(appCtx)
        }
    }
    fun speakAll(from: Int = 0, after: (() -> Unit)? = null) {
        if (all != null) { stopAll(); return }
        if (!narrated) { after?.invoke(); return }
        stopAll(); muted = false
        queue[0] = written; queue[1] = 1
        all = scope.launch {
            say(from, written)
            all = null
            if (after != null) after() else {
                if (written >= total && !muted) s.store.setHeard(key, -1)
                Playback.end(appCtx)
            }
        }
    }
    controlsRef[0] = object : Playback.Controls {
        override fun next() { jump[0] = 1; s.narrator.stop() }
        override fun prev() { jump[0] = if (s.narrator.position() > 1500) 2 else -1; s.narrator.stop() }
        override fun stop() { muted = true; stopAll() }
    }
    DisposableEffect(letter.id) { onDispose { s.music.stop(); if (!Playback.detached) { s.narrator.stop(); Playback.end(appCtx) } } }
    // 최근 앱에서 앱을 밀어 닫음 (화면이 사라짐, 언어를 바꿔 다시 그리는 것은 빼고): 남은 문장을 Playback 에 넘겨 이 편지 끝까지 이어 읽는다.
    // 앱 안에서 방을 나가면 지금처럼 멈춘다.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    DisposableEffect(letter.id, lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e != androidx.lifecycle.Lifecycle.Event.ON_DESTROY || activity?.isChangingConfigurations == true) return@LifecycleEventObserver
            val at = playing ?: return@LifecycleEventObserver
            if (Playback.now == null || muted) return@LifecycleEventObserver
            val mark = queue[1] == 1
            Playback.handOff(appCtx, (0 until queue[0].coerceIn(at + 1, total)).map(::audio), at, null,
                heard = if (mark) ({ k: Int -> s.store.setHeard(key, k) }) else null)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // 편지 속 그 곡 듣기 시트 (열려 있는 동안 낭독은 잠깐 멈춤)
    var listening by remember(letter.id) { mutableStateOf<io.github.graviton94.todayletters.core.Moment.Music?>(null) }

    // 다시 열 때: 듣다 멈춘 문장이 있으면 묻는다 (이어서 듣기 · 처음부터). 소리를 끈 사람에게는 묻지 않고 전처럼 이어서 도착.
    val resumeAt = remember(letter.id) {
        if (!s.app.sound || !narrated) null
        else if (already in 1 until total) already
        else s.store.heard(key).takeIf { it in 1 until total && already >= total }
    }
    var asking by remember(letter.id) { mutableStateOf(resumeAt != null) }
    var gate by remember(letter.id) { mutableStateOf(resumeAt == null || already >= total) }
    // 편지 도착 (v21 2단계 D1): 처음 여는 새 편지만. 도착 화면이 첫 줄을 읽었으면 방에서는 첫 줄을 다시 읽지 않는다
    // 큐레이터 노트를 띄워도 되는가: 이 편지의 도착(쓰기 · 마지막 메시지 낭독)이 다 끝났거나, 이미 다 읽은 편지
    var heardOut by remember(letter.id) { mutableStateOf(already >= total) }
    var arriving by remember(letter.id) { mutableStateOf(already == 0 && !s.progress(id, chapter, letter.id).done && !s.store.arrived(key) && total > 0) }

    // 도착: 남은 문장을 한 조각씩. 조각이 놓이면 바로 쓰기 시작하면서 빈센트 목소리로 읽고 (자동 낭독), 화면은 쓰이는 끝을 따라 내려간다.
    // 글자도 다 써지고 낭독도 끝나면 다음 조각. 조각이 화면 밖이라 ‘다 써짐’ 알림이 오지 않아도 시간이 지나면 다음으로 넘어간다.
    // 앱을 벗어나 화면이 그려지지 않는 동안에도 (화면 따라가기만 쉬고) 낭독은 이어진다.
    LaunchedEffect(letter.id) {
        suspend fun toEnd(animated: Boolean) {
            if (!fg()) return
            androidx.compose.runtime.withFrameNanos { }
            val last = list.layoutInfo.totalItemsCount - 1
            if (last < 0) return
            if (animated) list.animateScrollToItem(last) else list.scrollToItem(last)
            // 마지막 조각이 화면보다 길면 그 아래 끝까지
            list.scrollBy(100_000f)
        }
        snapshotFlow { gate && !arriving }.first { it }
        var voiced = false
        while (arrived < total) {
            val i = arrived
            writing = true
            if (animate && fg()) delay((Tokens.Motion.typingDotsMs * pace).toLong())
            arrived = i + 1
            toEnd(animated = animate)
            // 도착 화면이 첫 줄을 읽었어도 방에서는 다른 조각처럼 다시 읽는다 (v22: 첫 메시지도 방에서 듣고 따라갈 수 있게)
            val voice = if (narrated && s.app.sound && all == null && !muted) launch { voiced = true; queue[0] = total; queue[1] = 1; say(i, i + 1) } else null
            // 쓰는 동안 끝을 따라간다 (손으로 넘기는 중이면 기다림)
            val follow = launch {
                while (true) {
                    delay(300)
                    if (fg() && !list.isScrollInProgress) list.scrollBy(100_000f)
                }
            }
            val m = letter.messages[i]
            val lines = (m.text[view.learn].length + (if (view.showRead) m.text[view.read].length else 0)) / 26 + 2
            val limit = if (animate && fg()) lines * (Tokens.Motion.inkLineMs * pace.coerceAtLeast(0.3f)).toLong() + 2500 else 600
            kotlinx.coroutines.withTimeoutOrNull(limit) { snapshotFlow { written }.first { it > i } }
            if (written <= i) written = i + 1
            follow.cancel()
            writing = false
            toEnd(animated = animate)
            voice?.join()
            s.save(id, chapter, letter.id, s.progress(id, chapter, letter.id).copy(shown = i + 1))
            if (animate && fg()) delay((Tokens.Motion.inkPauseMs * pace).toLong())
        }
        if (voiced && !muted) s.store.setHeard(key, -1)
        if (voiced && all == null) Playback.end(appCtx)
        // 마지막 문장 낭독까지 끝난 뒤에 큐레이터 노트 (그 전에 뜨면 읽는 글을 가린다), 화면도 그때 노트까지
        heardOut = true
        toEnd(animated = animate)
    }

    val progress = s.progress(id, chapter, letter.id)
    // 이 편지에서 하는 걸음 (낭독이 없는 편지는 따라 읽기 없이, F11). 건너뛴 걸음은 지나간 것으로
    val modes = remember(letter.id, view.learn, s.version) { s.letterModes(id, chapter, letter) }
    val nextPlay = modes.firstOrNull { it !in progress.replied && it !in progress.skipped }
    val reply = Exercises.replyFor(letter, view.learn).second
    val finished = written >= total
    // 이 편지의 읽을 수 있는 비율 (아는 낱말 %): 머리의 날짜 옆에
    val known = remember(s.version, letter.id, view.learn) { s.coverage(id, letter.id) }
    val knownLabel = known?.let { stringResource(R.string.known_pct, it) }
    // 튜토리얼 (편지 I) 의 읽기 걸음 안내: 이 방에 처음 들어온 동안만 (도착 장면을 지난 뒤). 읽기 전에는 첫 조각 바로 위에
    // “이 문장을 눌러 보세요”, 다 읽으면 아래 다음 단추 바로 위에 “다 읽었어요 — 아래 ‘…’를 눌러 보세요” + 단추에 금빛 테 깜빡임
    val guiding = remember(letter.id) { glossTokens != null && s.guideDue("read") }
    LaunchedEffect(guiding, arriving) { if (guiding && !arriving) s.guideShown("read") }
    val guideOn = guiding && !arriving
    val guideNext = guideOn && finished && !progress.done && nextPlay != null

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.4f)) {
        RoomHeader(s, work.portrait, work.sender, if (writing) "${work.name[uiLang()]} · ${stringResource(R.string.room_typing)}" else shortDateLine(letter.date, letter.place),
            known = if (writing) null else known, knownLabel = knownLabel,
            auto = s.app.sound, onAuto = if (!narrated) null else ({ if (s.app.sound) stopAll(); s.update(s.app.copy(sound = !s.app.sound)) }), onInfo = { s.go(Route.RoomInfo(r)) })
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val notYet = stringResource(R.string.step_not_yet)
        val nLetter = remember(letter.id) { work.chapters.flatMap { it.letters }.indexOf(letter) + 1 }
        StepBar(read = written, total = total, modes = modes, progress = progress,
            words = letter.words.size, recipient = toWhom[uiLang()], number = roman(nLetter)) { step ->
            // 0 = 읽기, 1..modes = 연습, 마지막 = 그림. 다 한 걸음 · 건너뛴 걸음(다시 하기) · 지금 걸음만 이동
            val readDone = written >= total
            val nextMode = if (!readDone) null else nextPlay
            when {
                step == 0 -> scope.launch { list.animateScrollToItem(0) }
                step <= modes.size -> modes[step - 1].let { m ->
                    if (readDone && (m in progress.replied || m in progress.skipped || m == nextMode)) { stopAll(); s.go(Route.Play(r, m)) }
                    else android.widget.Toast.makeText(ctx, notYet, android.widget.Toast.LENGTH_SHORT).show()
                }
                else -> if (progress.done || (readDone && nextMode == null)) { stopAll(); s.go(Route.Artwork(id, r.letter, r)) }
                    else android.widget.Toast.makeText(ctx, notYet, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = PaddingValues(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            if (glossTokens != null) item(key = "${letter.id}:il") { InterlinearToggle(ilOn, seen.isNotEmpty() && ilOn) { s.setInterlinear(id, !ilOn) } }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Box(Modifier.weight(1f)) { Hair() }
                    Text(dateLine(letter.date, letter.place).uppercase(), style = Type.capsSm, color = p.giltText)
                    Box(Modifier.weight(1f)) { Hair() }
                }
            }

            // 그 무렵 빈센트의 상태 메시지 (메신저 프로필 한마디처럼)
            letter.status?.let { st -> item(key = "${letter.id}:status") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.status_line, work.name[uiLang()], st[uiLang()]), style = Type.small.ui(), color = p.inkSoft,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } }
            // 다시 읽기: 다 읽은 편지라면 지금 이 편지의 아는 낱말 % (처음 읽을 때와 견줘) · 오늘의 낱말을 몇 개 혼자 읽는지
            if (progress.done) s.letterGrowth(id, chapter, letter).takeIf { it.total > 0 || known != null }?.let { g -> item(key = "${letter.id}:growth") {
                val first = remember(letter.id) { s.firstCoverage(id, chapter, letter.id) }
                Row(Modifier.fillMaxWidth().background(p.fill).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Text("${known ?: g.pct}%", style = Type.title.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = Color(0xFFD2A955))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (known != null && first != null) Text(stringResource(R.string.reread_cov, first, known), style = Type.small.ui(), color = p.onFill)
                        if (g.total > 0) Text(stringResource(R.string.reread_banner, g.known, g.total), style = Type.small.ui(), color = p.onFill)
                    }
                }
            } }
            letter.moments.filter { it.after < 0 && it.shows(s, id) }.forEachIndexed { k, mo ->
                item(key = "${letter.id}:pre:$k") { MomentCard(s, work, mo, onMap = { }, onPhoto = { s.go(Route.Artwork(id, r.letter, r)) }, onMusic = { listening = it }) }
            }
            itemsIndexed(letter.messages.take(arrived), key = { i, _ -> "${letter.id}:$i" }) { i, m ->
                val toks = glossTokens?.getOrNull(i)
                val ranges = remember(letter.id, i, toks) { toks?.let { io.github.graviton94.todayletters.core.Vocab.align(m.text[view.learn], it) } ?: emptyList() }
                // 오늘의 낱말: 사전 열쇠가 있으면 토큰으로 (편지에 찍힌 꼴 그대로), 없으면 글자 맞추기로. 문장마다 낱말 하나에 한 번
                val sentMarks = remember(letter.id, i, view.learn, toks) {
                    if (toks != null && letter.words.any { it.lex.isNotEmpty() }) buildList {
                        toks.forEachIndexed { j, t ->
                            val k = letter.words.indexOfFirst { it.lex == t.key }
                            if (k >= 0 && !ranges[j].isEmpty() && none { it.second == k }) add(ranges[j] to k)
                        }
                    } else wordMarks(m.text[view.learn], letter.words.map { it.text[view.learn] })
                }
                val hints = remember(letter.id, i, toks, sentMarks) { toks?.let { ts -> ranges.filterIndexed { j, r -> work.lexicon?.get(ts[j].key)?.function != true && sentMarks.none { it.first == r } } } ?: emptyList() }
                // 번역 줄은 접지 않고 등급만큼 옅게 (core 의 Support.readAlpha, 작품 설정에서 끌 수 있음)
                val readAlpha = s.readAlpha(id)
                // 한 번만 쓴다: 다 써진 조각은 (스크롤로 화면 밖에 나갔다 돌아와도) 다시 쓰지 않는다
                val fresh = i >= already && animate && i >= written
                var shownIn by remember { mutableStateOf(!fresh) }
                LaunchedEffect(Unit) { shownIn = true }
                Column {
                if (i == 0 && guideOn && !finished) GuideBanner(stringResource(R.string.guide_read), Modifier.padding(bottom = Tokens.Space.s2))
                AnimatedVisibility(shownIn, enter = fadeIn(tween(Tokens.Motion.fadeMs * 2)) + slideInVertically(tween(Tokens.Motion.fadeMs * 2)) { it / 6 }) {
                    LetterSlip(
                        index = i, learn = m.text[view.learn], learnLang = view.learn,
                        read = if (view.showRead) m.text[view.read] else null, readLang = view.read,
                        animate = fresh, lineMs = (Tokens.Motion.inkLineMs * pace.coerceAtLeast(0.3f)).toInt(),
                        signature = if (i == total - 1) signOff(work.series.original, work.sender) else null,
                        canPlay = i < written, canListen = narrated,
                        listen = { ListenButtons(play = { sp -> speak(i, sp) }, label = stringResource(R.string.listen_line), vertical = true,
                            playing = playing == i, onListen = { speak(i) }, onStop = { stopAll() }) },
                        onWritten = { if (written < i + 1) written = i + 1 },
                        marks = sentMarks,
                        onWord = { wordFocus = it },
                        taps = ranges, onTap = if (toks != null) ({ j: Int -> s.gloss = AppState.GlossPick(id, chapter, letter, i, j, toks[j].key) }) else null, hints = hints,
                        picked = glossAt?.takeIf { it.first == i }?.let { ranges.getOrNull(it.second) }, onPickedAt = { s.glossAnchor = it },
                        readAlpha = readAlpha,
                        saved = s.isSaved(s.quoteKey(id, chapter, letter.id, i)),
                        onSave = { s.toggleQuote(s.quoteKey(id, chapter, letter.id, i)) },
                        spoken = spokenRange(s.narrator, audio(i), m.text[view.learn]),
                        il = il?.getOrNull(i), seen = seen,
                    )
                }
                // 메신저 같은 순간: 이 문장 다음의 위치 공유 · 사진 공유 (문장이 다 써진 뒤에)
                if (i < written) letter.moments.filter { it.after == i && it.shows(s, id) }.forEach { mo ->
                    Box(Modifier.padding(top = Tokens.Space.s4)) {
                        MomentCard(s, work, mo, onMap = { }, onPhoto = {
                            s.go(Route.Artwork(id, r.letter, r))
                        }, onMusic = { listening = it })
                    }
                }
                }
            }
            if (writing && arrived == written && arrived < total) item(key = "${letter.id}:typing") { TypingBubble(work.name[uiLang()], s.reducedMotion) }
            if (finished) {
                if (heardOut || progress.done) letter.note?.let { note ->
                    item(key = "${letter.id}:note") { CuratorNote(note[uiLang()], uiLang()) }
                }
                // 따라 읽기 (봉인까지): 다 읽은 편지라면 언제든
                if (progress.done) item(key = "${letter.id}:shadow") {
                    Secondary(stringResource(if (s.isSealed(id, chapter, letter)) R.string.focus_sealed else R.string.shadow_entry)) {
                        stopAll(); s.go(if (s.isSealed(id, chapter, letter)) Route.Seal(r) else Route.Shadow(r))
                    }
                }
                if (ReplyMode.CONSTELLATION in progress.replied) item {
                    val nextLetter = work.chapters.getOrNull(r.chapter - 1)?.letters?.getOrNull(r.letter)
                    val unreadByHim = nextLetter != null && !s.store.started(s.key(id, chapter, nextLetter.id)) && s.progress(id, chapter, nextLetter.id).shown == 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        if (unreadByHim) Text("1", style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.giltText,
                            modifier = Modifier.padding(end = 6.dp, bottom = 2.dp))
                        TheoNote(reply[view.learn], view.learn, if (view.showRead) reply[view.read] else null, view.read, toWhom[view.learn])
                    }
                }
            }
        }
        // 듣다 멈춘 자리에서 (v17 E)
        if (asking && resumeAt != null) Column(
            Modifier.fillMaxWidth().background(p.paper).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Hair()
            Caps(stringResource(R.string.caps_resume), p.giltText, small = true, decorative = true)
            Text(stringResource(R.string.room_resume, resumeAt + 1), style = Type.body.ui(), color = p.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.heightIn(min = 44.dp).pressable {
                    asking = false
                    if (already < total) speakAll(0) { gate = true } else speakAll(0)
                }, contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.room_resume_start), style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.inkSoft)
                }
                Box(Modifier.width(160.dp)) {
                    Primary(stringResource(R.string.room_resume_go), small = true, shine = true) {
                        asking = false
                        if (already < total) gate = true else speakAll(resumeAt)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().background(p.paper).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            when {
                !finished -> Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.buttonSm).dashed(p.line),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pen(p.inkSoft)
                    Box(Modifier.width(Tokens.Space.s2))
                    Text(stringResource(R.string.room_locked), style = Type.small.ui(), color = p.inkSoft)
                }
                progress.done -> {
                    // 건너뛴 걸음은 언제든 다시 (편지 목록 · 복습 탭에서도)
                    progress.toRedo.firstOrNull { it in modes }?.let { m ->
                        Secondary(stringResource(R.string.redo_step, stringResource(modeLabel(m)))) { stopAll(); s.go(Route.Play(r, m)) }
                    } ?: if (letter.words.isNotEmpty()) Secondary(stringResource(R.string.room_words, letter.words.size)) { words = true } else Unit
                    Primary(stringResource(R.string.done_home), shine = true) { stopAll(); s.go(r.from ?: Route.Inbox) }
                }
                else -> {
                    if (letter.words.isNotEmpty()) Secondary(stringResource(R.string.room_words, letter.words.size)) { words = true }
                    when (nextPlay) {
                        null -> Primary(stringResource(R.string.room_done_today), shine = true) { stopAll(); s.finishReading(r) }
                        ReplyMode.CONSTELLATION -> {
                            val help = s.replyHelp(id)
                            val how = when {
                                help == io.github.graviton94.todayletters.core.Ladder.Help.LINK || letter.replies.isEmpty() -> modeLabel(nextPlay)
                                help == io.github.graviton94.todayletters.core.Ladder.Help.WRITE -> R.string.ladder_write_mode
                                else -> R.string.ladder_choose_mode
                            }
                            Primary(stringResource(R.string.room_reply, stringResource(how)), shine = true) { stopAll(); s.go(Route.Play(r, nextPlay)) }
                        }
                        else -> {
                            val label = stringResource(R.string.room_next, stringResource(modeLabel(nextPlay)))
                            if (guideNext) GuideBanner(stringResource(R.string.guide_read_done, label))
                            Primary(label, Modifier.then(if (guideNext) Modifier.guidePulse(s.reducedMotion) else Modifier), shine = true) { stopAll(); s.go(Route.Play(r, nextPlay)) }
                        }
                    }
                }
            }
        }
    }
    if (arriving) LetterArrival(s, id, chapter, letter, remember(letter.id) { work.chapters.flatMap { it.letters }.indexOf(letter) + 1 },
        onBack = { arriving = false; s.narrator.stop(); s.back() }) { _ ->
        s.store.markArrived(key); arriving = false
    }
    if (words) WordSheet(s, r, onClose = { words = false })
    listening?.let { m -> MusicSheet(s, work, m, onClose = { listening = null }) }
    wordFocus?.let { k -> val (ch, lt) = s.letterOf(r); WordDetail(s, s.cardKey(id, ch, lt.id, k)) { wordFocus = null } }
    // 다 읽은 편지의 낱말은 낱말 카드함으로 (내일부터 복습)
    LaunchedEffect(finished) { if (finished) { s.collectWords(id, chapter, letter); s.collectAnchors(id, chapter, letter) } }
}

fun modeLabel(m: ReplyMode) = when (m) {
    ReplyMode.MATCH -> R.string.mode_match
    ReplyMode.CONSTELLATION -> R.string.mode_constellation
    ReplyMode.ALOUD -> R.string.mode_aloud
    ReplyMode.CHECK -> R.string.mode_check
    ReplyMode.TALK -> R.string.mode_talk
}

/** 쓰는 중…: 펜이 끄적이는 작은 거품 (움직임 줄이기면 멈춰 있음). */
@Composable
private fun TypingBubble(name: String, still: Boolean) {
    val p = Ink.palette
    val t = if (still) 1f else androidx.compose.animation.core.rememberInfiniteTransition(label = "pen").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1100), androidx.compose.animation.core.RepeatMode.Restart), label = "scribble",
    ).value
    Row(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)).background(p.slip).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(22.dp, 14.dp)) {
            val w = size.width; val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.8f); cubicTo(w * 0.2f, -h * 0.2f, w * 0.35f, h * 1.1f, w * 0.55f, h * 0.45f); cubicTo(w * 0.7f, 0f, w * 0.85f, h * 0.3f, w, h * 0.6f)
            }
            val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
            val part = androidx.compose.ui.graphics.Path(); m.getSegment(0f, m.length * t, part, true)
            drawPath(part, p.slipSoft, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text(stringResource(R.string.typing_bubble, if (uiLang() == Lang.KO) josa(name, "이", "가") else name), style = Type.small.ui(), color = p.slipSoft)
    }
}

/** 대화방 맨 위: 뒤로 · 동그란 초상 · 이름(첫 줄) · 날짜와 아는 낱말 %(둘째 줄) · 자동 낭독 · (?) · 더 보기(대화방 정보 · 낭독 빠르기). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RoomHeader(
    s: AppState, portrait: String, name: String, sub: String,
    known: Int? = null, knownLabel: String? = null,
    auto: Boolean, onAuto: (() -> Unit)?, onInfo: () -> Unit,
) {
    val p = Ink.palette
    var knownNote by remember { mutableStateOf(false) }
    Column(Modifier.background(p.paper)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = Tokens.Space.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(p.ink) }
            Portrait(portrait, name, Tokens.Size.avatar)
            Column(Modifier.weight(1f).padding(start = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name, style = Type.heading.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.ink, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                // 짧은 날짜 · 아는 낱말 % (작은 금빛 표)를 한 줄에. 큰 글자에서 넘치면 표가 날짜 아래로 내려간다 (자르지 않는다)
                DateAndBadge {
                    Text(sub, style = Type.small.ui(), color = p.inkSoft, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (known != null) Text("$known%", style = Type.small.ui().copy(fontSize = Tokens.Text.caps, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = p.giltText, maxLines = 1, softWrap = false,
                        modifier = Modifier.pressable(haptic = false) { knownNote = true }
                            .border(1.dp, p.giltText.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .padding(horizontal = 6.dp).then(if (knownLabel != null) Modifier.androidx_semantics(knownLabel) else Modifier))
                }
                // 아는 낱말 % 를 누르면 무엇을 세는지 두 줄
                if (knownNote && knownLabel != null) NumberNote(knownLabel,
                    listOf(stringResource(R.string.note_known_1), stringResource(R.string.note_known_2, stringResource(R.string.stage_recall)))) { knownNote = false }
            }
            // 자동 낭독 켜기 · 끄기: 켜져 있으면 금빛으로 채운 스피커, 꺼져 있으면 빗금. 낭독이 이 기기에 없으면 (소리 없이 읽기) 단추 없음
            if (onAuto != null) HeadButton(stringResource(if (auto) R.string.room_auto_on else R.string.room_auto_off), onClick = onAuto) {
                Box(Modifier.size(34.dp).background(if (auto) p.giltText else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, p.giltText, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(16.dp)) {
                        val w = size.width
                        val c = if (auto) p.paper else p.giltText
                        val st = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                        drawPath(androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.1f, w * 0.38f); lineTo(w * 0.3f, w * 0.38f); lineTo(w * 0.52f, w * 0.18f)
                            lineTo(w * 0.52f, w * 0.82f); lineTo(w * 0.3f, w * 0.62f); lineTo(w * 0.1f, w * 0.62f); close()
                        }, c, style = st)
                        if (auto) drawArc(c, -50f, 100f, false, Offset(w * 0.5f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.36f, w * 0.4f), style = st)
                        else drawLine(c, Offset(w * 0.62f, w * 0.35f), Offset(w * 0.95f, w * 0.68f), 1.4.dp.toPx())
                    }
                }
            }
            HeadButton(stringResource(R.string.help), onClick = { s.coachAgain("room") }) { HelpGlyph(p.ink) }
            // 더 보기: 대화방 정보. 빠르기는 문장마다의 0.7× 단추 · 작품 설정의 기본 듣기 빠르기로 (시안 slow.html)
            Box {
                var menu by remember { mutableStateOf(false) }
                HeadButton(stringResource(R.string.room_more), onClick = { menu = true }) { Lines(p.ink) }
                if (menu) RoomMenu(onInfo = { menu = false; onInfo() }, onClose = { menu = false })
            }
        }
        Hair()
    }
}

/** 머리 오른쪽 단추: 너비 48dp (손가락 크기 기준은 지킨다) · 높이 56dp. 이름 · 날짜 자리를 넓히려고 앱의 56dp 단추보다 좁게. */
@Composable
private fun HeadButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) =
    Box(Modifier.size(48.dp, Tokens.Size.touch).androidx_semantics(label).pressable(onClick = onClick), contentAlignment = Alignment.Center) { content() }

/**
 * 날짜 · 금빛 % 표: 한 줄에 들어가면 나란히 (표는 세로 가운데), 넘치면(큰 글자) 날짜 아래로 표를 내린다.
 * 날짜는 줄 바꿈 없이 잰 너비로 판단한다 (FlowRow 는 날짜를 먼저 접어 버려 표가 셋째 줄로 갔다).
 */
@Composable
private fun DateAndBadge(content: @Composable () -> Unit) {
    val gap = 6.dp; val vGap = 2.dp
    androidx.compose.ui.layout.Layout(content) { ms, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val badge = ms.getOrNull(1)?.measure(loose)
        val date0 = ms[0]
        val oneLine = badge == null || date0.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) + gap.roundToPx() + badge.width <= c.maxWidth
        if (oneLine) {
            val d = date0.measure(loose.copy(maxWidth = (c.maxWidth - (badge?.let { it.width + gap.roundToPx() } ?: 0)).coerceAtLeast(0)))
            val h = maxOf(d.height, badge?.height ?: 0)
            layout(d.width + (badge?.let { it.width + gap.roundToPx() } ?: 0), h) {
                d.placeRelative(0, (h - d.height) / 2)
                badge?.placeRelative(d.width + gap.roundToPx(), (h - badge.height) / 2)
            }
        } else {
            val d = date0.measure(loose)
            val w = maxOf(d.width, badge!!.width)
            layout(w, d.height + vGap.roundToPx() + badge.height) {
                d.placeRelative(0, 0)
                badge.placeRelative(0, d.height + vGap.roundToPx())
            }
        }
    }
}

/** 머리 오른쪽 더 보기 메뉴: 대화방 정보. */
@Composable
private fun RoomMenu(onInfo: () -> Unit, onClose: () -> Unit) {
    val p = Ink.palette
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.ui.window.Popup(alignment = Alignment.TopEnd, offset = androidx.compose.ui.unit.IntOffset(0, with(d) { 48.dp.roundToPx() }),
        onDismissRequest = onClose, properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
        Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).widthIn(min = 200.dp, max = 300.dp).background(p.paper).border(1.dp, p.line)) {
            MenuItem(stringResource(R.string.room_info), onClick = onInfo)
        }
    }
}

@Composable
private fun MenuItem(label: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable(haptic = false) { onClick() }.padding(horizontal = Tokens.Space.s4, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(label, style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 걸음 하나: 이름 · 한 줄 설명 · 상태 (0 남음 · 1 지금 · 2 끝남 · 3 건너뜀). */
internal data class Step(val label: String, val desc: String, val state: Int)

@Composable
private fun steps(read: Int, total: Int, modes: List<ReplyMode>, progress: io.github.graviton94.todayletters.core.LetterProgress, words: Int, recipient: String): List<Step> {
    val readDone = read >= total
    val marks = io.github.graviton94.todayletters.core.Journey.letterDots(read, total, modes, progress)
    fun state(k: Int) = when (marks[k]) {
        io.github.graviton94.todayletters.core.Journey.Step.DONE -> 2
        io.github.graviton94.todayletters.core.Journey.Step.NOW -> 1
        io.github.graviton94.todayletters.core.Journey.Step.SKIPPED -> 3
        else -> 0
    }
    return buildList {
        add(Step(stringResource(R.string.step_read_short),
            if (readDone) stringResource(R.string.step_read_d_done, total) else stringResource(R.string.step_read_d, total, read), state(0)))
        modes.forEachIndexed { k, m ->
            add(Step(stringResource(modeLabel(m)), when (m) {
                ReplyMode.MATCH -> stringResource(R.string.step_match_d, words)
                // 편지 안 따라 읽기는 한 문장 (F14)
                ReplyMode.ALOUD -> stringResource(R.string.step_aloud_one)
                ReplyMode.CHECK -> stringResource(R.string.step_check_d)
                ReplyMode.TALK -> stringResource(R.string.step_talk_d)
                else -> stringResource(R.string.step_reply_d, recipient)
            }, state(k + 1)))
        }
        add(Step(stringResource(R.string.step_plate), stringResource(R.string.step_plate_d), state(modes.size + 1)))
    }
}

/**
 * 진행 줄 (v21 2단계 U1): 지금 할 걸음 하나만 (로마 숫자 · 이름 · 몇 번째 · 앞 걸음 끝남) + 작은 마름모들.
 * 누르면 위에서 걸음 목록이 내려온다 (끝난 것 ✓ · 지금 · 남은 것). 끝난 걸음 · 지금 걸음만 바로 간다.
 */
@Composable
private fun StepBar(read: Int, total: Int, modes: List<ReplyMode>, progress: io.github.graviton94.todayletters.core.LetterProgress,
                    words: Int, recipient: String, number: String, onStep: (Int) -> Unit) {
    val p = Ink.palette
    val list = steps(read, total, modes, progress, words, recipient)
    var open by remember { mutableStateOf(false) }
    val cur = list.indexOfFirst { it.state == 1 }.let { if (it < 0) list.lastIndex else it }
    val openLabel = stringResource(R.string.steps_open)
    Column(Modifier.background(p.paper)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).pressable(haptic = false) { open = !open }
                .androidx_semantics(openLabel).padding(start = Tokens.Space.s4, end = Tokens.Space.s3, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(roman(cur + 1), style = Type.numeral.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.giltText,
                modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(list[cur].label, style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink, maxLines = 1)
                Text(stringResource(R.string.step_of, list.size, cur + 1) + (if (cur > 0 && list[cur - 1].state == 2) " · " + stringResource(R.string.step_after, list[cur - 1].label)
                    else if (cur > 0 && list[cur - 1].state == 3) " · " + stringResource(R.string.step_after_skipped, list[cur - 1].label) else ""),
                    style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                list.forEach { st ->
                    val d = if (st.state == 1) 9.dp else 7.dp
                    Box(Modifier.size(d).graphicsLayer { rotationZ = 45f }
                        .background(when (st.state) { 2 -> p.giltText; 1 -> p.ink; else -> Color.Transparent })
                        .border(1.dp, when (st.state) { 2, 3 -> p.giltText; 1 -> p.ink; else -> p.hideInk }))
                }
            }
            Box(Modifier.graphicsLayer { rotationZ = if (open) 90f else -90f }) { Chevron(p.inkSoft) }
        }
        // 한 것만큼 금빛 (가는 줄)
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.hair)) {
            Box(Modifier.fillMaxWidth(list.count { it.state >= 2 }.toFloat() / list.size).height(2.dp).background(p.giltText))
        }
        if (open) androidx.compose.ui.window.Popup(onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
            Column(Modifier.fillMaxSize()) {
                StepSheet(list, number) { i -> if (list[i].state > 0) open = false; onStep(i) }
                Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false) { open = false })
            }
        }
    }
}

/** 펼친 걸음 목록. */
@Composable
internal fun StepSheet(list: List<Step>, number: String, onStep: (Int) -> Unit) {
    val p = Ink.palette
    Column(Modifier.fillMaxWidth().background(p.paper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Caps(stringResource(R.string.steps_caps, number, list.size), p.giltText, small = true, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.steps_count, list.count { it.state >= 2 }, list.count { it.state < 2 }), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
        }
        list.forEachIndexed { i, st ->
            Hair()
            Row(
                Modifier.fillMaxWidth().background(if (st.state == 1) p.leaf else Color.Transparent)
                    .drawBehind { if (st.state == 1) drawRect(p.ink, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                    .pressable(haptic = false) { onStep(i) }.heightIn(min = 64.dp).padding(horizontal = Tokens.Space.s4, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                    if (st.state == 2) Box(Modifier.size(22.dp).background(p.giltText, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(11.dp)) {
                            val w = size.width
                            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.1f, w * 0.55f); lineTo(w * 0.4f, w * 0.82f); lineTo(w * 0.92f, w * 0.2f) }, p.paper,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                    } else Text(roman(i + 1), style = Type.heading.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display),
                        color = if (st.state == 1) p.ink else p.hideInk)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(st.label, style = Type.body.ui().copy(fontWeight = if (st.state == 2) null else androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = if (st.state == 2) p.inkSoft else p.ink)
                    Text(st.desc, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                }
                when (st.state) {
                    2 -> Capsule(stringResource(if (i == 0) R.string.step_again_read else R.string.step_again))
                    3 -> Capsule(stringResource(R.string.step_redo), CapsuleKind.FILLED)
                    1 -> Capsule(stringResource(R.string.step_now), CapsuleKind.FILLED)
                }
            }
        }
        Hair()
        Text(stringResource(R.string.steps_settings), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft,
            modifier = Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3))
        Hair()
    }
}

private fun Modifier.androidx_semantics(label: String) = this.then(androidx.compose.ui.Modifier.semantics { contentDescription = label })

/** 큐레이터 노트: 편지와 다른 재료 (미술관 벽의 작품 설명판). 앱 글자 언어로. */
@Composable
private fun CuratorNote(text: String, lang: Lang) {
    val p = Ink.palette
    Column(
        Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2).background(p.leaf)
            .border(1.dp, p.giltText.copy(alpha = 0.55f)).padding(3.dp).border(Tokens.Stroke.hair, p.giltText.copy(alpha = 0.3f))
            .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Canvas(Modifier.size(10.dp)) {
                val w = size.width
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, 0f); lineTo(w, w / 2); lineTo(w / 2, w); lineTo(0f, w / 2); close() }, p.giltText)
            }
            Text(stringResource(R.string.note_title), style = Type.caps.ui(), color = p.giltText)
        }
        Text(text, style = Type.body.of(lang).copy(fontSize = Tokens.Text.small * 1.08f, lineHeight = Tokens.Text.small * 1.75f), color = p.ink)
    }
}

/** 편지지 한 조각: 배울 언어 줄(크게) · 번역 줄(작게) · 듣기 단추. 새로 온 조각은 줄마다 잉크가 번지며 한 번만 써진다. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LetterSlip(
    index: Int, learn: String, learnLang: Lang, read: String?, readLang: Lang,
    animate: Boolean, lineMs: Int, signature: String?, canPlay: Boolean, canListen: Boolean = true,
    /** 듣기 단추 둘 ([ListenButtons]): 스피커 · 0.7×. */
    listen: @Composable () -> Unit, onWritten: () -> Unit,
    marks: List<Pair<IntRange, Int>> = emptyList(), onWord: (Int) -> Unit = {},
    saved: Boolean = false, onSave: () -> Unit = {},
    /** 번역 줄의 진하기 (등급이 오를수록 옅게, 접지는 않는다). */
    readAlpha: Float = 1f,
    /** 지금 낭독 중인 마디의 글자 범위 (없으면 null). */
    spoken: IntRange? = null,
    taps: List<IntRange> = emptyList(), onTap: ((Int) -> Unit)? = null, hints: List<IntRange> = emptyList(), picked: IntRange? = null,
    onPickedAt: (GlossAnchor?) -> Unit = {},
    /** 낱말 아래 뜻 (켜져 있고 이 메시지에 토큰이 있을 때). [seen] 은 예열에서 본 것 ([warmSeen]). */
    il: IlLine? = null, seen: Set<String> = emptySet(),
) {
    val p = Ink.palette
    var learnDone by remember { mutableStateOf(!animate) }
    val readInk = p.slipSoft.copy(alpha = p.slipSoft.alpha * readAlpha)
    val tilt = listOf(-0.6f, 0.5f, -0.3f, 0.4f)[index % 4]
    Slip(seed = index * 31 + 7, tilt = tilt, modifier = Modifier.fillMaxWidth(0.92f)) {
        Row(
            Modifier.padding(start = Tokens.Space.s4, end = Tokens.Space.s2, top = Tokens.Space.s4, bottom = Tokens.Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                // 문장 짝 (v22): 배우는 말 한 문장 바로 아래에 그 번역
                val pairs = remember(learn, read) { if (read != null) io.github.graviton94.todayletters.core.Pairs.of(learn, read) else emptyList() }
                if (pairs.size > 1) SentencePairs(
                    learn, learnLang, readLang, pairs, animate, lineMs, onWritten,
                    marks, onWord, spoken, taps, onTap, hints, picked, onPickedAt, il, seen, readInk,
                ) else {
                if (il != null) IlText(learn, il, null, learnLang, animate, true, lineMs, onDone = {
                    learnDone = true
                    if (read == null) onWritten()
                }, marks = marks, onWord = onWord, spoken = spoken, onTap = onTap, picked = picked, onPickedAt = onPickedAt, seen = seen)
                else InkText(learn, Type.target.of(learnLang), p.slipInk, animate, lineMs = lineMs, onDone = {
                    learnDone = true
                    if (read == null) onWritten()
                }, marks = marks.map { it.first }, markColor = Color(0xFF8F6A27), onMark = { k -> onWord(marks[k].second) }, spoken = spoken,
                    taps = taps, onTap = onTap, hints = hints, picked = picked, onPickedAt = onPickedAt)
                if (read != null) InkText(read, Type.base.of(readLang), readInk, animate, go = learnDone, lineMs = (lineMs * 0.7f).toInt(), onDone = onWritten)
                }
                if (signature != null) Text(signature, style = Type.signature, color = p.slipSoft, modifier = Modifier.align(Alignment.End).padding(top = Tokens.Space.s1))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                if (canPlay && canListen) listen()
                else Box(Modifier.size(Tokens.Size.speaker))
                // 내 구절에 담기
                if (canPlay) IconButton(stringResource(if (saved) R.string.quote_saved else R.string.quote_save), onClick = onSave) { Bookmark(saved) }
            }
        }
    }
}

/** [r] 안의 범위를 [r] 첫 글자 기준으로 (밖에 걸치면 null). */
private fun IntRange.inside(r: IntRange): IntRange? = if (isEmpty() || first < r.first || last > r.last) null else (first - r.first)..(last - r.first)

/**
 * 문장 짝 (v22): 메시지 하나를 (배우는 말 문장 · 바로 아래 번역) 짝으로. 짝마다 배우는 말이 써지고 이어서 번역이 써진다 (한 번만).
 * 밑줄 낱말 · 눌러서 뜻 보기 · 낭독 칠하기는 메시지 전체의 글자 범위를 문장마다 옮겨서 그대로 쓴다.
 */
@Composable
private fun SentencePairs(
    learn: String, learnLang: Lang, readLang: Lang, pairs: List<Pair<IntRange, String>>, animate: Boolean, lineMs: Int, onWritten: () -> Unit,
    marks: List<Pair<IntRange, Int>>, onWord: (Int) -> Unit, spoken: IntRange?,
    taps: List<IntRange>, onTap: ((Int) -> Unit)?, hints: List<IntRange>, picked: IntRange?, onPickedAt: (GlossAnchor?) -> Unit,
    il: IlLine? = null, seen: Set<String> = emptySet(),
    readInk: Color = Ink.palette.slipSoft,
) {
    val p = Ink.palette
    var stage by remember(learn) { mutableIntStateOf(if (animate) 0 else pairs.size * 2) }
    pairs.forEachIndexed { k, (r, tr) ->
        val seg = learn.substring(r.first.coerceAtLeast(0), (r.last + 1).coerceAtMost(learn.length))
        val segMarks = marks.mapNotNull { (m, w) -> m.inside(r)?.let { it to w } }
        val segSpoken = spoken?.takeIf { !it.isEmpty() && it.first <= r.last && it.last >= r.first }?.let { maxOf(it.first, r.first) - r.first..minOf(it.last, r.last) - r.first }
        if (k > 0) Box(Modifier.height(Tokens.Space.s1))
        if (il != null) IlText(learn, il, r, learnLang, animate, stage >= 2 * k, lineMs, onDone = { if (stage == 2 * k) stage = 2 * k + 1 },
            marks = marks, onWord = onWord, spoken = spoken, onTap = onTap, picked = picked, onPickedAt = onPickedAt, seen = seen)
        else InkText(seg, Type.target.of(learnLang), p.slipInk, animate, go = stage >= 2 * k, lineMs = lineMs, onDone = { if (stage == 2 * k) stage = 2 * k + 1 },
            marks = segMarks.map { it.first }, markColor = Color(0xFF8F6A27), onMark = { j -> onWord(segMarks[j].second) }, spoken = segSpoken,
            taps = taps.map { it.inside(r) ?: IntRange.EMPTY }, onTap = onTap, hints = hints.mapNotNull { it.inside(r) },
            picked = picked?.inside(r), onPickedAt = onPickedAt)
        if (tr.isNotBlank()) InkText(tr, Type.base.of(readLang), readInk, animate, go = stage >= 2 * k + 1, lineMs = (lineMs * 0.7f).toInt(),
            onDone = { if (stage == 2 * k + 1) stage = 2 * k + 2; if (k == pairs.lastIndex) onWritten() })
        else if (k == pairs.lastIndex) LaunchedEffect(stage) { if (stage >= 2 * k + 1) onWritten() }
        else LaunchedEffect(stage) { if (stage == 2 * k + 1) stage = 2 * k + 2 }
    }
}

/** 펜 모양 (답장 자리). */
@Composable
fun Pen(c: androidx.compose.ui.graphics.Color) = Canvas(Modifier.size(16.dp)) {
    val w = size.width
    val st = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawPath(androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.7f, w * 0.12f); lineTo(w * 0.88f, w * 0.3f); lineTo(w * 0.32f, w * 0.86f); lineTo(w * 0.1f, w * 0.9f); lineTo(w * 0.14f, w * 0.68f); close()
    }, c, style = st)
}

/** (?) 모양: 동그라미 안 물음표. 폰 글꼴 배율과 상관없이 같은 크기. */
@Composable
fun HelpGlyph(c: androidx.compose.ui.graphics.Color) = Box(Modifier.size(22.dp).border(1.dp, c, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width
        drawArc(c, 200f, 250f, false, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, w * 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
        drawCircle(c, 1.2.dp.toPx(), Offset(w / 2, w * 1.15f))
    }
}

/** 낱말 카드: 이 편지의 낱말 (배울 언어 · 번역, 누르면 발음). */
@Composable
fun WordSheet(s: AppState, r: Route.Letter, onClose: () -> Unit) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r)
    val view = s.room(r.series)
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).clickable(enabled = false) {}.navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Rule()
            Caps("${deco(s.work(r.series).series.original, "Mots", "Wörter")} · ${letter.date}", p.giltText)
            letter.words.forEachIndexed { i, w ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) {
                        s.narrator.play(s.wordAudio(r.series, chapter, letter, w, view.learn))
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.text[view.learn], style = Type.heading.of(view.learn), color = p.ink)
                        io.github.graviton94.todayletters.core.Pron.line(w, view.learn, view.learn == s.work(r.series).learnVocab).takeIf { it.isNotEmpty() }?.let { pr ->
                        Text(listOf(pr, w.pos).filter { it.isNotEmpty() }.joinToString(" · "), style = Type.small, color = p.inkSoft)
                        io.github.graviton94.todayletters.core.Pron.note(w, view.learn, uiLang()).takeIf { it.isNotEmpty() }?.let { Text(it, style = Type.small.ui(), color = p.giltText) }
                    }
                    }
                    Text(w.text[view.read], style = Type.body.of(view.read), color = p.inkSoft)
                    val wa = s.wordAudio(r.series, chapter, letter, w, view.learn)
                    if (wa.isNotEmpty()) ListenButtons(play = { sp -> s.narrator.play(wa, sp) }, onDark = true)
                }
                Hair()
            }
            // 편지 속 표현
            if (letter.phrases.isNotEmpty()) {
                val tr = if (view.read == Lang.EN || view.read == Lang.KO) view.read else uiLang()
                letter.phrases.forEach { ph ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ph.text[view.learn], style = Type.heading.of(view.learn), color = p.ink)
                        Text(ph.text[tr], style = Type.body.of(tr), color = p.inkSoft)
                    }
                    Hair()
                }
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
}

/** 낱말 한 장: 편지 속 밑줄 낱말을 누르면. 열자마자 발음을 들려준다. */
@Composable
fun WordCard(s: AppState, r: Route.Letter, index: Int, onClose: () -> Unit) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r)
    val view = s.room(r.series)
    val w = letter.words.getOrNull(index) ?: return
    val audio = s.wordAudio(r.series, chapter, letter, w, view.learn)
    LaunchedEffect(index) { s.narrator.play(audio) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).clickable(enabled = false) {}.navigationBarsPadding().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(w.text[view.learn], style = Type.display.of(view.learn), color = p.ink)
                    io.github.graviton94.todayletters.core.Pron.line(w, view.learn, view.learn == s.work(r.series).learnVocab).takeIf { it.isNotEmpty() }?.let { pr ->
                        Text(listOf(pr, w.pos).filter { it.isNotEmpty() }.joinToString(" · "), style = Type.small, color = p.inkSoft)
                        io.github.graviton94.todayletters.core.Pron.note(w, view.learn, uiLang()).takeIf { it.isNotEmpty() }?.let { Text(it, style = Type.small.ui(), color = p.giltText) }
                    }
                }
                if (audio.isNotEmpty()) ListenButtons(play = { sp -> s.narrator.play(audio, sp) }, label = stringResource(R.string.listen_line), onDark = true)
            }
            Hair()
            Text(w.text[view.read], style = Type.heading.of(view.read), color = p.ink)
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
}

@Composable
fun Lines(c: androidx.compose.ui.graphics.Color) = Canvas(Modifier.size(Tokens.Size.icon)) {
    val w = size.width; val h = size.height
    for (k in 0..2) drawLine(c, Offset(0f, h * (0.25f + k * 0.25f)), Offset(w, h * (0.25f + k * 0.25f)), 1.5f * density)
}

/** 대화방에 놓을 순간인가: ‘편지 속 그 곡’은 녹음이 있을 때만. */
/** 이 기기에 그 곡 · 그림이 있을 때만 놓는다 (녹음이 없는 곡, 소리 없이 읽기로 받지 않은 장의 곡 · 사진 · 스케치는 뺀다). */
private fun io.github.graviton94.todayletters.core.Moment.shows(s: AppState, series: String) = when (this) {
    is io.github.graviton94.todayletters.core.Moment.Music -> playable && io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, path(series))
    is io.github.graviton94.todayletters.core.Moment.Photo -> io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, (if (sketch) "sketches/" else "plates/") + image)
    else -> true
}

/** 장식 글자(머리글 · 갈래 이름)를 작품의 원어로: 독일어 작품은 [de], 그 밖(프랑스어 작품)은 [fr]. */
fun deco(original: Lang, fr: String, de: String) = if (original == Lang.DE) de else fr

/** 인용 부호 (앱 전체 하나, 그 글의 언어대로): « … » · „…“ · 「…」 · “…”. */
fun quoted(text: String, lang: Lang): String = when (lang) {
    Lang.FR -> "«\u202F$text\u202F»"
    Lang.DE -> "„$text“"
    Lang.JA -> "「$text」"
    else -> "“$text”"
}

/** 마지막 조각의 서명: 프랑스어 편지는 “t. à t. Vincent”, 독일어 편지는 “— Wolfgang”. */
fun signOff(original: Lang, sender: String) = if (original == Lang.FR) "t. à t. $sender" else "— $sender"

/** "1888-02-21" → "21 FÉVR." (소인용, 프랑스어 약자). */
fun dayOf(date: String): String = runCatching {
    // 날짜를 모르는 편지는 "1888-04" 처럼 달까지만: 소인에는 달 이름만
    if (date.length == 7) return@runCatching java.time.YearMonth.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.FRENCH))
    val d = java.time.LocalDate.parse(date.take(10))
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH))
}.getOrDefault(date)

/** 대화방 머리용 짧은 날짜: "1888. 10. 3." · "3 Oct 1888" (달만 있으면 "1888. 10." · "Oct 1888"). */
@Composable
fun shortDateLine(date: String, place: String): String {
    val ko = uiLang() == Lang.KO
    if (date.length == 7) runCatching { java.time.YearMonth.parse(date) }.getOrNull()?.let { m ->
        return if (ko) "${m.year}. ${m.monthValue}." else m.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH))
    }
    val d = runCatching { java.time.LocalDate.parse(date.take(10)) }.getOrNull() ?: return place
    return if (ko) "${d.year}. ${d.monthValue}. ${d.dayOfMonth}." else d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
}

/** 대화방 정보 등의 날짜: 앱 글자 언어로 ("1888년 2월 21일" / "21 February 1888"). 장소는 소인에 있다. */
@Composable
fun dateLine(date: String, place: String): String {
    val ko = uiLang() == Lang.KO
    if (date.length == 7) runCatching { java.time.YearMonth.parse(date) }.getOrNull()?.let { m ->
        return if (ko) "${m.year}년 ${m.monthValue}월" else m.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH))
    }
    val d = runCatching { java.time.LocalDate.parse(date.take(10)) }.getOrNull() ?: return place
    val text = if (ko) "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
    else d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH))
    return text
}
