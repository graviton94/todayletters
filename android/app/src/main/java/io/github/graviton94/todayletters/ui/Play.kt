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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
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
        ReplyMode.MATCH -> Match(s, r, onClose = { asking = true })
        ReplyMode.ALOUD -> Aloud(s, r, onClose = { asking = true })
        else -> Constellation(s, r, onClose = { asking = true })
    }
    if (asking) Ask(
        stringResource(R.string.play_leave_title), stringResource(R.string.play_leave_yes), stringResource(R.string.play_leave_no),
        onYes = { asking = false; s.recorder.stop(); s.back() }, onNo = { asking = false },
    )
}

/**
 * 별자리 잇기: 뜻에 맞게 낱말 별을 순서대로 누른다. 맞는 별만 켜지고, 켜질 때 바로 앞 별에서 그 별까지 선이 한 번 그어진다.
 * 틀린 별은 켜지지 않는다 (잠깐 붉게 흔들리고 알려 줌). 처음 한 번은 안내 카드와 다음 별의 빛 고리로 같이 한다.
 * 라이트 · 다크 모두 같은 모양, 색만 다르다.
 */
@Composable
private fun Constellation(s: AppState, r: Route.Play, onClose: () -> Unit) {
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
            s.cue(false)
            wrong = idx
        }
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(picked.size, answer.size, onClose = onClose, onHelp = { helpId(s.route)?.let { s.coachAgain(it) } }, count = "${picked.size} / ${answer.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            PracticeTitle("CONSTELLATION", stringResource(R.string.mode_constellation))
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
 * 낱말 맞추기 (v18, 별자리처럼): 왼쪽 낱말 별에서 오른쪽 뜻 별로 선을 끌어 잇는다 (낱말을 누르고 뜻을 눌러도 된다).
 * 맞으면 선이 금빛으로 굳고 발음이 나온다. 틀리면 붉은 점선이 잠깐 떨리다 사라진다. 연속으로 맞히면 ‘연속 N’.
 * 다 이으면 별자리가 한 번 빛나고 마치기.
 */
@Composable
private fun Match(s: AppState, r: Route.Play, onClose: () -> Unit) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    val view0 = androidx.compose.ui.platform.LocalView.current
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val (pairs, order) = remember(letter.id, view.learn, view.read) { Exercises.matchBoard(letter, view.learn, view.read) }
    var sel by remember { mutableStateOf<Int?>(null) }
    var matched by remember(letter.id) { mutableStateOf(setOf<Int>()) }
    var wrongLine by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var combo by remember { mutableIntStateOf(0) }
    var drag by remember { mutableStateOf<Offset?>(null) }
    val glow = remember { Animatable(0f) }
    val done = matched.size == pairs.size
    LaunchedEffect(wrongLine) { if (wrongLine != null) { delay(650); wrongLine = null } }
    LaunchedEffect(done) { if (done && !s.reducedMotion) { glow.animateTo(1f, tween(450)); glow.animateTo(0f, tween(900)) } }
    fun say(k: Int) = s.narrator.play(s.narrator.path(r.room.series, chapter, letter.id, "w${k + 1}_${view.learn.code}"))
    fun connect(l: Int, row: Int) {
        val k = order[row]
        if (k in matched || l in matched) return
        if (l == k) {
            matched = matched + k; combo++
            if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            s.cue(true)
            say(k)
        } else {
            wrongLine = l to row; combo = 0
            buzzWrong(view0, s.app.haptics); s.cue(false)
        }
        sel = null
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(matched.size, pairs.size, onClose = onClose, onHelp = { helpId(s.route)?.let { s.coachAgain(it) } }, count = "${matched.size} / ${pairs.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PracticeTitle("MATCH", stringResource(R.string.match_prompt))
            Text(if (combo >= 2) stringResource(R.string.match_combo, combo) else " ", style = Type.small.ui(), color = p.giltText)
            val rowH = 58.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxWidth().height(rowH * pairs.size + 16.dp).background(p.sky).border(Tokens.Stroke.hair, p.hair),
            ) {
                val w = constraints.maxWidth.toFloat()
                val rh = with(density) { rowH.toPx() }
                val top = with(density) { 8.dp.toPx() }
                val lx = w * 0.42f; val rx = w * 0.58f
                fun cy(i: Int) = top + rh * (i + 0.5f)
                fun nearest(y: Float) = ((y - top) / rh).toInt().coerceIn(0, pairs.lastIndex)
                Box(Modifier.matchParentSize().pointerInput(pairs, matched) {
                    detectDragGestures(
                        onDragStart = { pos -> val i = nearest(pos.y); if (pos.x < w * 0.5f && i !in matched) { sel = i; drag = pos } },
                        onDrag = { ch, _ -> if (drag != null) drag = ch.position },
                        onDragEnd = { val d = drag; val l = sel; drag = null; if (d != null && l != null && d.x > w * 0.5f) connect(l, nearest(d.y)) },
                        onDragCancel = { drag = null },
                    )
                }.drawBehind {
                    val gl = p.gilt.copy(alpha = (0.85f + 0.15f * glow.value))
                    matched.forEach { k ->
                        val a = Offset(lx, cy(k)); val b = Offset(rx, cy(order.indexOf(k)))
                        if (glow.value > 0f) drawLine(p.gilt.copy(alpha = 0.35f * glow.value), a, b, 7.dp.toPx())
                        drawLine(gl, a, b, 1.8.dp.toPx())
                    }
                    wrongLine?.let { (l, row) ->
                        drawLine(p.wrong, Offset(lx, cy(l)), Offset(rx, cy(row)), 1.6.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 7f)))
                    }
                    val d = drag; val l = sel
                    if (d != null && l != null) drawLine(p.ink.copy(alpha = 0.7f), Offset(lx, cy(l)), d, 1.2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 7f)))
                    val r0 = 6.dp.toPx()
                    pairs.indices.forEach { i ->
                        val on = i in matched || sel == i
                        if (glow.value > 0f && i in matched) drawCircle(p.gilt.copy(alpha = 0.3f * glow.value), r0 * 2.2f, Offset(lx, cy(i)))
                        if (on) drawCircle(p.gilt, r0, Offset(lx, cy(i))) else drawCircle(p.gilt, r0, Offset(lx, cy(i)), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        val k = order[i]
                        val bad = wrongLine?.second == i
                        if (k in matched) drawCircle(p.gilt, r0, Offset(rx, cy(i)))
                        else drawCircle(if (bad) p.wrong else p.gilt, r0, Offset(rx, cy(i)), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    }
                })
                val lw = with(density) { (lx - 16.dp.toPx()).toDp() }
                val rStart = with(density) { (rx + 14.dp.toPx()).toDp() }
                val rw = with(density) { (w - rx - 18.dp.toPx()).toDp() }
                pairs.forEachIndexed { i, (word, _) ->
                    Box(Modifier.offset(y = 8.dp + rowH * i).width(lw).height(rowH).clickable(enabled = i !in matched, role = Role.Button) { sel = i; say(i) },
                        contentAlignment = Alignment.CenterEnd) {
                        Text(word, style = Type.heading.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display).of(view.learn),
                            color = if (i in matched) p.giltText else p.ink, maxLines = 2, textAlign = TextAlign.End)
                    }
                }
                order.forEachIndexed { row, k ->
                    val bad = wrongLine?.second == row
                    Box(Modifier.offset(x = rStart, y = 8.dp + rowH * row).width(rw).height(rowH)
                        .clickable(enabled = k !in matched, role = Role.Button) { sel?.let { connect(it, row) } },
                        contentAlignment = Alignment.CenterStart) {
                        Text(pairs[k].second, style = Type.small.of(view.read), maxLines = 2,
                            color = when { bad -> p.wrong; k in matched -> p.giltText; else -> p.ink })
                    }
                }
            }
            Text(stringResource(if (done) R.string.match_done else R.string.match_hint), style = Type.small.ui(), color = if (done) p.correct else p.inkSoft, textAlign = TextAlign.Center)
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Primary(stringResource(R.string.play_finish), enabled = done) { s.replyAndContinue(r.room, ReplyMode.MATCH) }
        }
    }
}

/**
 * 편지 안 따라 읽기 (v18): 복습 · 봉인과 같은 따라 읽기 화면 하나 (ReadAlong) 에서, 이 편지의 한 문장만.
 * 문장은 이 편지에서 낱말 수가 알맞은(4~14) 가장 짧은 문장. 녹음까지 하면 마칠 수 있고, 점수는 같이 보여 준다.
 */
@Composable
private fun Aloud(s: AppState, r: Route.Play, onClose: () -> Unit) {
    val (_, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val i = remember(letter.id, view.learn) {
        letter.messages.indices.filter { letter.messages[it].text[view.learn].split(" ").size in 4..14 }
            .minByOrNull { letter.messages[it].text[view.learn].length } ?: letter.messages.lastIndex
    }
    ReadAlong(s, r.room, only = listOf(i), onClose = onClose, onFinish = { s.replyAndContinue(r.room, ReplyMode.ALOUD) })
}

/** 읽는 자리 칠하기: 읽은 낱말은 먹색, 지금 낱말은 금빛 바탕, 남은 낱말은 흐리게. [pos] < 0 이면 모두 먹색. */
internal fun readAlong(text: String, pos: Int, done: Color, rest: Color, now: Color): androidx.compose.ui.text.AnnotatedString =
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
