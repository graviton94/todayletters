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
 * 편지 걸음은 오늘의 회화 내 줄 (“me”, 장이 갈수록 길어지는 말하기 계단) 을 먼저 따라 말하고 (원래 소리 = 회화 소리 t<줄>_<말>), 그다음 편지 문장
 * (core [io.github.graviton94.todayletters.core.ShadowLines]). 봉인까지 가는 전체 따라 읽기는 편지 문장만.
 */
@Composable
fun ShadowScreen(s: AppState, r: Route.Shadow) = ReadAlong(s, r.room, only = r.only?.let { listOf(it) }, onClose = { s.back() }, onFinish = null)

@Composable
fun ReadAlong(s: AppState, room: Route.Letter, only: List<Int>?, onClose: () -> Unit, onFinish: (() -> Unit)?,
              /** 편지 걸음이면 건너뛰기 (편지 목록 · 복습 탭에서 다시 하기). */ onSkip: (() -> Unit)? = null,
              /** 튜토리얼의 안내 한 줄 (core 의 Tutorial 걸음 이름). */ guide: String? = null) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(room)
    val id = room.series
    val view = s.room(id)
    val units = remember(letter.id) { s.shadowChunks(id, chapter, letter) }
    // 편지 걸음에서는 목소리가 들어간 녹음이면 통과 (침묵 · 잡음만이면 아니다)
    val step = onFinish != null
    // 마디 하나 = 한 칸 (shadowChunks 가 문장마다 6~10 낱말 마디를 준다). 편지 걸음이면 회화 내 줄이 앞에 한 칸씩
    val flat = remember(units, only, view.learn) {
        io.github.graviton94.todayletters.core.ShadowLines.order(letter.talk, view.learn, units.map { it.size }, only, withTalk = step)
    }
    // 회화 줄의 원래 소리 (편지 폴더 t<줄>_<말>, 기기에 없으면 소리 없이 녹음만)
    fun talkAudio(line: Int): String? = letter.talk?.audioName(line, view.learn)?.let { s.narrator.path(id, chapter, letter.id, it) }?.takeIf { s.narrator.has(it) }
    fun unitOf(sp: io.github.graviton94.todayletters.core.ShadowSpot): io.github.graviton94.todayletters.data.Chunk =
        if (!sp.isTalk) units[sp.msg][sp.chunk]
        else io.github.graviton94.todayletters.data.Chunk(if (talkAudio(sp.talk) != null) 0 else -1, Int.MAX_VALUE,
            io.github.graviton94.todayletters.core.Breaks.plain(letter.talk?.lines?.getOrNull(sp.talk)?.text?.get(view.learn).orEmpty()))
    fun fileOf(sp: io.github.graviton94.todayletters.core.ShadowSpot) =
        if (sp.isTalk) s.talkShadowTake(id, chapter, letter.id, sp.talk) else s.take(id, chapter, letter.id, sp.msg, sp.chunk)
    fun ok(sp: io.github.graviton94.todayletters.core.ShadowSpot) = fileOf(sp).exists() && (step || unitOf(sp).start < 0 || s.passed(id, chapter, letter.id, sp.tag))
    val first = remember(flat) { flat.indexOfFirst { !ok(it) }.let { if (it < 0) flat.lastIndex else it } }
    var at by remember(letter.id) { mutableIntStateOf(first.coerceAtLeast(0)) }
    var finished by remember { mutableStateOf(false) }
    var unpassed by remember { mutableStateOf(0) }
    val tries = remember(letter.id) { mutableMapOf<Int, Int>() }
    DisposableEffect(Unit) { onDispose { s.pcm.onFrame = null; s.pcm.stop(); s.recorder.release(); s.narrator.stop() } }
    if (flat.isEmpty()) return
    val spot = flat[at.coerceIn(0, flat.lastIndex)]
    val mi = spot.msg
    val cj = spot.chunk
    val talkLine = if (spot.isTalk) letter.talk?.lines?.getOrNull(spot.talk) else null
    val unit = unitOf(spot)
    val audio = if (spot.isTalk) talkAudio(spot.talk).orEmpty() else s.narrator.path(id, chapter, letter.id, "m${mi + 1}_${view.learn.code}")
    val sentence = if (spot.isTalk) unit.text else io.github.graviton94.todayletters.core.Breaks.plain(letter.messages[mi].text[view.learn])
    // 녹음 · 채점 (회화 내 차례와 같은 부품): 원래 낭독의 이 마디와 내 녹음을 기기 안에서 비교
    val take = rememberShadowTake(s, audio.takeIf { unit.start >= 0 }, unit.start, unit.end, fileOf(spot), step, key = letter.id to at,
        onVoice = { v -> if (step && v) s.markPassed(id, chapter, letter.id, spot.tag) }) { sc, fresh ->
        if (sc.stars >= 1) s.markStar(id, chapter, letter.id)   // 봉인 조건: 따라 읽기 별 하나 이상 (F13)
        s.recordStars(id, chapter, letter.id, spot.tag, sc.stars)   // 메달 진열장의 따라 읽기 별 (마디마다 가장 많이 받은 별)
        if (sc.passed) s.markPassed(id, chapter, letter.id, spot.tag) else if (fresh && !step) tries[at] = (tries[at] ?: 0) + 1
    }
    val have = take.have

    // 읽는 자리: 원래 낭독의 마디 시각으로 글자 자리를 셈
    val marks = remember(audio) { if (audio.isEmpty()) emptyList() else s.narrator.chunks(audio) }
    val spans = remember(sentence, marks) {
        var from = 0
        marks.map { c ->
            val k = sentence.indexOf(c.text, from).takeIf { it >= 0 } ?: from
            from = k + c.text.length
            Triple(c, k, k + c.text.length)
        }
    }
    var pos by remember(at) { mutableIntStateOf(-1) }
    LaunchedEffect(take.playing) {
        while (take.playing) {
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
        pos = -1
    }
    // 새 문장에 오면 원래 소리를 한 번 들려준다
    LaunchedEffect(take) { if (!take.have) take.original(s) }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.35f)) {
        PracticeHeader(at, flat.size, onClose = onClose, count = "${flat.count { ok(it) }} / ${flat.size}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            PracticeTitle(stringResource(R.string.shadow_title).uppercase(), null)
            val nth = if (only == null) mi + 1 else only.indexOf(mi) + 1
            val of = if (only == null) units.size else only.size
            if (guide != null) GuideLine(s, guide)
            // 한 문장 · 한 마디뿐이면 "1 / 1" 은 위의 진행 숫자와 겹친다 (F14). 회화 줄은 ‘오늘 말해 볼 말 1 / 2’
            val talks = flat.count { it.isTalk }
            if (spot.isTalk) Text(stringResource(R.string.shadow_talk_line, flat.indexOf(spot) + 1, talks),
                style = Type.small.ui(), color = p.giltText, modifier = Modifier.align(Alignment.CenterHorizontally))
            else if (flat.size - talks > 1) Text(if (units[mi].size > 1) stringResource(R.string.read_where_part, nth, of, cj + 1, units[mi].size) else stringResource(R.string.read_where, nth, of),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
            // 읽는 자리 칠하기 그대로, 낱말을 누르면 뜻 (눌러서 뜻 보기). 긴 문장은 이번 마디 밖을 흐리게.
            val part = remember(sentence, unit) { if (spot.isTalk || units[mi].size <= 1) null else sentence.indexOf(unit.text).takeIf { it >= 0 }?.let { it until it + unit.text.length } }
            val painted = readAlong(sentence, pos, p.ink, p.inkSoft.copy(alpha = 0.7f), p.gilt.copy(alpha = 0.35f)).let { a ->
                if (part == null) a else androidx.compose.ui.text.AnnotatedString.Builder(a).apply {
                    val dim = androidx.compose.ui.text.SpanStyle(color = p.inkSoft.copy(alpha = 0.35f))
                    if (part.first > 0) addStyle(dim, 0, part.first)
                    if (part.last + 1 < sentence.length) addStyle(dim, part.last + 1, sentence.length)
                }.toAnnotatedString()
            }
            // 회화 줄은 편지 글이 아니라서 눌러서 뜻 보기 없이
            if (talkLine != null) Text(painted, style = Type.target.of(view.learn), color = p.ink)
            else GlossText(s, id, chapter, letter, mi, painted, style = Type.target.of(view.learn))
            if (view.showRead) Text((talkLine?.text ?: letter.messages[mi].text)[view.read], style = Type.base.of(view.read), color = p.inkSoft)
            if (!take.hasAudio) Text(stringResource(R.string.shadow_noaudio), style = Type.small.ui(), color = p.inkSoft)
            ShadowBoard(take)
        }
        ShadowControls(s, take)
        Column(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            val last = at == flat.lastIndex
            val allTried = flat.all { fileOf(it).exists() }
            val stuck = (tries[at] ?: 0) >= 3 && !ok(spot) && !last
            fun advance() {
                s.narrator.stop(); s.recorder.stopPlaying(); take.playing = false
                when {
                    !last -> at++
                    !allTried -> at = flat.indexOfFirst { !fileOf(it).exists() }
                    onFinish != null -> onFinish()
                    else -> {
                        unpassed = flat.count { !ok(it) }
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
                enabled = have && !take.recording && !take.scoring && (!step || take.voice == true), shine = true) { advance() }
            if (onSkip != null && !have) SkipLink(onSkip)
        }
    }
    // 아직 통과 못 한 문장이 있으면: 그 문장으로 (봉인은 모든 문장 통과)
    if (unpassed > 0) Ask(
        title = stringResource(R.string.shadow_unpassed_title),
        yes = stringResource(R.string.shadow_go), no = stringResource(R.string.shadow_stay),
        onYes = { at = flat.indexOfFirst { !ok(it) }.coerceAtLeast(0); unpassed = 0 }, onNo = { unpassed = 0 },
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

/**
 * 따라 말하기 한 칸의 녹음 · 채점 상태: 따라 읽기 (편지 · 오늘 · 봉인) 와 회화 내 차례가 같은 것을 쓴다.
 * 원래 소리 ([audio] 의 [start]~[end] 밀리초, 없으면 null) 를 듣고, 마이크로 녹음해 ([file], 기기 안 PCM),
 * 억양 · 리듬 · 완성도를 기기 안에서 잰다 (core 의 Prosody). 폰의 음성 인식 서비스는 쓰지 않는다 (그래서 인식이 없는 폰에서도 같다).
 * [step] (편지 걸음 · 회화 · 답장 말해 보기 · 복습 말하기) 이면 목소리가 들어간 녹음은 통과 ([voice], core 의 Prosody.spoke):
 * 무슨 말인지는 알아듣지 않는다 (스스로 배우는 앱이라 믿고 맡김). 침묵 · 잡음만인 녹음은 통과가 아니다.
 */
@androidx.compose.runtime.Stable
class ShadowTake(val audio: String?, val start: Int, val end: Int, val file: java.io.File, val step: Boolean) {
    var recording by mutableStateOf(false)
    var playing by mutableStateOf(false)
    /** 녹음이 바뀔 때마다 하나씩 (다시 그리기 · 다시 채점). */
    var tick by mutableIntStateOf(0)
    var result by mutableStateOf<Triple<io.github.graviton94.todayletters.core.Prosody.Score, FloatArray?, FloatArray?>?>(null)
    var scoring by mutableStateOf(false)
    var refCurve by mutableStateOf<FloatArray?>(null)
    var allowed by mutableStateOf(false)
    /** 마지막 녹음에 목소리가 들어갔는가 (아직 재지 않았으면 null). 원래 소리가 없어도 잰다. */
    var voice by mutableStateOf<Boolean?>(null)
    internal var askMic: () -> Unit = {}
    val levels = androidx.compose.runtime.mutableStateListOf<Float>()
    val pitches = androidx.compose.runtime.mutableStateListOf<Float>()
    val hasAudio: Boolean get() = audio != null && start >= 0
    val have: Boolean get() { tick; return file.exists() }
    /** 마지막 채점이 통과인가 (아직 없으면 null). */
    val passed: Boolean? get() = result?.first?.let { it.passed || (step && voice == true) } ?: if (step && !hasAudio) voice else null

    fun stopRecording(s: AppState) { if (recording) { s.pcm.onFrame = null; s.pcm.stop(); recording = false; tick++ } }

    /** 원래 소리 (기본 빠르기, [speed] 를 주면 그 빠르기로 한 번). */
    fun original(s: AppState, speed: Float? = null) {
        stopRecording(s)
        val a = audio ?: return
        if (!hasAudio) return
        s.recorder.stopPlaying()
        playing = if (end == Int.MAX_VALUE) s.narrator.play(a, speed) { playing = false }
            else s.narrator.playRange(a, start, end, speed) { playing = false }
    }

    /** 녹음 시작 · 끝 (마이크 권한이 없으면 먼저 묻는다). */
    fun record(s: AppState) {
        if (!allowed) { askMic(); return }
        if (recording) { stopRecording(s); return }
        s.narrator.stop(); s.recorder.stopPlaying(); playing = false
        levels.clear(); pitches.clear()
        s.pcm.onFrame = { db, hz -> levels.add(db); pitches.add(hz); if (levels.size > 1500) { levels.removeAt(0); pitches.removeAt(0) } }
        file.parentFile?.mkdirs()
        recording = s.pcm.start(file)
    }
}

/**
 * [ShadowTake] 하나 ([key] 가 바뀌면 새로). 녹음이 끝나 채점되면 [onScored] (점수, 이 화면에서 새로 녹음했는가).
 * 이 화면에서 새로 녹음할 때마다 (원래 소리가 있든 없든) [onVoice] (목소리가 들어갔는가).
 */
@Composable
fun rememberShadowTake(s: AppState, audio: String?, start: Int, end: Int, file: java.io.File, step: Boolean, key: Any,
                       onVoice: (Boolean) -> Unit = {},
                       onScored: (io.github.graviton94.todayletters.core.Prosody.Score, Boolean) -> Unit = { _, _ -> }): ShadowTake {
    val ctx = LocalContext.current
    val t = remember(key) { ShadowTake(audio, start, end, file, step).also { it.allowed = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED } }
    val scored by androidx.compose.runtime.rememberUpdatedState(onScored)
    val voiced by androidx.compose.runtime.rememberUpdatedState(onVoice)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { t.allowed = it }
    t.askMic = { ask.launch(Manifest.permission.RECORD_AUDIO) }
    // 앱을 벗어나면 녹음은 그 자리에서 끝난다 (그때까지 녹음한 것은 남는다)
    LaunchedEffect(t, s.away) { t.stopRecording(s); t.playing = false }
    // 원래 소리의 음높이 선 (칸이 바뀔 때 한 번)
    LaunchedEffect(t) {
        if (!t.hasAudio) return@LaunchedEffect
        t.refCurve = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val ref = io.github.graviton94.todayletters.data.decodeRange(ctx, t.audio!!, t.start, t.end) ?: return@withContext null
            val P = io.github.graviton94.todayletters.core.Prosody
            P.semitones(P.trim(P.analyze(ref, io.github.graviton94.todayletters.data.Wav.RATE)).f0)
        }
    }
    // 억양 · 리듬: 원래 소리와 내 녹음을 기기 안에서 비교 (녹음이 바뀔 때마다)
    // 목소리가 들어갔는가는 원래 소리가 없어도 잰다 (침묵 · 잡음만인 녹음은 통과가 아니다)
    LaunchedEffect(t, t.tick) {
        t.result = null; t.voice = null
        if (!t.have || t.recording) return@LaunchedEffect
        t.scoring = true
        val P = io.github.graviton94.todayletters.core.Prosody
        val rate = io.github.graviton94.todayletters.data.Wav.RATE
        val mt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { P.analyze(io.github.graviton94.todayletters.data.Wav.samples(io.github.graviton94.todayletters.data.Wav.pcm(t.file)), rate) }.getOrNull()
        }
        t.voice = mt != null && P.spoke(mt)
        if (mt != null && t.hasAudio) t.result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val ref = io.github.graviton94.todayletters.data.decodeRange(ctx, t.audio!!, t.start, t.end) ?: return@withContext null
            val rt = P.analyze(ref, rate)
            Triple(P.score(rt, mt), P.semitones(P.trim(rt).f0), P.semitones(P.trim(mt).f0))
        }
        t.scoring = false
        val fresh = t.tick > 0
        t.result?.first?.let {
            s.recordScore(it); s.cue(it.passed || (t.step && t.voice == true))
            scored(it, fresh)
        } ?: run { if (fresh && !t.hasAudio) s.cue(t.voice == true) }
        if (fresh) voiced(t.voice == true)
    }
    return t
}

/** 판: 녹음 중이면 실시간 파형 + 내 음높이, 녹음 뒤에는 두 선과 점수 · 별 · 억양 · 리듬. */
@Composable
fun ShadowBoard(t: ShadowTake) {
    val p = Ink.palette
    val res = t.result
    if (t.recording || (t.have && t.hasAudio)) Column(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("┅ " + stringResource(R.string.shadow_orig), style = Type.small.ui(), color = p.inkSoft)
            Text("━ " + stringResource(R.string.shadow_mine), style = Type.small.ui(), color = p.giltText)
        }
        if (t.recording) LiveTrace(t.levels, t.pitches, t.refCurve)
        else if (res == null) Text(stringResource(if (t.scoring) R.string.shadow_scoring else R.string.shadow_noscore), style = Type.small.ui(), color = p.inkSoft)
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
                    listOf(P.STAR1, P.STAR2, P.STAR3).forEach { th ->
                        Box(Modifier.fillMaxWidth(th / 100f).height(6.dp)) { Box(Modifier.align(Alignment.CenterEnd).size(1.dp, 10.dp).background(p.inkSoft)) }
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
                t.passed == true -> stringResource(R.string.read_pass)
                else -> stringResource(R.string.shadow_retry, P.PASS)
            }, style = Type.small.ui(), color = if (res.first.passed) p.giltText else p.inkSoft)
        }
        Text(stringResource(R.string.shadow_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
    }
}

