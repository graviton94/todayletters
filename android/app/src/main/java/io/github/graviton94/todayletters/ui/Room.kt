package io.github.graviton94.todayletters.ui

import io.github.graviton94.todayletters.data.Playback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
    var playing by remember(letter.id) { mutableStateOf<Int?>(null) }
    var all by remember { mutableStateOf<Job?>(null) }
    val controlsRef = remember { arrayOfNulls<Playback.Controls>(1) }
    var speed by remember(id) { androidx.compose.runtime.mutableFloatStateOf(s.store.roomSpeed(id)) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val pace = when (view.pace) { TypingPace.CALM -> 1f; TypingPace.QUICK -> 0.55f; TypingPace.INSTANT -> 0f }
    val animate = pace > 0f && !s.reducedMotion
    fun audio(i: Int) = s.narrator.path(id, chapter, letter.id, "m${i + 1}_${view.learn.code}")

    // 낭독 이어 듣기: 앱을 벗어나도 알림창 · 잠금 화면 · 이어폰 단추로 멈추고 넘긴다 (data/Playback)
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    fun fg() = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    val key = s.key(id, chapter, letter.id)
    val jump = remember(letter.id) { intArrayOf(0) }   // 단추로 넘길 곳: 1 다음 문장, -1 이전 문장, 2 이 문장 처음부터
    var muted by remember(letter.id) { mutableStateOf(false) }   // 알림을 밀어 지우면 이 방에서는 더 읽지 않는다
    val nowTitle = dateLine(letter.date, letter.place)
    val nowSub = stringResource(R.string.narration_sub, work.name[uiLang()], work.recipient[uiLang()],
        work.chapters.firstOrNull { it.id == chapter }?.title?.get(uiLang()).orEmpty())
    fun session() {
        if (Playback.now == null) Playback.start(appCtx, s.narrator,
            Playback.Now(nowTitle, nowSub, letter.plate?.image?.takeIf { it.isNotBlank() }?.let { "plates/$it" }, 0, total), controlsRef[0]!!)
    }

    fun stopAll() { all?.cancel(); all = null; s.narrator.stop(); playing = null; Playback.end(appCtx) }
    fun speak(i: Int) {
        if (playing == i) { stopAll(); return }
        stopAll()
        if (s.narrator.play(audio(i), speed) { if (playing == i) playing = null }) playing = i
    }
    /** [from] 번째부터 [until] 앞까지 차례로 읽는다. 멈춰 두면 기다리고, 단추로 앞뒤 문장을 오간다. */
    suspend fun say(from: Int, until: Int) {
        var k = from
        while (k < until && !muted) {
            snapshotFlow { Playback.paused }.first { !it }
            session()
            playing = k; Playback.update(k); s.store.setHeard(key, k)
            jump[0] = 0
            suspendCancellableCoroutine { c ->
                c.invokeOnCancellation { s.narrator.stop() }
                if (!s.narrator.play(audio(k), speed) { if (c.isActive) c.resume(Unit) } && c.isActive) c.resume(Unit)
            }
            k = when (jump[0]) { -1 -> (k - 1).coerceAtLeast(0); 2 -> k; else -> k + 1 }
        }
        if (playing != null) playing = null
    }
    fun speakAll(from: Int = 0, after: (() -> Unit)? = null) {
        if (all != null) { stopAll(); return }
        stopAll(); muted = false
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
    DisposableEffect(letter.id) { onDispose { s.narrator.stop(); Playback.end(appCtx) } }

    // 다시 열 때: 듣다 멈춘 문장이 있으면 묻는다 (이어서 듣기 · 처음부터). 소리를 끈 사람에게는 묻지 않고 전처럼 이어서 도착.
    val resumeAt = remember(letter.id) {
        if (!s.app.sound) null
        else if (already in 1 until total) already
        else s.store.heard(key).takeIf { it in 1 until total && already >= total }
    }
    var asking by remember(letter.id) { mutableStateOf(resumeAt != null) }
    var gate by remember(letter.id) { mutableStateOf(resumeAt == null || already >= total) }
    // 편지 도착 (v21 2단계 D1): 처음 여는 새 편지만. 도착 화면이 첫 줄을 읽었으면 방에서는 첫 줄을 다시 읽지 않는다
    var arriving by remember(letter.id) { mutableStateOf(already == 0 && !s.progress(id, chapter, letter.id).done && !s.store.arrived(key) && total > 0) }
    var arrivalVoiced by remember(letter.id) { mutableStateOf(false) }

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
            val voice = if (s.app.sound && all == null && !muted && !(i == 0 && arrivalVoiced)) launch { voiced = true; say(i, i + 1) } else null
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
        toEnd(animated = animate)
    }

    val progress = s.progress(id, chapter, letter.id)
    val modes = Plays.inLetter.filter { it in s.seriesSettings(id).modes }
    val nextPlay = modes.firstOrNull { it !in progress.replied }
    val reply = Exercises.replyFor(letter, view.learn).second
    val finished = written >= total
    val cardMap = remember(s.version) { s.cardMap() }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.4f)) {
        RoomHeader(s, work.portrait, work.sender, if (writing) "${work.name[uiLang()]} · ${stringResource(R.string.room_typing)}" else dateLine(letter.date, letter.place),
            auto = s.app.sound, onAuto = { if (s.app.sound) stopAll(); s.update(s.app.copy(sound = !s.app.sound)) }, onInfo = { s.go(Route.RoomInfo(r)) },
            speed = speed, onSpeed = { speed = if (speed < 1f) 1f else 0.7f; s.store.setRoomSpeed(id, speed) })
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val notYet = stringResource(R.string.step_not_yet)
        val nLetter = remember(letter.id) { work.chapters.flatMap { it.letters }.indexOf(letter) + 1 }
        StepBar(read = written, total = total, modes = modes, replied = progress.replied, done = progress.done,
            words = letter.words.size, recipient = work.recipient[uiLang()], number = roman(nLetter)) { step ->
            // 0 = 읽기, 1..modes = 연습, 마지막 = 그림. 다 한 걸음과 지금 걸음만 이동
            val readDone = written >= total
            val nextMode = if (!readDone) null else modes.firstOrNull { it !in progress.replied }
            when {
                step == 0 -> scope.launch { list.animateScrollToItem(0) }
                step <= modes.size -> modes[step - 1].let { m ->
                    if (readDone && (m in progress.replied || m == nextMode)) { stopAll(); s.go(Route.Play(r, m)) }
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
            // 다시 읽기: 다 읽은 편지라면 지금 얼마나 혼자 읽는지 (번역이 그만큼 접힌다)
            if (progress.done) s.letterGrowth(id, chapter, letter).takeIf { it.total > 0 }?.let { g -> item(key = "${letter.id}:growth") {
                Row(Modifier.fillMaxWidth().background(p.fill).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Text("${g.pct}%", style = Type.title.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = Color(0xFFD2A955))
                    Text(stringResource(R.string.reread_banner, g.known, g.total), style = Type.small.ui(), color = p.onFill, modifier = Modifier.weight(1f))
                }
            } }
            letter.moments.filter { it.after < 0 }.forEachIndexed { k, mo ->
                item(key = "${letter.id}:pre:$k") { MomentCard(s, work, mo, onMap = { }, onPhoto = { s.go(Route.Artwork(id, r.letter, r)) }) }
            }
            itemsIndexed(letter.messages.take(arrived), key = { i, _ -> "${letter.id}:$i" }) { i, m ->
                val sentMarks = wordMarks(m.text[view.learn], letter.words.map { it.text[view.learn] })
                // 번역이 사라지는 편지 (엔진 공통): 이 문장 낱말의 절반 이상을 혼자 읽으면 번역을 접고 아직인 낱말만 뜻풀이
                val fade = remember(s.version, i) {
                    val idx = sentMarks.map { it.second }.distinct()
                    val known = idx.filter { k -> io.github.graviton94.todayletters.core.Growth.known(cardMap[s.cardKey(id, chapter, letter.id, k)]) }
                    val fold = idx.isNotEmpty() && known.size * 2 >= idx.size
                    fold to (idx - known.toSet()).map { k -> letter.words[k].let { firstSense(it.text[view.learn]) to firstSense(it.text[view.read]) } }
                }
                val fresh = i >= already && animate
                var shownIn by remember { mutableStateOf(!fresh) }
                LaunchedEffect(Unit) { shownIn = true }
                Column {
                AnimatedVisibility(shownIn, enter = fadeIn(tween(Tokens.Motion.fadeMs * 2)) + slideInVertically(tween(Tokens.Motion.fadeMs * 2)) { it / 6 }) {
                    LetterSlip(
                        index = i, learn = m.text[view.learn], learnLang = view.learn,
                        read = if (view.showRead) m.text[view.read] else null, readLang = view.read,
                        animate = fresh, lineMs = (Tokens.Motion.inkLineMs * pace.coerceAtLeast(0.3f)).toInt(),
                        signature = if (i == total - 1) "t. à t. ${work.sender}" else null,
                        playing = playing == i, canPlay = i < written,
                        onPlay = { speak(i) }, onWritten = { if (written < i + 1) written = i + 1 },
                        marks = sentMarks,
                        onWord = { wordFocus = it },
                        fold = fade.first, glosses = fade.second,
                        saved = s.isSaved(s.quoteKey(id, chapter, letter.id, i)),
                        onSave = { s.toggleQuote(s.quoteKey(id, chapter, letter.id, i)) },
                    )
                }
                // 메신저 같은 순간: 이 문장 다음의 위치 공유 · 사진 공유 (문장이 다 써진 뒤에)
                if (i < written) letter.moments.filter { it.after == i }.forEach { mo ->
                    Box(Modifier.padding(top = Tokens.Space.s4)) {
                        MomentCard(s, work, mo, onMap = { }, onPhoto = {
                            s.go(Route.Artwork(id, r.letter, r))
                        })
                    }
                }
                }
            }
            if (writing && arrived == written && arrived < total) item(key = "${letter.id}:typing") { TypingBubble(work.name[uiLang()], s.reducedMotion) }
            if (finished) {
                letter.note?.let { note ->
                    item { CuratorNote(note[uiLang()], uiLang()) }
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
                        TheoNote(reply[view.learn], view.learn, if (view.showRead) reply[view.read] else null, view.read, work.recipient[view.learn])
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
            Caps("RESUME", p.giltText, small = true, decorative = true)
            Text(stringResource(R.string.room_resume, resumeAt + 1), style = Type.body.ui(), color = p.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.heightIn(min = 44.dp).pressable {
                    asking = false
                    if (already < total) speakAll(0) { gate = true } else speakAll(0)
                }, contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.room_resume_start), style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.inkSoft)
                }
                Box(Modifier.width(160.dp)) {
                    Primary(stringResource(R.string.room_resume_go), small = true) {
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
                    if (letter.words.isNotEmpty()) Secondary(stringResource(R.string.room_words, letter.words.size)) { words = true }
                    Primary(stringResource(R.string.done_home)) { stopAll(); s.go(r.from ?: Route.Inbox) }
                }
                else -> {
                    if (letter.words.isNotEmpty()) Secondary(stringResource(R.string.room_words, letter.words.size)) { words = true }
                    when (nextPlay) {
                        null -> Primary(stringResource(R.string.room_done_today)) {
                            stopAll()
                            s.save(id, chapter, letter.id, progress.complete(s.seriesSettings(id).modes).copy(done = true))
                            s.finished()
                            s.go(Route.Done(r))
                        }
                        ReplyMode.CONSTELLATION -> Primary(stringResource(R.string.room_reply, stringResource(modeLabel(nextPlay)))) { stopAll(); s.coachDone("first_steps"); s.go(Route.Play(r, nextPlay)) }
                        else -> Primary(stringResource(R.string.room_next, stringResource(modeLabel(nextPlay)))) { stopAll(); s.coachDone("first_steps"); s.go(Route.Play(r, nextPlay)) }
                    }
                }
            }
        }
    }
    if (arriving) LetterArrival(s, id, chapter, letter, remember(letter.id) { work.chapters.flatMap { it.letters }.indexOf(letter) + 1 }) { heard ->
        s.store.markArrived(key); arrivalVoiced = heard; arriving = false
    }
    if (words) WordSheet(s, r, onClose = { words = false })
    wordFocus?.let { k -> val (ch, lt) = s.letterOf(r); WordDetail(s, s.cardKey(id, ch, lt.id, k)) { wordFocus = null } }
    // 다 읽은 편지의 낱말은 낱말 카드함으로 (내일부터 복습)
    LaunchedEffect(finished) { if (finished) s.collectWords(id, chapter, letter) }
}

fun modeLabel(m: ReplyMode) = when (m) {
    ReplyMode.MATCH -> R.string.mode_match
    ReplyMode.CONSTELLATION -> R.string.mode_constellation
    ReplyMode.ALOUD -> R.string.mode_aloud
    ReplyMode.DICTATION -> R.string.mode_dictation
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

/** 대화방 맨 위: 뒤로 · 동그란 초상 · 이름과 날짜 · 전체 듣기 · (?) · 대화방 정보. */
@Composable
private fun RoomHeader(
    s: AppState, portrait: String, name: String, sub: String,
    auto: Boolean, onAuto: () -> Unit, onInfo: () -> Unit,
    speed: Float = 1f, onSpeed: () -> Unit = {},
) {
    val p = Ink.palette
    Column(Modifier.background(p.paper)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = Tokens.Space.s1), verticalAlignment = Alignment.CenterVertically) {
            IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(p.ink) }
            Portrait(portrait, name, Tokens.Size.avatar)
            Column(Modifier.weight(1f).padding(start = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name, style = Type.heading.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.ink, maxLines = 1)
                Text(sub, style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
            }
            // 낭독 빠르기 (v21 F6): 1× ↔ 0.7×, 시리즈마다 기억
            Box(Modifier.heightIn(min = 44.dp).pressable { onSpeed() }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Capsule(if (speed < 1f) "0.7×" else "1×", if (speed < 1f) CapsuleKind.FILLED else CapsuleKind.OUTLINE)
            }
            // 자동 낭독 켜기 · 끄기: 켜져 있으면 금빛으로 채운 스피커, 꺼져 있으면 빗금
            IconButton(stringResource(if (auto) R.string.room_auto_on else R.string.room_auto_off), onClick = onAuto) {
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
            IconButton(stringResource(R.string.help), onClick = { s.coachAgain("room") }) { HelpGlyph(p.ink) }
            IconButton(stringResource(R.string.room_info), onClick = onInfo) { Lines(p.ink) }
        }
        Hair()
    }
}

/** 걸음 하나: 이름 · 한 줄 설명 · 상태 (0 남음 · 1 지금 · 2 끝남). */
internal data class Step(val label: String, val desc: String, val state: Int)

@Composable
private fun steps(read: Int, total: Int, modes: List<ReplyMode>, replied: Set<ReplyMode>, done: Boolean, words: Int, recipient: String): List<Step> {
    val readDone = read >= total
    val next = if (!readDone) null else modes.firstOrNull { it !in replied }
    return buildList {
        add(Step(stringResource(R.string.step_read_short),
            if (readDone) stringResource(R.string.step_read_d_done, total) else stringResource(R.string.step_read_d, total, read), if (readDone) 2 else 1))
        modes.forEach { m ->
            add(Step(stringResource(modeLabel(m)), when (m) {
                ReplyMode.MATCH -> stringResource(R.string.step_match_d, words)
                ReplyMode.ALOUD -> stringResource(R.string.step_aloud_d, total)
                else -> stringResource(R.string.step_reply_d, recipient)
            }, when { m in replied -> 2; m == next -> 1; else -> 0 }))
        }
        add(Step(stringResource(R.string.step_plate), stringResource(R.string.step_plate_d), if (done) 2 else if (readDone && next == null) 1 else 0))
    }
}

/**
 * 진행 줄 (v21 2단계 U1): 지금 할 걸음 하나만 (로마 숫자 · 이름 · 몇 번째 · 앞 걸음 끝남) + 작은 마름모들.
 * 누르면 위에서 걸음 목록이 내려온다 (끝난 것 ✓ · 지금 · 남은 것). 끝난 걸음 · 지금 걸음만 바로 간다.
 */
@Composable
private fun StepBar(read: Int, total: Int, modes: List<ReplyMode>, replied: Set<ReplyMode>, done: Boolean,
                    words: Int, recipient: String, number: String, onStep: (Int) -> Unit) {
    val p = Ink.palette
    val list = steps(read, total, modes, replied, done, words, recipient)
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
                Text(stringResource(R.string.step_of, list.size, cur + 1) + (if (cur > 0 && list[cur - 1].state == 2) " · " + stringResource(R.string.step_after, list[cur - 1].label) else ""),
                    style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                list.forEach { st ->
                    val d = if (st.state == 1) 9.dp else 7.dp
                    Box(Modifier.size(d).graphicsLayer { rotationZ = 45f }
                        .background(when (st.state) { 2 -> p.giltText; 1 -> p.ink; else -> Color.Transparent })
                        .border(1.dp, when (st.state) { 2 -> p.giltText; 1 -> p.ink; else -> p.hideInk }))
                }
            }
            Box(Modifier.graphicsLayer { rotationZ = if (open) 90f else -90f }) { Chevron(p.inkSoft) }
        }
        // 한 것만큼 금빛 (가는 줄)
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.hair)) {
            Box(Modifier.fillMaxWidth(list.count { it.state == 2 }.toFloat() / list.size).height(2.dp).background(p.giltText))
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
            Text(stringResource(R.string.steps_count, list.count { it.state == 2 }, list.count { it.state < 2 }), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
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
    animate: Boolean, lineMs: Int, signature: String?, playing: Boolean, canPlay: Boolean,
    onPlay: () -> Unit, onWritten: () -> Unit,
    marks: List<Pair<IntRange, Int>> = emptyList(), onWord: (Int) -> Unit = {},
    saved: Boolean = false, onSave: () -> Unit = {},
    fold: Boolean = false, glosses: List<Pair<String, String>> = emptyList(),
) {
    val p = Ink.palette
    var learnDone by remember { mutableStateOf(!animate) }
    var reveal by remember { mutableStateOf(false) }
    val tilt = listOf(-0.6f, 0.5f, -0.3f, 0.4f)[index % 4]
    Slip(seed = index * 31 + 7, tilt = tilt, modifier = Modifier.fillMaxWidth(0.92f)) {
        Row(
            Modifier.padding(start = Tokens.Space.s4, end = Tokens.Space.s2, top = Tokens.Space.s4, bottom = Tokens.Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                InkText(learn, Type.target.of(learnLang), p.slipInk, animate, lineMs = lineMs, onDone = {
                    learnDone = true
                    if (read == null || fold) onWritten()
                }, marks = marks.map { it.first }, markColor = Color(0xFF8F6A27), onMark = { k -> onWord(marks[k].second) })
                // 번역이 사라지는 편지: 익힌 낱말이 많은 문장은 번역을 접고, 아직인 낱말만 작은 뜻풀이로
                if (read != null && fold) {
                    if (glosses.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        glosses.forEach { (w, m) ->
                            Text("$w · $m", style = Type.small.of(readLang).copy(fontSize = Tokens.Text.caps), color = Color(0xFF7A5A20),
                                modifier = Modifier.border(1.dp, Color(0x557A5A20)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (reveal) Text(read, style = Type.base.of(readLang), color = p.slipSoft)
                    else Text(stringResource(R.string.fade_show), style = Type.small.ui(), color = p.slipSoft, modifier = Modifier.pressable(haptic = false) { reveal = true }.padding(vertical = 4.dp))
                } else if (read != null) InkText(read, Type.base.of(readLang), p.slipSoft, animate, go = learnDone, lineMs = (lineMs * 0.7f).toInt(), onDone = onWritten)
                if (signature != null) Text(signature, style = Type.signature, color = p.slipSoft, modifier = Modifier.align(Alignment.End).padding(top = Tokens.Space.s1))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                if (canPlay) SpeakerButton(playing, stringResource(R.string.listen_line), onClick = onPlay)
                else Box(Modifier.size(Tokens.Size.speaker))
                // 내 구절에 담기
                if (canPlay) IconButton(stringResource(if (saved) R.string.quote_saved else R.string.quote_save), onClick = onSave) { Bookmark(saved) }
            }
        }
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
            Modifier.fillMaxWidth().background(p.paper).clickable(enabled = false) {}.padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Rule()
            Caps("Mots · ${letter.date}", p.giltText)
            letter.words.forEachIndexed { i, w ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) {
                        s.narrator.play(s.narrator.path(r.series, chapter, letter.id, "w${i + 1}_${view.learn.code}"))
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.text[view.learn], style = Type.heading.of(view.learn), color = p.ink)
                        if (w.ipa.isNotEmpty() && view.learn == s.work(r.series).series.original) Text("[${w.ipa}] · ${w.pos}", style = Type.small, color = p.inkSoft)
                    }
                    Text(w.text[view.read], style = Type.body.of(view.read), color = p.inkSoft)
                }
                Hair()
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
    val audio = s.narrator.path(r.series, chapter, letter.id, "w${index + 1}_${view.learn.code}")
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
                    if (w.ipa.isNotEmpty() && view.learn == s.work(r.series).series.original) Text("[${w.ipa}] · ${w.pos}", style = Type.small, color = p.inkSoft)
                }
                SpeakerButton(false, stringResource(R.string.listen_line), onDark = true) { s.narrator.play(audio) }
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

/** "1888-02-21" → "21 FÉVR." (소인용, 프랑스어 약자). */
fun dayOf(date: String): String = runCatching {
    // 날짜를 모르는 편지는 "1888-04" 처럼 달까지만: 소인에는 달 이름만
    if (date.length == 7) return@runCatching java.time.YearMonth.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.FRENCH))
    val d = java.time.LocalDate.parse(date.take(10))
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH))
}.getOrDefault(date)

/** 대화방 머리의 날짜: 앱 글자 언어로 ("1888년 2월 21일" / "21 February 1888"). 장소는 소인에 있다. */
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
