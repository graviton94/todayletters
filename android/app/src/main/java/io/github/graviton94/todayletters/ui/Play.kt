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
            if (done) Primary(stringResource(R.string.play_send)) { s.replied(r.room, ReplyMode.CONSTELLATION); s.back() }
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

/** 따라 읽기: 원어 듣기 → 녹음 → 보내기. MVP 는 녹음한 것만으로 완료 (채점 없음). 녹음은 기기 안에만. */
@Composable
private fun Aloud(s: AppState, r: Route.Play) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val i = letter.messages.lastIndex
    val line = letter.messages[i].text
    var recording by remember { mutableStateOf(false) }
    var has by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { s.recorder.stop() } }

    Column(Modifier.fillMaxSize()) {
        PlayHeader(s, stringResource(R.string.mode_aloud), "")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(stringResource(R.string.aloud_prompt), style = Type.body.ui(), color = p.inkSoft)
            Incoming { Pair2(line[view.learn], view.learn, line[view.read], view.read) }
            Secondary(stringResource(R.string.aloud_listen)) { s.narrator.play(s.narrator.path(r.room.series, chapter, letter.id, "m${i + 1}_${view.learn.code}")) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(Tokens.Size.mic).background(if (recording) p.wrong else p.paper, CircleShape).border(Tokens.Stroke.hair, p.giltText, CircleShape)
                        .clickable(role = Role.Button) {
                            if (!allowed) { ask.launch(Manifest.permission.RECORD_AUDIO); return@clickable }
                            if (recording) { s.recorder.stop(); recording = false; has = true } else { s.narrator.stop(); recording = s.recorder.start() }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(if (recording) "■" else "●", style = Type.heading, color = if (recording) p.onFill else p.giltText) }
            }
            Text(
                stringResource(if (recording) R.string.aloud_recording else if (has) R.string.aloud_recorded else R.string.aloud_tap),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (has) Secondary(stringResource(R.string.aloud_mine)) { s.recorder.play() }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4)) {
            Primary(stringResource(R.string.play_send), enabled = has && !recording) { s.replied(r.room, ReplyMode.ALOUD); s.back() }
        }
    }
}

/** 화면 안에서 묻기 (시스템 대화상자 대신). */
@Composable
fun Ask(title: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    val p = Ink.palette
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onNo), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Tokens.Space.s6).background(p.paper).border(Tokens.Stroke.hair, p.ink).clickable(enabled = false) {}.padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(title, style = Type.heading.ui(), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.weight(1f)) { Secondary(yes, onClick = onYes) }
                Box(Modifier.weight(1f)) { Primary(no, onClick = onNo) }
            }
        }
    }
}
