package io.github.graviton94.todayletters.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 답장 (플레이어블). 뒤로 가기는 바로 나가지 않고 한 번 묻는다. */
@Composable
fun Play(s: AppState, r: Route.Play) {
    var asking by remember { mutableStateOf(false) }
    BackHandler { asking = true }
    when (r.mode) {
        ReplyMode.MATCH -> Match(s, r)
        ReplyMode.ALOUD -> Aloud(s, r)
        else -> Constellation(s, r)
    }
    if (asking) Ask(
        stringResource(R.string.play_leave_title), stringResource(R.string.play_leave_yes), stringResource(R.string.play_leave_no),
        onYes = { asking = false; s.recorder.stop(); s.back() }, onNo = { asking = false },
    )
}

@Composable
private fun PlayHeader(s: AppState, label: String, step: String) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2, vertical = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(p.ink) }
        Text(label, style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
        if (step.isNotEmpty()) Caps(step)
        IconButton(stringResource(R.string.help), onClick = { helpId(s.route)?.let { s.coachAgain(it) } }) { HelpGlyph(p.ink) }
    }
    Hair()
}

/**
 * 별자리 잇기: 뜻에 맞게 낱말 별을 순서대로 누른다. 맞는 별만 켜지고, 켜질 때 바로 앞 별에서 그 별까지 선이 한 번 그어진다.
 * 틀린 별은 켜지지 않는다 (잠깐 붉게 흔들리고 알려 줌). 처음 한 번은 안내 카드와 다음 별의 빛 고리로 같이 한다.
 * 라이트 · 다크 모두 같은 모양, 색만 다르다.
 */
@Composable
private fun Constellation(s: AppState, r: Route.Play) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    val (_, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val (sentence, tri) = Exercises.replyFor(letter, view.learn)
    val (answer, pieces) = remember(letter.id, view.learn) { Exercises.constellation(sentence, letter.id.hashCode()) }
    var picked by remember(letter.id, view.learn) { mutableStateOf(listOf<Int>()) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    s.coachTick
    val guided = s.coach.due("play_constellation", calm = true)
    val link = remember { Animatable(1f) }
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "k")
    val done = picked.size == answer.size
    val scope = rememberCoroutineScope()

    LaunchedEffect(wrong) { if (wrong != null) { delay(700); wrong = null } }

    fun tap(idx: Int) {
        if (done || idx in picked) return
        if (Exercises.isNext(answer, picked.size, pieces[idx])) {
            if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            picked = picked + idx
            scope.launch { link.snapTo(0f); link.animateTo(1f, tween(Tokens.Motion.starLinkMs, easing = FastOutSlowInEasing)) }
            if (picked.size == answer.size && guided) s.coachDone("play_constellation")
        } else {
            if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            wrong = idx
        }
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PlayHeader(s, stringResource(R.string.mode_constellation), "")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Slip(seed = 5, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(stringResource(if (guided) R.string.guide_title else R.string.play_meaning), style = Type.capsSm.ui(), color = Color(0xFF7A5A20))
                    if (guided) Text(stringResource(R.string.guide_body), style = Type.body.ui(), color = p.slipInk)
                    Text("“${tri[view.read]}”", style = Type.heading.of(view.read), color = p.slipInk)
                }
            }
            Sky(
                pieces = pieces, picked = picked, learn = view.learn, link = link.value, wrong = wrong,
                hint = if (guided && !done) pieces.indices.firstOrNull { it !in picked && Exercises.isNext(answer, picked.size, pieces[it]) } else null,
                pulse = if (s.reducedMotion) 0.5f else pulse.value, onTap = ::tap,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Hair()
                Text(stringResource(R.string.play_mine), style = Type.capsSm.ui(), color = p.giltText, modifier = Modifier.padding(top = Tokens.Space.s2))
                val rest = answer.size - picked.size
                Text(
                    (picked.joinToString(" ") { pieces[it] } + if (rest > 0) " " + List(rest.coerceAtMost(4)) { "_" }.joinToString(" ") else "").trim(),
                    style = Type.title.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic).of(view.learn),
                    color = p.ink,
                )
            }
            when {
                done -> Text(stringResource(R.string.play_right), style = Type.small.ui(), color = p.correct)
                wrong != null -> Text(stringResource(R.string.play_wrong_star), style = Type.small.ui(), color = p.wrong)
                showAnswer -> Text(answer.joinToString(" "), style = Type.small.of(view.learn), color = p.inkSoft)
                guided -> Text(stringResource(R.string.guide_after), style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (done) Primary(stringResource(R.string.play_send)) { s.replyAndContinue(r.room, ReplyMode.CONSTELLATION) }
            else Secondary(stringResource(R.string.play_show)) { showAnswer = true }
        }
    }
}

