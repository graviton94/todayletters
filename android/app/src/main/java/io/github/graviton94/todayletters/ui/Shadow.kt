package io.github.graviton94.todayletters.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 따라 읽기 (v18, 모든 시리즈 · 모든 자리 공통): 편지 문장을 통째로. 원래 소리 → 내 목소리.
 * 편지 안 연습(한 문장), 오늘의 할 일 · 봉인(편지 전체) 모두 이 화면 하나를 쓴다.
 * 읽는 자리가 금빛으로 따라가고, 녹음하는 동안 파형과 내 음높이 선이 원래 낭독 선 위에 실시간으로 그려진다.
 * 채점은 억양 · 리듬 (core 의 Prosody, 기기 안에서): 60점이면 통과 (75 좋아요 · 90 훌륭해요). 봉인은 모든 문장 통과.
 * 세 번 해도 60점이 안 되면 ‘넘어가기’ (통과로는 세지 않음).
 */
@Composable
fun ShadowScreen(s: AppState, r: Route.Shadow) = ReadAlong(s, r.room, only = null, onClose = { s.back() }, onFinish = null)

@Composable
fun ReadAlong(s: AppState, room: Route.Letter, only: List<Int>?, onClose: () -> Unit, onFinish: (() -> Unit)?) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(room)
    val id = room.series
    val view = s.room(id)
    val units = remember(letter.id) { s.shadowChunks(id, chapter, letter) }
    // 문장 하나 = 한 칸 (shadowChunks 가 문장마다 하나를 준다)
    val flat = remember(units, only) { units.indices.filter { only == null || it in only }.map { it to 0 } }
    fun ok(i: Int, j: Int) = s.take(id, chapter, letter.id, i, j).exists() && (units[i][j].start < 0 || s.passed(id, chapter, letter.id, i, j))
    val first = remember(flat) { flat.indexOfFirst { (i, j) -> !ok(i, j) }.let { if (it < 0) flat.lastIndex else it } }
    var at by remember(letter.id) { mutableIntStateOf(first.coerceAtLeast(0)) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }   // 녹음이 바뀌면 화면을 다시 그린다
    var finished by remember { mutableStateOf(false) }
    var unpassed by remember { mutableStateOf(0) }
    val tries = remember(letter.id) { mutableMapOf<Int, Int>() }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    // 실시간: 프레임마다 크기(dB) · 음높이(Hz)
    val levels = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    val pitches = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    DisposableEffect(Unit) { onDispose { s.pcm.onFrame = null; s.pcm.stop(); s.recorder.release(); s.narrator.stop() } }
    // 앱을 벗어나면 녹음은 그 자리에서 끝난다 (그때까지 녹음한 문장은 남는다)
    LaunchedEffect(s.away) { if (recording) { recording = false; tick++ }; playing = false }
    if (flat.isEmpty()) return
    val (mi, cj) = flat[at.coerceIn(0, flat.lastIndex)]
    val unit = units[mi][cj]
    val audio = s.narrator.path(id, chapter, letter.id, "m${mi + 1}_${view.learn.code}")
    val hasAudio = unit.start >= 0
    val mine = s.take(id, chapter, letter.id, mi, cj)
    tick
    val have = mine.exists()
    val sentence = io.github.graviton94.todayletters.core.Breaks.plain(letter.messages[mi].text[view.learn])

    // 원래 낭독의 음높이 선 (문장이 바뀔 때 한 번)
    var refCurve by remember(at) { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(at) {
        if (!hasAudio) return@LaunchedEffect
        refCurve = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val ref = io.github.graviton94.todayletters.data.decodeRange(ctx, audio, unit.start, unit.end) ?: return@withContext null
            val P = io.github.graviton94.todayletters.core.Prosody
            P.semitones(P.trim(P.analyze(ref, io.github.graviton94.todayletters.data.Wav.RATE)).f0)
        }
    }
    // 억양 · 리듬: 원래 낭독과 내 녹음을 기기 안에서 비교 (녹음이 바뀔 때마다)
    var result by remember(at, tick) { mutableStateOf<Triple<io.github.graviton94.todayletters.core.Prosody.Score, FloatArray?, FloatArray?>?>(null) }
    var scoring by remember(at, tick) { mutableStateOf(false) }
    LaunchedEffect(at, tick) {
        if (!have || !hasAudio) return@LaunchedEffect
        scoring = true
        result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val ref = io.github.graviton94.todayletters.data.decodeRange(ctx, audio, unit.start, unit.end) ?: return@withContext null
            val me = io.github.graviton94.todayletters.data.Wav.samples(io.github.graviton94.todayletters.data.Wav.pcm(mine))
            val P = io.github.graviton94.todayletters.core.Prosody
            val rt = P.analyze(ref, io.github.graviton94.todayletters.data.Wav.RATE); val mt = P.analyze(me, io.github.graviton94.todayletters.data.Wav.RATE)
            Triple(P.score(rt, mt), P.semitones(P.trim(rt).f0), P.semitones(P.trim(mt).f0))
        }
        scoring = false
        result?.first?.let {
            s.recordScore(it)
            if (it.passed) s.markPassed(id, chapter, letter.id, mi, cj) else if (tick > 0) tries[mi] = (tries[mi] ?: 0) + 1
        }
    }

    // 읽는 자리: 원래 낭독의 마디 시각으로 글자 자리를 셈
    val marks = remember(audio) { s.narrator.chunks(audio) }
    val spans = remember(sentence, marks) {
        var from = 0
        marks.map { c ->
            val k = sentence.indexOf(c.text, from).takeIf { it >= 0 } ?: from
            from = k + c.text.length
            Triple(c, k, k + c.text.length)
        }
    }
    var pos by remember(at) { mutableIntStateOf(-1) }
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
    fun original() {
        if (recording) { s.pcm.onFrame = null; s.pcm.stop(); recording = false; tick++ }
        if (!hasAudio) return
        s.recorder.stopPlaying()
        playing = s.narrator.play(audio) { playing = false; pos = -1 }
    }
    fun record() {
        if (!allowed) { ask.launch(Manifest.permission.RECORD_AUDIO); return }
        if (recording) { s.pcm.onFrame = null; s.pcm.stop(); recording = false; tick++; return }
        s.narrator.stop(); s.recorder.stopPlaying(); playing = false
        levels.clear(); pitches.clear()
        s.pcm.onFrame = { db, hz -> levels.add(db); pitches.add(hz); if (levels.size > 1500) { levels.removeAt(0); pitches.removeAt(0) } }
        recording = s.pcm.start(mine)
    }
    // 새 문장에 오면 원래 소리를 한 번 들려준다
    LaunchedEffect(at) { if (!have) original() }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(at, flat.size, onClose = onClose, count = "${flat.count { (i, j) -> ok(i, j) }} / ${flat.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle("READ ALONG", null)
            Text(stringResource(R.string.read_where, flat.size.let { if (only == null) mi + 1 else at + 1 }, if (only == null) units.size else flat.size),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(readAlong(sentence, pos, p.ink, p.inkSoft.copy(alpha = 0.7f), p.gilt.copy(alpha = 0.35f)), style = Type.target.of(view.learn))
            if (view.showRead) Text(letter.messages[mi].text[view.read], style = Type.base.of(view.read), color = p.inkSoft)
            if (!hasAudio) Text(stringResource(R.string.shadow_noaudio), style = Type.small.ui(), color = p.inkSoft)
            // 판: 녹음 중이면 실시간 파형 + 내 음높이, 녹음 뒤에는 두 선과 점수
            val res = result
            if (recording || (have && hasAudio)) Column(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("┅ " + stringResource(R.string.shadow_orig), style = Type.small.ui(), color = p.inkSoft)
                    Text("━ " + stringResource(R.string.shadow_mine), style = Type.small.ui(), color = p.giltText)
                }
                if (recording) LiveTrace(levels, pitches, refCurve)
                else if (res == null) Text(stringResource(if (scoring) R.string.shadow_scoring else R.string.shadow_noscore), style = Type.small.ui(), color = p.inkSoft)
                else {
                    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                        drawCurve(res.second, p.inkSoft, dotted = true); drawCurve(res.third, p.giltText, dotted = false)
                    }
                    val total = res.first.total
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Text("$total", style = Type.display.copy(fontFamily = Faces.display), color = if (res.first.passed) p.correct else p.wrong)
                        Box(Modifier.weight(1f).height(6.dp).background(p.hair)) {
                            Box(Modifier.fillMaxWidth(total / 100f).height(6.dp).background(if (res.first.passed) p.correct else p.wrong))
                            Box(Modifier.fillMaxWidth(io.github.graviton94.todayletters.core.Prosody.PASS / 100f).height(6.dp)) {
                                Box(Modifier.align(Alignment.CenterEnd).size(2.dp, 14.dp).background(p.ink))
                            }
                        }
                        Text(stringResource(R.string.read_pass_line, io.github.graviton94.todayletters.core.Prosody.PASS), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Meter(stringResource(R.string.shadow_intonation), res.first.intonation, Modifier.weight(1f), p.giltText, p.ink, p.inkSoft)
                        Meter(stringResource(R.string.shadow_rhythm), res.first.rhythm, Modifier.weight(1f), p.giltText, p.ink, p.inkSoft)
                    }
                    val P = io.github.graviton94.todayletters.core.Prosody
                    Text(stringResource(when { total >= P.GREAT -> R.string.read_great; total >= P.GOOD -> R.string.read_good; res.first.passed -> R.string.read_pass; else -> R.string.shadow_retry }),
                        style = Type.small.ui(), color = if (res.first.passed) p.correct else p.wrong)
                }
                Text(stringResource(R.string.shadow_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            }
            Text(
                stringResource(if (recording) R.string.shadow_recording else if (have) R.string.shadow_check else R.string.shadow_tap),
                style = Type.small.ui(), color = if (recording) p.wrong else p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        // 원래 소리 · 말하기 · 내 소리
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            RoundButton(stringResource(R.string.shadow_orig), 56.dp, if (playing) p.giltText else Color.Transparent, p.giltText, enabled = hasAudio, onDark = p.paper) {
                if (playing) { s.narrator.stop(); playing = false } else original()
            }
            val recStart = stringResource(R.string.a11y_record); val recStop = stringResource(R.string.a11y_record_stop)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(80.dp).background(if (recording) p.wrong else p.wrong.copy(alpha = 0.12f), CircleShape).border(1.dp, p.wrong, CircleShape)
                        .semantics { contentDescription = if (recording) recStop else recStart }
                        .pressable(role = Role.Button) { record() },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) { if (recording) drawRect(p.paper) else drawCircle(p.wrong, size.minDimension / 2) }
                }
                Text(stringResource(if (recording) R.string.shadow_stop else if (have) R.string.shadow_again else R.string.shadow_talk), style = Type.small.ui(), color = p.ink)
            }
            RoundButton(stringResource(R.string.shadow_mine), 56.dp, Color.Transparent, if (have) p.giltText else p.hair, enabled = have && !recording, onDark = p.paper) {
                s.narrator.stop(); playing = false; s.recorder.play(mine)
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            val last = at == flat.lastIndex
            val allTried = flat.all { (i, j) -> s.take(id, chapter, letter.id, i, j).exists() }
            val stuck = (tries[mi] ?: 0) >= 3 && !ok(mi, cj) && !last
            fun advance() {
                s.narrator.stop(); s.recorder.stopPlaying(); playing = false
                when {
                    !last -> at++
                    !allTried -> at = flat.indexOfFirst { (i, j) -> !s.take(id, chapter, letter.id, i, j).exists() }
                    onFinish != null -> onFinish()
                    else -> {
                        unpassed = flat.count { (i, j) -> !ok(i, j) }
                        if (unpassed == 0) {
                            finished = true
                            if (s.shadowDone(id, chapter, letter)) { s.back(); s.go(Route.Seal(room)) }
                        }
                    }
                }
            }
            if (stuck) Secondary(stringResource(R.string.read_skip), small = true) { advance() }
            Primary(stringResource(if (last && allTried) (if (onFinish != null) R.string.aloud_finish else R.string.shadow_finish) else R.string.shadow_next),
                enabled = have && !recording && !scoring) { advance() }
        }
    }
    // 아직 통과 못 한 문장이 있으면: 그 문장으로 (봉인은 모든 문장 통과)
    if (unpassed > 0) Ask(
        title = stringResource(R.string.shadow_unpassed_title),
        yes = stringResource(R.string.shadow_go), no = stringResource(R.string.shadow_stay),
        onYes = { at = flat.indexOfFirst { (i, j) -> !ok(i, j) }.coerceAtLeast(0); unpassed = 0 }, onNo = { unpassed = 0 },
        body = stringResource(R.string.shadow_unpassed_body, unpassed),
    )
    // 다 따라 읽었지만 아직 혼자 다 읽지 못하는 편지: 봉인까지 남은 길
    if (finished && !s.isSealed(id, chapter, letter)) {
        val g = s.letterGrowth(id, chapter, letter)
        Ask(
            title = stringResource(R.string.shadow_done_title),
            yes = stringResource(R.string.shadow_back), no = stringResource(R.string.shadow_stay),
            onYes = { finished = false; s.back() }, onNo = { finished = false },
            body = stringResource(R.string.shadow_done_body, g.total, g.known),
        )
    }
}

