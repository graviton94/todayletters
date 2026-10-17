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
import io.github.graviton94.todayletters.data.learnVocab
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
        // 편지 이해 확인 · 오늘의 회화 (ui/Depth.kt): 편지에 데이터가 있을 때만 걸음에 든다 (Plays.forLetter)
        ReplyMode.CHECK -> CheckStep(s, r, onClose = { asking = true })
        ReplyMode.TALK -> TalkStep(s, r, onClose = { asking = true })
        // 답장: 언어 등급이 A1 · 1단이거나 저자 답장이 없으면 별자리 잇기, 그 위로는 답장 사다리 (빈칸 · 직접 쓰기).
        // 턴이 여럿이면 차례로 (턴마다 복습 카드). 턴을 보낸 뒤에는 ‘소리 내어 말해 보기’ 한 번 (건너뛰기 있음), 마지막 턴 뒤면 답장 걸음 끝
        else -> {
            val help = s.replyHelp(r.room.series)
            val letter = s.letterOf(r.room).second
            val turns = Exercises.turns(letter, s.room(r.room.series).learn).size.coerceAtLeast(1)
            var turn by androidx.compose.runtime.saveable.rememberSaveable(r) { mutableIntStateOf(0) }
            var speaking by androidx.compose.runtime.saveable.rememberSaveable(r) { mutableStateOf(false) }
            val t = turn.coerceIn(0, turns - 1)
            val advance: () -> Unit = {
                speaking = false
                if (t < turns - 1) turn = t + 1 else s.replyAndContinue(r.room, ReplyMode.CONSTELLATION)
            }
            val next: () -> Unit = {
                s.replyTurnDone(r.room, t)
                speaking = true
            }
            when {
                speaking -> ReplySpeak(s, r, t, turns, onNext = advance)
                help == io.github.graviton94.todayletters.core.Ladder.Help.LINK || letter.replies.isEmpty() -> Constellation(s, r, t, turns, next, onClose = { asking = true })
                else -> ReplyLadder(s, r, help, t, turns, next, onClose = { asking = true })
            }
        }
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
private fun Constellation(s: AppState, r: Route.Play, turn: Int, turns: Int, onDone: () -> Unit, onClose: () -> Unit) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    val (_, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val (sentence, tri) = Exercises.replyFor(letter, view.learn, turn)
    val (answer, pieces) = remember(letter.id, view.learn, turn) { Exercises.constellation(sentence, letter.id.hashCode() + turn) }
    var picked by remember(letter.id, view.learn, turn) { mutableStateOf(listOf<Int>()) }
    var wrong by remember { mutableStateOf<Int?>(null) }
    var showAnswer by remember(turn) { mutableStateOf(false) }
    // 튜토리얼 (편지 I) 의 답장 걸음: 처음 한 번 안내와 다음 별의 빛 고리. 그 뒤로는 어느 작품에서도 없이
    val guided = remember(letter.id) { s.guideDue("reply") }
    LaunchedEffect(guided) { if (guided) s.guideShown("reply") }
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
        } else {
            if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            s.cue(false)
            wrong = idx
        }
    }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        val turnLabel = if (turns > 1) stringResource(R.string.ladder_turn, turn + 1, turns) + " · " else ""
        PracticeHeader(picked.size, answer.size, onClose = onClose, onHelp = { helpId(s.route)?.let { s.coachAgain(it) } }, count = "$turnLabel${picked.size} / ${answer.size}")
        // 한 화면에 다 (굴리지 않는다): 하늘이 남은 높이를 채우고 별 사이를 줄인다. 낮은 화면은 제목을 빼고 사이를 좁힌다
        val small = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < SMALL_SKY_H
        Column(
            Modifier.weight(1f).padding(horizontal = Tokens.Space.s5, vertical = if (small) Tokens.Space.s2 else Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(if (small) Tokens.Space.s2 else Tokens.Space.s4),
        ) {
            if (!small) PracticeTitle("CONSTELLATION", stringResource(R.string.mode_constellation))
            letter.replies.getOrNull(turn)?.let { GrammarNote(s, r.room.series, it, view.learn) }
            Slip(seed = 5, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(if (small) Tokens.Space.s3 else Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(stringResource(R.string.play_meaning), style = Type.capsSm.ui(), color = Color(0xFF7A5A20))
                    if (guided) Text(stringResource(R.string.guide_reply), style = (if (small) Type.small else Type.body).ui(), color = p.slipInk)
                    Text(quoted(tri[view.read], view.read), style = (if (small) Type.body else Type.heading).of(view.read), color = p.slipInk)
                }
            }
            Sky(
                pieces = pieces, picked = picked, learn = view.learn, link = link.value, wrong = wrong,
                hint = if (!done) pieces.indices.firstOrNull { it !in picked && Exercises.isNext(answer, picked.size, pieces[it]) } else null,
                pulse = if (s.reducedMotion) 0.5f else pulse.value, onTap = ::tap,
                small = small, modifier = Modifier.weight(1f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Hair()
                Text(stringResource(R.string.play_mine), style = Type.capsSm.ui(), color = p.giltText, modifier = Modifier.padding(top = if (small) 0.dp else Tokens.Space.s2))
                val rest = answer.size - picked.size
                Text(
                    (picked.joinToString(" ") { pieces[it] } + if (rest > 0) " " + List(rest.coerceAtMost(4)) { "_" }.joinToString(" ") else "").trim(),
                    style = (if (small) Type.heading else Type.title).copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic).of(view.learn),
                    color = p.ink,
                )
            }
            when {
                done -> Text(stringResource(R.string.play_right, s.work(r.room.series).name[uiLang()]), style = Type.small.ui(), color = p.correct)
                wrong != null -> Text(stringResource(R.string.play_wrong_star), style = Type.small.ui(), color = p.wrong)
                showAnswer -> Text(answer.joinToString(" "), style = Type.small.of(view.learn), color = p.inkSoft)
                guided -> Text(stringResource(R.string.guide_after), style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (done) Primary(stringResource(if (turn < turns - 1) R.string.ladder_next_turn else R.string.play_send), shine = true) { onDone() }
            else {
                Secondary(stringResource(R.string.play_show)) { showAnswer = true }
                SkipLink { s.skipStep(r.room, ReplyMode.CONSTELLATION) }
            }
        }
    }
}

/** 이 높이(dp)보다 낮은 화면은 별자리 잇기를 작게 (제목 빼고 · 작은 낱말). */
private const val SMALL_SKY_H = 720
private val SKY_ROW_MAX = 84.dp
private val SKY_ROW_MIN = 44.dp
private val SKY_MIN_H = 140.dp

/**
 * 하늘: 낱말 별을 두 줄 칸에 흩어 놓는다 (같은 편지는 늘 같은 자리). 켜진 별 사이에 선, 마지막 선은 [link] 만큼 그어지는 중.
 * 별의 자리를 재고 나서 선을 그리도록, 자리 계산과 그리기를 한 Layout 안에서 한다.
 */
@Composable
private fun Sky(
    pieces: List<String>, picked: List<Int>, learn: io.github.graviton94.todayletters.core.Lang, link: Float, wrong: Int?,
    hint: Int?, pulse: Float, onTap: (Int) -> Unit, small: Boolean = false, modifier: Modifier = Modifier,
) {
    val p = Ink.palette
    val centers = remember(pieces) { Array(pieces.size) { Offset.Zero } }
    val jitter = remember(pieces) { val rnd = java.util.Random(pieces.joinToString().hashCode().toLong()); List(pieces.size) { rnd.nextFloat() to rnd.nextFloat() } }
    Layout(
        modifier = modifier.fillMaxWidth().heightIn(min = SKY_MIN_H, max = SKY_ROW_MAX * ((pieces.size + 1) / 2) + 24.dp).background(p.sky).border(Tokens.Stroke.hair, p.hair)
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
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (small) 2.dp else 6.dp),
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
                    Text(w, style = (if (small) Type.small else Type.chipWord).of(learn), color = if (wrong == idx) p.wrong else p.ink, maxLines = 1)
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val pad = 12.dp.roundToPx()
        // 칸: 두 줄 (낱말이 많아 한 칸이 너무 낮으면 세 줄). 칸 높이는 받은 높이를 나눠서 (최대 SKY_ROW_MAX)
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else (SKY_ROW_MAX * ((pieces.size + 1) / 2) + 24.dp).roundToPx()
        val cols = if ((h - pad * 2) / ((pieces.size + 1) / 2).coerceAtLeast(1) < SKY_ROW_MIN.roundToPx() && pieces.size > 4) 3 else 2
        val rows = ((pieces.size + cols - 1) / cols).coerceAtLeast(1)
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints(maxWidth = width / cols)) }
        val itemH = placeables.maxOfOrNull { it.height } ?: 0
        val rowPx = ((h - pad * 2 - itemH).coerceAtLeast(0) / (rows - 1).coerceAtLeast(1).toFloat()).coerceAtMost(SKY_ROW_MAX.toPx())
        layout(width, h) {
            placeables.forEachIndexed { i, pl ->
                val row = i / cols; val col = i % cols
                val cellW = width / cols.toFloat()
                val (jx, jy) = jitter[i]
                val cx = cellW * col + cellW * (0.3f + jx * 0.4f) + if (row % 2 == 1) cellW * 0.08f else 0f
                val x = (cx - pl.width / 2f).toInt().coerceIn(pad / 2, (width - pl.width - pad / 2).coerceAtLeast(pad / 2))
                // 위아래 흔들림은 칸 사이의 ¼ 안에서 (마지막 줄도 하늘 안에)
                val y = (pad + rowPx * row + rowPx * (jy * 0.25f)).toInt().coerceAtMost((h - pl.height - pad / 2).coerceAtLeast(0))
                pl.place(x, y)
                // 별(맨 위 동그라미)의 가운데
                centers[i] = Offset(x + pl.width / 2f, y + 6.dp.toPx() + (Tokens.Size.starNow + 6.dp).toPx() / 2)
            }
        }
    }
}