/**
 * 하늘: 낱말 별을 두 줄 칸에 흩어 놓는다 (같은 편지는 늘 같은 자리). 켜진 별 사이에 선, 마지막 선은 [link] 만큼 그어지는 중.
 * 별의 자리를 재고 나서 선을 그리도록, 자리 계산과 그리기를 한 Layout 안에서 한다.
 */
@Composable
private fun Sky(
    pieces: List<String>, picked: List<Int>, learn: io.github.graviton94.todayletters.core.Lang, link: Float, wrong: Int?,
    hint: Int?, pulse: Float, onTap: (Int) -> Unit,
) {
    val p = Ink.palette
    val centers = remember(pieces) { Array(pieces.size) { Offset.Zero } }
    val rows = (pieces.size + 1) / 2
    val rowH = 84.dp
    val jitter = remember(pieces) { val rnd = java.util.Random(pieces.joinToString().hashCode().toLong()); List(pieces.size) { rnd.nextFloat() to rnd.nextFloat() } }
    Layout(
        modifier = Modifier.fillMaxWidth().height(rowH * rows + 24.dp).background(p.sky).border(Tokens.Stroke.hair, p.hair)
            .drawBehind {
                // 선: 켜진 순서대로, 앞 별 → 뒤 별. 시작은 흐리고 끝은 진하게.
                for (k in 1 until picked.size) {
                    val a = centers[picked[k - 1]]; val b = centers[picked[k]]
                    val t = if (k == picked.lastIndex) link else 1f
                    val end = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                    drawLine(Brush.linearGradient(listOf(p.gilt.copy(alpha = 0.3f), p.gilt), start = a, end = b), a, end, 1.6.dp.toPx())
                }
                // 처음 안내: 다음 별의 빛 고리
                hint?.let { h ->
                    val c = centers[h]
                    drawCircle(p.gilt.copy(alpha = (1f - pulse) * 0.7f), 10.dp.toPx() + 16.dp.toPx() * pulse, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            },
        content = {
            pieces.forEachIndexed { idx, w ->
                val on = idx in picked
                Column(
                    Modifier.clickable(enabled = !on, role = Role.Button) { onTap(idx) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.size(Tokens.Size.starNow + 6.dp)) {
                        val r = 7.dp.toPx()
                        val c = Offset(size.width / 2, size.height / 2)
                        when {
                            on -> { drawCircle(p.gilt.copy(alpha = 0.3f), r * 1.8f, c); drawCircle(p.gilt, r, c) }
                            wrong == idx -> drawCircle(p.wrong, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            else -> drawCircle(p.gilt, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        }
                    }
                    Text(w, style = Type.chipWord.of(learn), color = if (wrong == idx) p.wrong else p.ink, maxLines = 1)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints(maxWidth = width / 2)) }
        val pad = 12.dp.roundToPx()
        layout(width, constraints.maxHeight) {
            placeables.forEachIndexed { i, pl ->
                val row = i / 2; val col = i % 2
                val cellW = width / 2f
                val (jx, jy) = jitter[i]
                val cx = cellW * col + cellW * (0.3f + jx * 0.4f) + if (row % 2 == 1) cellW * 0.08f else 0f
                val x = (cx - pl.width / 2f).toInt().coerceIn(pad / 2, width - pl.width - pad / 2)
                val y = (pad + rowH.toPx() * row + rowH.toPx() * (jy * 0.25f)).toInt()
                pl.place(x, y)
                // 별(맨 위 동그라미)의 가운데
                centers[i] = Offset(x + pl.width / 2f, y + 6.dp.toPx() + (Tokens.Size.starNow + 6.dp).toPx() / 2)
            }
        }
    }
}

/**
 * 낱말 맞추기: 왼쪽 낱말(배울 언어)을 누르고 오른쪽 뜻(번역)을 누른다. 짝이 맞으면 둘 다 금빛으로 잠기고 발음이 나온다.
 * 틀리면 잠깐 붉게 흔들린다. 다 맞히면 마치기.
 */
@Composable
private fun Match(s: AppState, r: Route.Play) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val (pairs, order) = remember(letter.id, view.learn, view.read) { Exercises.matchBoard(letter, view.learn, view.read) }
    var left by remember { mutableStateOf<Int?>(null) }
    var matched by remember(letter.id) { mutableStateOf(setOf<Int>()) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    val done = matched.size == pairs.size
    LaunchedEffect(wrong) { if (wrong != null) { delay(600); wrong = null } }

    fun pickRight(k: Int) {
        val l = left ?: return
        if (l == k) {
            matched = matched + k; left = null
            if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            s.narrator.play(s.narrator.path(r.room.series, chapter, letter.id, "w${k + 1}_${view.learn.code}"))
        } else {
            wrong = k; left = null
            if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PlayHeader(s, stringResource(R.string.mode_match), "${matched.size} / ${pairs.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(stringResource(R.string.match_hint), style = Type.small.ui(), color = p.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    pairs.forEachIndexed { k, (w, _) ->
                        MatchCard(w, view.learn, on = left == k, matched = k in matched, wrong = false) { if (k !in matched) left = k }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    order.forEach { k ->
                        MatchCard(pairs[k].second, view.read, on = false, matched = k in matched, wrong = wrong == k) { if (k !in matched) pickRight(k) }
                    }
                }
            }
            when {
                done -> Text(stringResource(R.string.match_done), style = Type.small.ui(), color = p.correct)
                wrong != null -> Text(stringResource(R.string.match_wrong), style = Type.small.ui(), color = p.wrong)
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Primary(stringResource(R.string.play_finish), enabled = done) { s.replyAndContinue(r.room, ReplyMode.MATCH) }
        }
    }
}

@Composable
private fun MatchCard(text: String, lang: io.github.graviton94.todayletters.core.Lang, on: Boolean, matched: Boolean, wrong: Boolean, onClick: () -> Unit) {
    val p = Ink.palette
    val shake by androidx.compose.animation.core.animateFloatAsState(if (wrong) 1f else 0f, tween(120), label = "shake")
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .graphicsLayer { translationX = kotlin.math.sin(shake * 18f) * 6.dp.toPx() * shake; alpha = if (matched) 0.55f else 1f }
            .background(when { matched -> p.hide; on -> p.fill; else -> p.leaf })
            .border(1.dp, when { wrong -> p.wrong; on -> p.fill; matched -> p.hair; else -> p.line })
            .pressable(enabled = !matched, onClick = onClick).padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (matched) CheckMark(true, 16.dp)
            Text(text, style = Type.body.of(lang), color = if (on) p.onFill else p.ink, maxLines = 2)
        }
    }
}

/**
 * 따라 읽기 (답장이 아니라 연습): 빈센트가 읽는 동안 읽는 자리의 낱말이 차례로 칠해진다.
 * 그다음 소리 내어 읽고 녹음한다. MVP 는 녹음만으로 완료 (채점 없음). 녹음은 기기 안에만.
 * 문장은 이 편지에서 낱말 수가 알맞은(4~14) 가장 짧은 문장.
 */
@Composable
private fun Aloud(s: AppState, r: Route.Play) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val i = remember(letter.id, view.learn) {
        letter.messages.indices.filter { letter.messages[it].text[view.learn].split(" ").size in 4..14 }
            .minByOrNull { letter.messages[it].text[view.learn].length } ?: letter.messages.lastIndex
    }
    val line = letter.messages[i].text
    val sentence = io.github.graviton94.todayletters.core.Breaks.plain(line[view.learn])
    val audio = s.narrator.path(r.room.series, chapter, letter.id, "m${i + 1}_${view.learn.code}")
    val chunks = remember(audio) { s.narrator.chunks(audio) }
    var playing by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(-1) }        // 지금 칠하는 글자 자리
    var recording by remember { mutableStateOf(false) }
    var has by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { s.recorder.stop(); s.narrator.stop() } }
    LaunchedEffect(s.away) { if (recording) { has = s.recorder.file.exists() || has; recording = false } }

    // 덩어리 시각 → 글자 자리. 덩어리 글자를 문장에서 차례로 찾아, 덩어리 안에서는 시간에 비례해 나아간다.
    val spans = remember(sentence, chunks) {
        var from = 0
        chunks.map { c ->
            val at = sentence.indexOf(c.text, from).takeIf { it >= 0 } ?: from
            from = at + c.text.length
            Triple(c, at, at + c.text.length)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val t = s.narrator.position()
            if (t >= 0) {
                val hit = spans.lastOrNull { it.first.start <= t }
                pos = when {
                    hit == null -> -1
                    t >= hit.first.end -> hit.third
                    else -> hit.second + ((t - hit.first.start).toFloat() / (hit.first.end - hit.first.start).coerceAtLeast(1) * (hit.third - hit.second)).toInt()
                }
            }
            androidx.compose.runtime.withFrameMillis { }
        }
    }
    fun listen() {
        if (playing) { s.narrator.stop(); playing = false; return }
        s.recorder.stop(); recording = false
        playing = s.narrator.play(audio) { playing = false; heard = true; pos = sentence.length }
        if (spans.isEmpty()) pos = -1
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PlayHeader(s, stringResource(R.string.mode_aloud), "")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(stringResource(R.string.aloud_prompt), style = Type.small.ui(), color = p.inkSoft)
            Slip(seed = 9, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(readAlong(sentence, pos, p.slipInk, p.slipSoft.copy(alpha = 0.75f), p.gilt.copy(alpha = 0.35f)), style = Type.target.of(view.learn))
                    Text(line[view.read], style = Type.base.of(view.read), color = p.slipSoft)
                }
            }
            Secondary(stringResource(if (playing) R.string.room_stop else if (heard) R.string.aloud_again else R.string.aloud_listen)) { listen() }
            val recStart = stringResource(R.string.a11y_record); val recStop = stringResource(R.string.a11y_record_stop)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(Tokens.Size.mic).background(if (recording) p.wrong else Color.Transparent, CircleShape).border(1.dp, p.giltText, CircleShape)
                        .semantics { contentDescription = if (recording) recStop else recStart }
                        .pressable(role = Role.Button) {
                            if (!allowed) { ask.launch(Manifest.permission.RECORD_AUDIO); return@pressable }
                            if (recording) { has = s.recorder.stop() || has; recording = false } else { s.narrator.stop(); playing = false; recording = s.recorder.start() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        if (recording) drawRect(p.onFill) else drawCircle(p.wrong, size.minDimension / 2)
                    }
                }
            }
            Text(
                stringResource(if (recording) R.string.aloud_recording else if (has) R.string.aloud_recorded else R.string.aloud_tap),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (has) Secondary(stringResource(R.string.aloud_mine)) { s.narrator.stop(); playing = false; s.recorder.play() }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Primary(stringResource(R.string.aloud_finish), enabled = has && !recording) { s.replyAndContinue(r.room, ReplyMode.ALOUD) }
        }
    }
}