/** 반음 곡선 하나 (가운데 0, 위아래 ±8 반음). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurve(c: FloatArray?, color: Color, dotted: Boolean, upTo: Float = 1f) {
    if (c == null || c.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    c.forEachIndexed { k, v ->
        val x = size.width * upTo * k / (c.size - 1); val y = size.height / 2 - v.coerceIn(-8f, 8f) / 8f * size.height / 2.2f
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(if (dotted) 1.5.dp.toPx() else 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
        pathEffect = if (dotted) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 8f)) else null))
}

/**
 * 녹음 중 실시간 판: 크기 막대(파형)와 내 음높이 선. 원래 낭독의 선은 점선으로 뒤에 깔린다.
 * 가로는 원래 낭독 길이에 맞춰 늘고 (넘치면 왼쪽으로 밀림), 내 선은 반음 (내 첫 목소리 기준).
 */
@Composable
private fun LiveTrace(levels: List<Float>, pitches: List<Float>, ref: FloatArray?) {
    val p = Ink.palette
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val n = levels.size
        val span = maxOf(ref?.size?.div(3) ?: 0, n, 60)   // 녹음 프레임은 원래 분석 프레임(10ms)의 약 3배 길이
        drawCurve(ref, p.inkSoft, dotted = true)
        val bw = size.width / span
        levels.forEachIndexed { k, db ->
            val h = ((db + 60f) / 60f).coerceIn(0.02f, 1f) * size.height * 0.9f
            val x = k * bw
            drawRect(p.hair, androidx.compose.ui.geometry.Offset(x, size.height / 2 - h / 2), androidx.compose.ui.geometry.Size((bw * 0.6f).coerceAtLeast(1f), h))
        }
        val voiced = pitches.filter { it > 0f }
        if (voiced.size >= 3) {
            val med = voiced.sorted()[voiced.size / 2]
            val path = androidx.compose.ui.graphics.Path(); var started = false
            pitches.forEachIndexed { k, hz ->
                if (hz <= 0f) { started = false; return@forEachIndexed }
                val st = (12 * kotlin.math.ln(hz / med) / kotlin.math.ln(2f)).coerceIn(-8f, 8f)
                val x = k * bw; val y = size.height / 2 - st / 8f * size.height / 2.2f
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, p.giltText, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            pitches.indexOfLast { it > 0f }.takeIf { it >= 0 }?.let { k ->
                val hz = pitches[k]; val st = (12 * kotlin.math.ln(hz / med) / kotlin.math.ln(2f)).coerceIn(-8f, 8f)
                drawCircle(p.giltText, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(k * bw, size.height / 2 - st / 8f * size.height / 2.2f))
            }
        }
    }
}