/**
 * 낱말 맞추기 (문맥 카드): 낱말 하나씩, 이 편지 속 그 문장을 보며 뜻 넷 가운데 하나를 고른다.
 * 복습의 뜻 고르기와 같은 이름표 · 보기 (WordLabel · MeaningOptions) 를 쓰고, 여기서는 편지 문장이 붙는다.
 * 맞으면 발음 · 번역 문장에 그 뜻이 금빛, 잠깐 뒤 다음 낱말. 틀리면 그 보기만 지우고 다시 고른다. 다 하면 표현 카드 또는 마침.
 */
@Composable
private fun Match(s: AppState, r: Route.Play, onClose: () -> Unit) {
    val p = Ink.palette
    val view0 = androidx.compose.ui.platform.LocalView.current
    val haptic = LocalHapticFeedback.current
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val pairs = remember(letter.id, view.learn, view.read) { Exercises.pairs(letter, view.learn, view.read).take(8) }
    var phrases by remember(letter.id) { mutableStateOf(false) }
    if (phrases) { PhraseCards(s, r) { s.replyAndContinue(r.room, ReplyMode.MATCH) }; return }
    var at by remember(letter.id) { mutableIntStateOf(0) }
    var picked by remember(at) { mutableStateOf<String?>(null) }
    var missed by remember(at) { mutableStateOf(setOf<String>()) }
    var shakeKey by remember { mutableIntStateOf(0) }
    val n = pairs.size
    val done = at >= n
    val k = at.coerceAtMost(n - 1)
    val right = pairs.getOrNull(k)?.second.orEmpty()
    val over = picked == right
    // 보기: 정답 + 다른 낱말 뜻 셋 (이 편지 먼저, 모자라면 같은 작품의 다른 편지 낱말). 같은 뜻 · 같은 낱말 · 빈 뜻은 빼고 (core Distractors).
    // 같은 편지는 늘 같은 차례
    val options = remember(letter.id, k, view.read) {
        val me = letter.words.getOrNull(k)
        fun item(w: io.github.graviton94.todayletters.core.Word) =
            io.github.graviton94.todayletters.core.Distractors.Item(w.text[view.read], io.github.graviton94.todayletters.core.Distractors.Kind.WORD, w.pos, w.text[view.learn])
        val near = letter.words.filter { it !== me }.map(::item)
        val far = s.work(r.room.series).chapters.flatMap { it.letters }.filter { it.id != letter.id }.flatMap { it.words }.map(::item)
        val answer = me?.let(::item) ?: io.github.graviton94.todayletters.core.Distractors.Item(right, io.github.graviton94.todayletters.core.Distractors.Kind.WORD)
        val seed = (letter.id + k).hashCode().toLong()
        val others = io.github.graviton94.todayletters.core.Distractors.pick(answer, near, 3, seed).let { got ->
            if (got.size >= 3) got else got + io.github.graviton94.todayletters.core.Distractors.pick(answer, far, 3, seed, exclude = got).take(3 - got.size)
        }
        (others + right).shuffled(java.util.Random((letter.id + "o" + k).hashCode().toLong()))
    }
    // 그 낱말이 나온 편지 문장 (배우는 말 · 읽는 말)
    val line = remember(letter.id, k, view.learn) {
        val w = letter.words.getOrNull(k)
        val keys = listOfNotNull(w?.text?.get(view.learn)) + (w?.forms ?: emptyList())
        letter.messages.firstNotNullOfOrNull { m ->
            val t = m.text[view.learn]; val mk = wordMarks(t, keys)
            if (mk.isEmpty()) null else Triple(t, mk.first().first, m.text[view.read])
        }
    }
    fun say(i: Int, speed: Float? = null) = letter.words.getOrNull(i)?.let { s.narrator.play(s.wordAudio(r.room.series, chapter, letter, it, view.learn), speed) }
    LaunchedEffect(letter.id, k) { if (!done) say(k) }
    LaunchedEffect(over, k) { if (over) { delay(1100); at = k + 1 } }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(minOf(at, n), n, onClose = onClose, onHelp = { helpId(s.route)?.let { s.coachAgain(it) } }, count = "${minOf(at + 1, n)} / $n")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4).padding(top = Tokens.Space.s1, bottom = Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle("MATCH", stringResource(R.string.match_prompt))
            GuideLine(s, "match")
            if (done) Text(stringResource(R.string.match_done), style = Type.heading.ui(), color = p.correct, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            else {
                val w = letter.words.getOrNull(k)
                WordLabel(pairs[k].first, w?.let { io.github.graviton94.todayletters.core.Pron.line(it, view.learn, view.learn == s.work(r.room.series).learnVocab) }.orEmpty(), view.learn, "",
                    note = w?.let { io.github.graviton94.todayletters.core.Pron.note(it, view.learn, uiLang()) }.orEmpty(),
                    play = if (w == null || s.wordAudio(r.room.series, chapter, letter, w, view.learn).isEmpty()) null else ({ sp -> say(k, sp) }))
                line?.let { (t, range, read) ->
                    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                        Text(stringResource(R.string.match_in_letter), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.giltText)
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            append(t)
                            addStyle(androidx.compose.ui.text.SpanStyle(color = p.giltText, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), range.first, range.last + 1)
                        }, style = Type.target.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic).of(view.learn), color = p.ink)
                        if (over && view.read != view.learn) Text(meaningMarks(read, right, p.ink), style = Type.small.of(view.read), color = p.inkSoft)
                    }
                }
                MeaningOptions(options, right, view.read, picked, over, missed, false, shakeKey) { o ->
                    picked = o
                    if (o == right) {
                        if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        s.cue(true); say(k)
                    } else { missed = missed + o; shakeKey++; buzzWrong(view0, s.app.haptics); s.cue(false) }
                }
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            if (letter.phrases.isNotEmpty()) Primary(stringResource(R.string.phrase_next), enabled = done, shine = true) { phrases = true }
            else Primary(stringResource(R.string.play_finish), enabled = done, shine = true) { s.replyAndContinue(r.room, ReplyMode.MATCH) }
            if (!done) SkipLink { s.skipStep(r.room, ReplyMode.MATCH) }
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
    ReadAlong(s, r.room, only = listOf(i), onClose = onClose, onFinish = { s.replyAndContinue(r.room, ReplyMode.ALOUD) },
        onSkip = { s.skipStep(r.room, ReplyMode.ALOUD) }, guide = "aloud")
}

/**
 * 편지 걸음 건너뛰기 (낱말 맞추기 · 따라 읽기 · 답장): 편지는 계속 이어지고, 건너뛴 걸음은 편지 목록 · 복습 탭의 ‘다시 하기’로 언제든.
 */
@Composable
fun SkipLink(onSkip: () -> Unit) {
    val p = Ink.palette
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onSkip), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.step_skip), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.inkSoft)
    }
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