/**
 * 안내 한 줄 + 원래 소리 (와 0.7×) · 말하기 (녹음) · 내 소리. [canListen] 이 아니면 원래 소리를 막고 (빈칸 · 조각을 다 맞추기 전),
 * [canRecord] 가 아니면 녹음을 막는다.
 */
@Composable
fun ShadowControls(s: AppState, t: ShadowTake, canListen: Boolean = true, canRecord: Boolean = true,
                   /** 녹음 전 안내 한 줄 (기본: 이 문장을 따라 읽어 보세요). */ tip: Int = R.string.shadow_tap) {
    val p = Ink.palette
    // 안내 한 줄: 스크롤 밖, 단추 바로 위 (단추에 가리지 않게). 목소리가 들리지 않은 녹음이면 ‘다시 해 봐요’
    Text(
        stringResource(if (t.recording) R.string.shadow_recording else if (t.voice == false && t.tick > 0) R.string.talk_retry else if (t.have) R.string.shadow_check else tip),
        style = Type.small.ui(), color = if (t.recording) p.giltText else p.inkSoft, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s2),
    )
    Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        // 원래 소리 (기본 듣기 빠르기) 와 그 옆 0.7× (천천히 한 번)
        // 원래 소리가 없으면 단추를 두지 않는다 (소리 없는 단추 없음). 답을 아직 못 봐서 못 듣는 때만 흐린 단추
        val listen = t.hasAudio && canListen
        if (t.hasAudio) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            RoundButton(stringResource(R.string.shadow_orig), 56.dp, if (t.playing && s.narrator.slowBy == null) p.giltText else Color.Transparent, if (listen) p.giltText else p.hair, enabled = listen, onDark = p.paper) {
                if (t.playing && s.narrator.slowBy == null) { s.narrator.stop(); t.playing = false } else t.original(s)
            }
            if (listen) Box(Modifier.padding(bottom = 24.dp)) { SlowToggle({ t.original(s, io.github.graviton94.todayletters.core.Listen.SLOW) }, onDark = true, onStop = { s.narrator.stop(); t.playing = false }) }
        }
        val recStart = stringResource(R.string.a11y_record); val recStop = stringResource(R.string.a11y_record_stop)
        val rec = canRecord || t.recording
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(80.dp).background(if (t.recording) p.wrong else if (rec) p.wrong.copy(alpha = 0.12f) else p.hide, CircleShape).border(1.dp, if (rec) p.wrong else p.hair, CircleShape)
                    .semantics { contentDescription = if (t.recording) recStop else recStart }
                    .pressable(enabled = rec, role = Role.Button) { t.record(s) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(24.dp)) { if (t.recording) drawRect(p.paper) else drawCircle(if (rec) p.wrong else p.hideInk, size.minDimension / 2) }
            }
            Text(stringResource(if (t.recording) R.string.shadow_stop else if (t.have) R.string.shadow_again else R.string.shadow_talk), style = Type.small.ui(), color = if (rec) p.ink else p.inkSoft)
        }
        RoundButton(stringResource(R.string.shadow_mine), 56.dp, Color.Transparent, if (t.have) p.giltText else p.hair, enabled = t.have && !t.recording, onDark = p.paper) {
            s.narrator.stop(); t.playing = false; s.recorder.play(t.file)
        }
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

