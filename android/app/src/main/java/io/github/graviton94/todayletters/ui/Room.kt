package io.github.graviton94.todayletters.ui

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
    var mapOpen by remember { mutableStateOf(false) }
    var playing by remember(letter.id) { mutableStateOf<Int?>(null) }
    var all by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val pace = when (view.pace) { TypingPace.CALM -> 1f; TypingPace.QUICK -> 0.55f; TypingPace.INSTANT -> 0f }
    val animate = pace > 0f && !s.reducedMotion
    fun audio(i: Int) = s.narrator.path(id, chapter, letter.id, "m${i + 1}_${view.learn.code}")

    fun stopAll() { all?.cancel(); all = null; s.narrator.stop(); playing = null }
    fun speak(i: Int) {
        if (playing == i) { stopAll(); return }
        stopAll()
        if (s.narrator.play(audio(i)) { if (playing == i) playing = null }) playing = i
    }
    fun speakAll() {
        if (all != null) { stopAll(); return }
        stopAll()
        all = scope.launch {
            for (i in 0 until written) {
                playing = i
                suspendCancellableCoroutine { c ->
                    c.invokeOnCancellation { s.narrator.stop() }
                    if (!s.narrator.play(audio(i)) { if (c.isActive) c.resume(Unit) } && c.isActive) c.resume(Unit)
                }
            }
            playing = null; all = null
        }
    }
    DisposableEffect(letter.id) { onDispose { s.narrator.stop() } }

    // 도착: 남은 문장을 한 조각씩. 조각의 글자가 다 써지면 다음 조각.
    LaunchedEffect(letter.id) {
        while (arrived < total) {
            val i = arrived
            writing = true
            if (animate) delay((Tokens.Motion.typingDotsMs * pace).toLong())
            arrived = i + 1
            list.animateScrollToItem((i + 1).coerceAtMost(list.layoutInfo.totalItemsCount))
            snapshotFlow { written }.first { it > i }
            writing = false
            // 자동 낭독 (기본 켜짐): 조각이 다 써지면 그 문장을 빈센트 목소리로 읽고 나서 다음 조각
            if (s.app.sound && all == null) {
                playing = i
                suspendCancellableCoroutine { c ->
                    c.invokeOnCancellation { s.narrator.stop() }
                    if (!s.narrator.play(audio(i)) { if (c.isActive) c.resume(Unit) } && c.isActive) c.resume(Unit)
                }
                if (playing == i) playing = null
            }
            s.save(id, chapter, letter.id, s.progress(id, chapter, letter.id).copy(shown = i + 1))
            if (animate) delay((Tokens.Motion.inkPauseMs * pace).toLong())
        }
    }

    val progress = s.progress(id, chapter, letter.id)
    val modes = Plays.inLetter.filter { it in s.seriesSettings(id).modes }
    val nextPlay = modes.firstOrNull { it !in progress.replied }
    val reply = Exercises.replyFor(letter, view.learn).second
    val finished = written >= total
    val cardMap = remember(s.version) { s.cardMap() }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.4f)) {
        RoomHeader(s, work.portrait, work.sender, if (writing) "${work.name[uiLang()]} · ${stringResource(R.string.room_typing)}" else dateLine(letter.date, letter.place),
            auto = s.app.sound, onAuto = { if (s.app.sound) stopAll(); s.update(s.app.copy(sound = !s.app.sound)) }, onInfo = { s.go(Route.RoomInfo(r)) })
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val notYet = stringResource(R.string.step_not_yet)
        TodayStrip(read = written, total = total, modes = modes, replied = progress.replied, done = progress.done) { step ->
            // 0 = 읽기, 1..modes = 연습, 마지막 = 그림. 다 한 단계와 지금 단계만 이동, 나머지는 짧은 안내
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
                    Postmark(letter.place, dayOf(letter.date), letter.date.take(4))
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
            letter.moments.filter { it.after < 0 }.forEachIndexed { k, mo ->
                item(key = "${letter.id}:pre:$k") { MomentCard(s, work, mo, onMap = { mapOpen = true }, onPhoto = { s.go(Route.Artwork(id, r.letter, r)) }) }
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
                        MomentCard(s, work, mo, onMap = { mapOpen = true }, onPhoto = {
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
                if (ReplyMode.CONSTELLATION in progress.replied) item {
                    val nextLetter = work.chapters.getOrNull(r.chapter - 1)?.letters?.getOrNull(r.letter)
                    val unreadByHim = nextLetter != null && !s.store.started(s.key(id, chapter, nextLetter.id)) && s.progress(id, chapter, nextLetter.id).shown == 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        if (unreadByHim) Text("1", style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.giltText,
                            modifier = Modifier.padding(end = 6.dp, bottom = 2.dp))
                        TheoNote(reply[view.learn], view.learn, if (view.showRead) reply[view.read] else null, view.read, work.recipient[view.learn])
                    }
                }
                // 처음 한 번: 세 가지를 모두 해 보자는 안내
                if (!progress.done && progress.replied.isEmpty() && s.coach.due("first_steps", calm = true)) item {
                    s.coachTick
                    Slip(seed = 3, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Text(stringResource(R.string.first_guide_title), style = Type.heading.ui(), color = p.slipInk)
                            Text(stringResource(R.string.first_guide_body), style = Type.small.ui(), color = p.slipSoft)
                            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                                modes.forEachIndexed { k, m ->
                                    if (k > 0) Text("→", style = Type.small, color = p.slipSoft)
                                    Text(stringResource(modeLabel(m)), style = Type.small.ui(), color = p.slipInk)
                                }
                            }
                        }
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
                    Primary(stringResource(R.string.done_home)) { stopAll(); s.go(r.from) }
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
    if (words) WordSheet(s, r, onClose = { words = false })
    wordFocus?.let { k -> WordCard(s, r, k, onClose = { wordFocus = null }) }
    if (mapOpen) MapSheet(s, work, onClose = { mapOpen = false })
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

/** 오늘의 순서: 읽기 → (낱말 → 따라 읽기 → 답장, 켠 것만) → 그림. 한 일은 금빛 체크, 지금 할 일은 먹색, 남은 일은 흐리게. */
@Composable
private fun TodayStrip(read: Int, total: Int, modes: List<ReplyMode>, replied: Set<ReplyMode>, done: Boolean, onStep: (Int) -> Unit) {
    val p = Ink.palette
    val readDone = read >= total
    val next = if (!readDone) null else modes.firstOrNull { it !in replied }
    val steps = buildList {
        add(stringResource(R.string.step_read, read, total) to (if (readDone) 2 else 1))
        modes.forEach { m ->
            val label = stringResource(when (m) { ReplyMode.MATCH -> R.string.step_match; ReplyMode.ALOUD -> R.string.step_aloud; else -> R.string.step_reply })
            add(label to when { m in replied -> 2; m == next -> 1; else -> 0 })
        }
        add(stringResource(R.string.step_plate) to (if (done) 2 else if (readDone && next == null) 1 else 0))
    }
    Column(Modifier.background(p.paper)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Tokens.Space.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            steps.forEachIndexed { i, (label, state) ->
                if (i > 0) Box(Modifier.weight(1f).height(1.dp).background(if (state > 0) p.giltText.copy(alpha = 0.5f) else p.hair))
                Row(
                    Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(if (state == 1) p.giltText.copy(alpha = 0.12f) else Color.Transparent)
                        .pressable(haptic = false) { onStep(i) }.heightIn(min = 48.dp).padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    when (state) {
                        2 -> Box(Modifier.size(14.dp).background(p.giltText, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(8.dp)) {
                                val w = size.width
                                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.1f, w * 0.55f); lineTo(w * 0.4f, w * 0.82f); lineTo(w * 0.92f, w * 0.2f) }, p.paper,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            }
                        }
                        1 -> Box(Modifier.size(8.dp).background(p.ink, androidx.compose.foundation.shape.CircleShape))
                        else -> Box(Modifier.size(8.dp).border(1.dp, p.hideInk, androidx.compose.foundation.shape.CircleShape))
                    }
                    Text(label, style = Type.small.ui().copy(fontSize = Tokens.Text.capsSm * 1.15f), maxLines = 1,
                        color = when (state) { 2 -> p.giltText; 1 -> p.ink; else -> p.hideInk })
                }
            }
        }
        Hair()
    }
}

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
    val d = java.time.LocalDate.parse(date.take(10))
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH))
}.getOrDefault(date)

/** 대화방 머리의 날짜: 앱 글자 언어로 ("1888년 2월 21일" / "21 February 1888"). 장소는 소인에 있다. */
@Composable
fun dateLine(date: String, place: String): String {
    val ko = uiLang() == Lang.KO
    val d = runCatching { java.time.LocalDate.parse(date.take(10)) }.getOrNull() ?: return place
    val text = if (ko) "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
    else d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH))
    return text
}