/** 읽는 자리 칠하기: 읽은 낱말은 먹색, 지금 낱말은 금빛 바탕, 남은 낱말은 흐리게. [pos] < 0 이면 모두 먹색. */
private fun readAlong(text: String, pos: Int, done: Color, rest: Color, now: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        if (pos < 0) { withStyle(androidx.compose.ui.text.SpanStyle(color = done)) { append(text) }; return@buildAnnotatedString }
        val words = Regex("\\S+").findAll(text).toList()
        var last = 0
        words.forEach { m ->
            append(text.substring(last, m.range.first))
            val style = when {
                m.range.last < pos -> androidx.compose.ui.text.SpanStyle(color = done)
                m.range.first <= pos -> androidx.compose.ui.text.SpanStyle(color = done, background = now)
                else -> androidx.compose.ui.text.SpanStyle(color = rest)
            }
            withStyle(style) { append(m.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }

/** 화면 안에서 묻기 (시스템 대화상자 대신). */
@Composable
/** 묻는 창: 제목 17 · 본문 14 · 단추 46 (작은 단추 둘). 화면 너비의 84%. */
fun Ask(title: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit, body: String? = null) {
    val p = Ink.palette
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onNo), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.84f).background(p.paper).border(Tokens.Stroke.hair, p.line).clickable(enabled = false) {}
                .padding(start = Tokens.Space.s5, end = Tokens.Space.s5, top = Tokens.Space.s5, bottom = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Text(title, style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
            if (body != null) Text(body, style = Type.small.ui(), color = p.inkSoft)
            Row(Modifier.padding(top = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.weight(1f)) { Secondary(yes, small = true, onClick = onYes) }
                Box(Modifier.weight(1f)) { Primary(no, small = true, onClick = onNo) }
            }
        }
    }
}