@Composable
private fun Meter(label: String, value: Int, modifier: Modifier, gold: Color, text: Color, soft: Color) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Type.small.ui(), color = soft)
        Text("$value", style = Type.title.copy(fontFamily = Faces.display), color = text)
        Box(Modifier.fillMaxWidth().height(4.dp).background(Ink.palette.hair)) { Box(Modifier.fillMaxWidth(value / 100f).height(4.dp).background(gold)) }
    }
}

@Composable
private fun RoundButton(label: String, diameter: androidx.compose.ui.unit.Dp, fill: Color, ring: Color, enabled: Boolean, onDark: Color = Color(0xFF17110C), onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(diameter).background(fill, CircleShape).border(1.dp, ring, CircleShape).pressable(enabled = enabled, role = Role.Button) { onClick() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val c = if (fill == Color.Transparent) ring else onDark
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.25f, size.height * 0.15f); lineTo(size.width * 0.85f, size.height / 2); lineTo(size.width * 0.25f, size.height * 0.85f); close() }
                drawPath(path, c)
            }
        }
        Text(label, style = Type.small.ui(), color = Ink.palette.inkSoft)
    }
}

/**
 * 완독 봉인 (모든 시리즈 공통): 혼자 다 읽고 다 따라 읽은 편지. 그 편지의 자료가 크게 (자료가 나오는 세 순간 가운데 하나),
 * 날짜 줄, 내 낭독 듣기, 이 편지에서 익힌 낱말, 내 낭독 보내기.
 */
