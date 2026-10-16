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
 * 따라 읽기 (v23, 모든 시리즈 · 모든 자리 공통): 문장마다, 긴 문장은 6~10 낱말 마디로. 원래 소리(0.8배) → 내 목소리.
 * 편지 안 연습(한 문장), 오늘의 할 일 · 봉인(편지 전체) 모두 이 화면 하나를 쓴다.
 * 읽는 자리가 금빛으로 따라가고, 녹음하는 동안 파형과 내 음높이 선이 원래 낭독 선 위에 실시간으로 그려진다.
 * 채점은 억양 · 리듬 · 완성도 (core 의 Prosody, 기기 안에서): 별 1~3 (50 · 70 · 85), 55점이면 통과로 기록. 빨간 ‘실패’는 없다.
 * 편지 걸음([onFinish] 가 있을 때)에서는 녹음하면 통과. 봉인은 모든 마디 통과. 세 번 해도 안 되면 ‘넘어가기’.
 */
@Composable
fun ShadowScreen(s: AppState, r: Route.Shadow) = ReadAlong(s, r.room, only = r.only?.let { listOf(it) }, onClose = { s.back() }, onFinish = null)

@Composable
fun ReadAlong(s: AppState, room: Route.Letter, only: List<Int>?, onClose: () -> Unit, onFinish: (() -> Unit)?,
              /** 편지 걸음이면 건너뛰기 (편지 목록 · 복습 탭에서 다시 하기). */ onSkip: (() -> Unit)? = null,
              /** 튜토리얼의 안내 한 줄 (core 의 Tutorial 걸음 이름). */ guide: String? = null) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(room)
    val id = room.series
    val view = s.room(id)
    val units = remember(letter.id) { s.shadowChunks(id, chapter, letter) }
    // 마디 하나 = 한 칸 (shadowChunks 가 문장마다 6~10 낱말 마디를 준다)
    val flat = remember(units, only) { units.indices.filter { only == null || it in only }.flatMap { i -> units[i].indices.map { i to it } } }
    // 편지 걸음에서는 녹음하면 통과
    val step = onFinish != null
    fun ok(i: Int, j: Int) = s.take(id, chapter, letter.id, i, j).exists() && (step || units[i][j].start < 0 || s.passed(id, chapter, letter.id, i, j))
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
            s.recordScore(it); s.cue(it.passed || (step && it.total > 0))
            if (it.stars >= 1) s.markStar(id, chapter, letter.id)   // 봉인 조건: 따라 읽기 별 하나 이상 (F13)
            s.recordStars(id, chapter, letter.id, mi, cj, it.stars)   // 메달 진열장의 따라 읽기 별 (마디마다 가장 많이 받은 별)
            if (it.passed || (step && it.total > 0)) s.markPassed(id, chapter, letter.id, mi, cj) else if (tick > 0) tries[at] = (tries[at] ?: 0) + 1
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
    fun original(speed: Float? = null) {
        if (recording) { s.pcm.onFrame = null; s.pcm.stop(); recording = false; tick++ }
        if (!hasAudio) return
        s.recorder.stopPlaying()
        playing = s.narrator.playRange(audio, unit.start, unit.end, speed) { playing = false; pos = -1 }
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
            PracticeTitle(stringResource(R.string.shadow_title).uppercase(), null)
            val nth = if (only == null) mi + 1 else only.indexOf(mi) + 1
            val of = if (only == null) units.size else only.size
            if (guide != null) GuideLine(s, guide)
            // 한 문장 · 한 마디뿐이면 "1 / 1" 은 위의 진행 숫자와 겹친다 (F14)
            if (flat.size > 1) Text(if (units[mi].size > 1) stringResource(R.string.read_where_part, nth, of, cj + 1, units[mi].size) else stringResource(R.string.read_where, nth, of),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
            // 읽는 자리 칠하기 그대로, 낱말을 누르면 뜻 (눌러서 뜻 보기). 긴 문장은 이번 마디 밖을 흐리게.
            val part = remember(sentence, unit) { if (units[mi].size <= 1) null else sentence.indexOf(unit.text).takeIf { it >= 0 }?.let { it until it + unit.text.length } }
            val painted = readAlong(sentence, pos, p.ink, p.inkSoft.copy(alpha = 0.7f), p.gilt.copy(alpha = 0.35f)).let { a ->
                if (part == null) a else androidx.compose.ui.text.AnnotatedString.Builder(a).apply {
                    val dim = androidx.compose.ui.text.SpanStyle(color = p.inkSoft.copy(alpha = 0.35f))
                    if (part.first > 0) addStyle(dim, 0, part.first)
                    if (part.last + 1 < sentence.length) addStyle(dim, part.last + 1, sentence.length)
                }.toAnnotatedString()
            }
            GlossText(s, id, chapter, letter, mi, painted, style = Type.target.of(view.learn))
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
                    val stars = res.first.stars
                    val P = io.github.graviton94.todayletters.core.Prosody
                    // 점수 · 별 (빨간 ‘실패’ 없음): 화면 읽기는 한 덩어리로 “점수 72, 별 셋 중 둘”
                    val said = stringResource(R.string.a11y_score, total, stars)
                    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = said },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Text("$total", style = Type.display.copy(fontFamily = Faces.display), color = p.ink)
                        Text("★".repeat(stars) + "☆".repeat(3 - stars), style = Type.title, color = p.giltText)
                        Box(Modifier.weight(1f).height(6.dp).background(p.hair)) {
                            Box(Modifier.fillMaxWidth(total / 100f).height(6.dp).background(p.gilt))
                            // 별 문턱 셋
                            listOf(P.STAR1, P.STAR2, P.STAR3).forEach { t ->
                                Box(Modifier.fillMaxWidth(t / 100f).height(6.dp)) { Box(Modifier.align(Alignment.CenterEnd).size(1.dp, 10.dp).background(p.inkSoft)) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Meter(stringResource(R.string.shadow_intonation), res.first.intonation, Modifier.weight(1f), p.giltText, p.ink, p.inkSoft)
                        Meter(stringResource(R.string.shadow_rhythm), res.first.rhythm, Modifier.weight(1f), p.giltText, p.ink, p.inkSoft)
                    }
                    Text(when {
                        stars >= 3 -> stringResource(R.string.read_great)
                        stars == 2 -> stringResource(R.string.read_good)
                        res.first.passed || (step && total > 0) -> stringResource(R.string.read_pass)
                        else -> stringResource(R.string.shadow_retry, P.PASS)
                    }, style = Type.small.ui(), color = if (res.first.passed) p.giltText else p.inkSoft)
                }
                Text(stringResource(R.string.shadow_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            }
        }
        // 안내 한 줄: 스크롤 밖, 단추 바로 위 (단추에 가리지 않게)
        Text(
            stringResource(if (recording) R.string.shadow_recording else if (have) R.string.shadow_check else R.string.shadow_tap),
            style = Type.small.ui(), color = if (recording) p.giltText else p.inkSoft, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s2),
        )
        // 원래 소리 · 말하기 · 내 소리
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            // 원래 소리 (기본 듣기 빠르기) 와 그 옆 0.7× (천천히 한 번)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                RoundButton(stringResource(R.string.shadow_orig), 56.dp, if (playing && s.narrator.slowBy == null) p.giltText else Color.Transparent, p.giltText, enabled = hasAudio, onDark = p.paper) {
                    if (playing && s.narrator.slowBy == null) { s.narrator.stop(); playing = false } else original()
                }
                if (hasAudio) Box(Modifier.padding(bottom = 24.dp)) { SlowToggle({ original(io.github.graviton94.todayletters.core.Listen.SLOW) }, onDark = true, onStop = { s.narrator.stop(); playing = false }) }
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
            val stuck = (tries[at] ?: 0) >= 3 && !ok(mi, cj) && !last
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
            // 편지 걸음(한 문장)은 녹음하면 바로 ‘마치기’ (F14: ‘다음 문장’이 아니라)
            Primary(stringResource(if (last && (allTried || onFinish != null)) (if (onFinish != null) R.string.aloud_finish else R.string.shadow_finish) else R.string.shadow_next),
                enabled = have && !recording && !scoring) { advance() }
            if (onSkip != null && !have) SkipLink(onSkip)
        }
    }
    // 아직 통과 못 한 문장이 있으면: 그 문장으로 (봉인은 모든 문장 통과)
    if (unpassed > 0) Ask(
        title = stringResource(R.string.shadow_unpassed_title),
        yes = stringResource(R.string.shadow_go), no = stringResource(R.string.shadow_stay),
        onYes = { at = flat.indexOfFirst { (i, j) -> !ok(i, j) }.coerceAtLeast(0); unpassed = 0 }, onNo = { unpassed = 0 },
        body = stringResource(R.string.shadow_unpassed_body, unpassed, io.github.graviton94.todayletters.core.Prosody.PASS),
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
    var started = false
    c.forEachIndexed { k, v ->
        if (v.isNaN()) { started = false; return@forEachIndexed }   // 긴 쉼: 선을 끊는다
        val x = size.width * upTo * k / (c.size - 1); val y = size.height / 2 - v.coerceIn(-8f, 8f) / 8f * size.height / 2.2f
        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
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
    // 화면 읽기: “억양 72” 한 덩어리로
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                PlayGlyph(playing, t.ink, 16.dp)
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