@Composable
fun SealScreen(s: AppState, r: Route.Seal) {
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val work = s.work(id)
    val t = darkTone(work)
    val view = s.room(id)
    val letters = work.chapters.flatMap { it.letters }
    val idx = letters.indexOf(letter) + 1
    val reading = remember(letter.id) { s.myReading(id, chapter, letter) }
    val chunks = remember(letter.id) { s.shadowChunks(id, chapter, letter).sumOf { it.size } }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(letter.id) { s.sealSeen(id, chapter, letter) }
    DisposableEffect(Unit) { onDispose { s.recorder.stopPlaying() } }

    Column(Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(R.string.seal_caps, roman(idx)).uppercase(), style = Type.caps, color = t.accent, modifier = Modifier.padding(top = Tokens.Space.s4))
        Text(stringResource(R.string.seal_title), style = Type.title.ui(), color = t.ink, textAlign = TextAlign.Center)
        Text(dateLine(letter.date, letter.place), style = Type.small.ui(), color = t.soft)
        letter.plate?.let { pl -> AssetImage("plates/${pl.image}", Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2).fillMaxWidth().height(220.dp), sample = 2) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Box(Modifier.weight(1f).height(1.dp).background(t.ink.copy(alpha = 0.14f)))
            Text(stringResource(R.string.seal_line, java.time.LocalDate.now().let { "${it.year}. ${it.monthValue}. ${it.dayOfMonth}" }), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = t.soft)
            Box(Modifier.weight(1f).height(1.dp).background(t.ink.copy(alpha = 0.14f)))
        }
        // 내 낭독
        if (reading != null) Row(
            Modifier.fillMaxWidth().pressable {
                if (playing) { s.recorder.stopPlaying(); playing = false } else { playing = true; s.recorder.play(reading) { playing = false } }
            }.padding(vertical = Tokens.Space.s2),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Box(Modifier.size(46.dp).border(1.5.dp, t.ink, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (playing) "■" else "▶", style = Type.body, color = t.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.seal_mine, roman(idx)), style = Type.body.ui(), color = t.ink)
                Text(stringResource(R.string.seal_mine_d, chunks), style = Type.small.ui(), color = t.soft)
            }
        }
        // 이 편지에서 익힌 낱말
        if (letter.words.isNotEmpty()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.seal_words), style = Type.small.ui(), color = t.soft)
            Text(letter.words.joinToString(" · ") { it.text[view.learn] }, style = Type.body.of(view.learn), color = t.ink)
        }
        Box(Modifier.height(Tokens.Space.s3))
        val upcoming = letters.getOrNull(idx)
        upcoming?.let { u ->
            val d = s.daysUntil(id, u.id)
            Text(
                when { d <= 0 -> stringResource(R.string.next_arrived); d == 1 -> stringResource(R.string.letter_eta); else -> stringResource(R.string.next_in, d) } + " · " + stringResource(R.string.next_title),
                style = Type.small.ui(), color = t.soft,
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (reading != null) Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(t.ink).pressable {
                val share = stringResource0(ctx, R.string.seal_share_text, letter.messages.first().text[view.learn].replace("\u2060", ""), work.fullName)
                runCatching {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", reading)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, share)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.seal_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.seal_send), style = Type.body.ui(), color = t.paper) }
            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).pressable { s.back() }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.close), style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = t.ink)
            }
        }
    }
}

private fun stringResource0(ctx: android.content.Context, id: Int, vararg args: Any) = ctx.getString(id, *args)

