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
import androidx.compose.ui.semantics.selected
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
        // 통과 (ShadowTake.passed, 판정은 한 곳): 편지 걸음이면 그 칸 통과로, 봉인까지의 따라 읽기면 안 된 횟수를 센다 (세 번이면 ‘넘어가기’)
        onVoice = { ok -> if (step && ok) s.markPassed(id, chapter, letter.id, spot.tag) else if (!step && !ok) { tries[at] = (tries[at] ?: 0) + 1 } },
        sayLike = s.sayLike(id, unit.text)) { sc, _ ->
        if (sc.stars >= 1) s.markStar(id, chapter, letter.id)   // 봉인 조건: 따라 읽기 별 하나 이상 (F13)
        s.recordStars(id, chapter, letter.id, spot.tag, sc.stars)   // 메달 진열장의 따라 읽기 별 (마디마다 가장 많이 받은 별)
        if (sc.passed) s.markPassed(id, chapter, letter.id, spot.tag)
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
 * 이 화면에서 새로 녹음한 것은 음절 수도 본다 (core 의 SpeakCheck, 길이는 보지 않음): 원래 소리에서 센 음절 (없으면 [estimate], 글자로 어림한 것) 과
 * 40% 넘게 어긋나면 ‘조금 더 또박또박 · 끝까지 말해 봐요’ ([unclear]) 로 한 번 더, 같은 칸에서 두 번째로 안 되면 목소리만 들어갔어도 통과.
 */
@androidx.compose.runtime.Stable
class ShadowTake(val audio: String?, val start: Int, val end: Int, val file: java.io.File, val step: Boolean,
                 /** 원래 소리가 없을 때 견줄 음절 수 (내 이야기로: 글자로 어림, core 의 SpeakCheck.estimate). null 이면 음절은 따지지 않는다. */
                 val estimate: Int? = null,
                 /** 비슷하게 읽으면 (내 글자로 쓴 소리, core 의 SayLike): 두 번째로 안 되면 보여 준다. 없으면 null. */
                 sayLike: String? = null) {
    /** 소리 힌트 파일을 뒤에서 다 읽으면 채워진다 ([rememberShadowTake]). */
    var sayLike by mutableStateOf(sayLike)
    var recording by mutableStateOf(false)
    var playing by mutableStateOf(false)
    /** 녹음이 바뀔 때마다 하나씩 (다시 그리기 · 다시 채점). */
    var tick by mutableIntStateOf(0)
    var result by mutableStateOf<Triple<io.github.graviton94.todayletters.core.Prosody.Score, FloatArray?, FloatArray?>?>(null)
    var scoring by mutableStateOf(false)
    /** 원래 소리의 선 (core 의 Prosody.preview: 채점 뒤 원래 선과 같은 칸 · 같은 폭, -1..1). 녹음 전부터 판에 점선으로. */
    var refCurve by mutableStateOf<FloatArray?>(null)
    /** 원래 선의 폭 (반음): 녹음 중 내 선을 같은 폭으로 (core 의 LiveLine). */
    var refRange by mutableStateOf(4f)
    /** 앞뒤 침묵을 뺀 원래 소리의 길이 (밀리초, 모르면 null): 녹음 고리 · 저절로 멈추기 (core 의 AutoStop). */
    var refMs by mutableStateOf<Int?>(null)
    /** 녹음 고리 · 저절로 멈추기의 기준 길이: 원래 소리, 없으면 글자로 어림한 음절 ([estimate]). 모르면 null (고리 없음 · 상한만). */
    val targetMs: Int? get() = refMs ?: if (!hasAudio) io.github.graviton94.todayletters.core.AutoStop.estimateMs(estimate) else null
    /** 녹음 고리 (0..1, 첫 목소리부터 [targetMs] 만큼 차오름). 녹음 중이 아니거나 길이를 모르면 null. */
    var ring by mutableStateOf<Float?>(null)
    /** 이번 녹음의 첫 목소리 시각 (녹음 시작부터 밀리초, 아직이면 -1): 실시간 선의 0 자리. */
    var onset by mutableStateOf(-1L)
    /** 원래 소리 칩 (1× · 0.7×): true 면 원래 소리를 0.7× 로. */
    var slow by mutableStateOf(false)
    /** 비교 듣기 (원래 소리 → 내 소리) 의 지금 쪽. 듣지 않으면 null. 판정은 바꾸지 않는다. */
    var compare by mutableStateOf<Side?>(null)
    enum class Side { ORIG, MINE }
    private var compareRun = 0
    var allowed by mutableStateOf(false)
    /** 마지막 녹음이 통과인가 (목소리 + 음절 검사, 아직 재지 않았으면 null). 원래 소리가 없어도 잰다. */
    var voice by mutableStateOf<Boolean?>(null)
    /** 마지막 녹음에 목소리는 들어갔지만 음절이 원래와 어긋남 (‘또박또박’ 한 줄). */
    var unclear by mutableStateOf(false)
    /** 이 칸에서 새로 녹음해 안 된 횟수 (두 번째 실패는 목소리만 들어가면 통과). */
    var misses by mutableIntStateOf(0)
    /** 봐주기 전 판정으로 안 된 횟수 (침묵 · 음절 어긋남): 둘이 되면 ‘비슷하게 읽으면’ 을 그 뒤로 늘 보여 준다. */
    var rough by mutableIntStateOf(0)
    val showsSayLike: Boolean get() = rough >= 2 && !sayLike.isNullOrBlank()
    internal var askMic: () -> Unit = {}
    val levels = androidx.compose.runtime.mutableStateListOf<Float>()
    val pitches = androidx.compose.runtime.mutableStateListOf<Float>()
    /** 프레임마다 녹음 시작부터의 시각 (밀리초, [levels] · [pitches] 와 같은 칸). */
    val times = androidx.compose.runtime.mutableStateListOf<Long>()
    val hasAudio: Boolean get() = audio != null && start >= 0
    val have: Boolean get() { tick; return file.exists() }
    /**
     * 마지막 녹음이 통과인가 (아직 재지 않았으면 null). 판정은 이것 하나 (core 의 SpeakCheck.judge): [step] 이면 [voice]
     * (목소리 + 음절 맞음 또는 점수 ≥ 통과 문턱, 두 번째 실패는 목소리만), 봉인까지의 따라 읽기는 점수 ≥ 통과 문턱.
     * 점수 · 별 · 안내 한 줄 · 다음 단추 · 효과음이 모두 이것을 따른다 (‘훌륭해요’ 와 ‘또박또박’ 이 함께 나오지 않게).
     */
    val passed: Boolean? get() = if (step) voice else if (voice == null) null else result?.first?.passed == true

    /** 녹음하는 동안 쓰는 파일: 다 녹음하면 [file] 로 바꾼다 (너무 짧은 녹음이 지난 녹음을 남긴 채 다시 채점되지 않게). */
    private val pending: java.io.File get() = java.io.File(file.path + ".rec")

    /**
     * 녹음을 끝낸다. 쓸 만한 녹음 (0.3초 이상, PcmRecorder) 이 남았으면 이번 녹음으로, 아니면 지난 녹음도 지운다
     * (그러면 판에 점수 없이 ‘다시 해 봐요’). 녹음이 바뀌는 것은 여기뿐: 원래 소리 · 내 소리 듣기는 판정을 바꾸지 않는다.
     */
    fun stopRecording(s: AppState) {
        if (!recording) return
        s.pcm.onFrame = null; s.pcm.stop(); recording = false; ring = null
        val rec = pending
        if (rec.length() > 44) { file.delete(); if (!rec.renameTo(file)) { rec.copyTo(file, overwrite = true); rec.delete() } }
        else { rec.delete(); file.delete() }
        tick++
    }

    /** 원래 소리 (칩 [slow] 면 0.7×, 아니면 기본 빠르기. [speed] 를 주면 그 빠르기로 한 번). */
    fun original(s: AppState, speed: Float? = null) {
        stopRecording(s)
        stopCompare(s)
        playOriginal(s, speed ?: if (slow) io.github.graviton94.todayletters.core.Listen.SLOW else null) {}
    }

    private fun playOriginal(s: AppState, speed: Float?, then: () -> Unit) {
        val a = audio ?: return
        if (!hasAudio) return
        s.recorder.stopPlaying()
        playing = if (end == Int.MAX_VALUE) s.narrator.play(a, speed) { playing = false; then() }
            else s.narrator.playRange(a, start, end, speed) { playing = false; then() }
    }

    /**
     * 비교 듣기 (시안 rec_controls 2 · C): 원래 소리 (칩 빠르기) 에 이어 내 소리. 듣는 중에 다시 누르면 멈춤.
     * 녹음 · 판정 ([tick] · [voice] · [result]) 은 건드리지 않는다.
     */
    fun compareListen(s: AppState) {
        if (compare != null) { stopCompare(s); return }
        stopRecording(s)
        if (!have) return
        val run = ++compareRun
        compare = Side.ORIG
        playOriginal(s, if (slow) io.github.graviton94.todayletters.core.Listen.SLOW else null) {
            if (run != compareRun || compare != Side.ORIG) return@playOriginal
            compare = Side.MINE
            s.recorder.play(file) { if (run == compareRun) compare = null }
        }
        if (!playing && compare == Side.ORIG) { compare = Side.MINE; s.recorder.play(file) { if (run == compareRun) compare = null } }
    }

    /** 비교 듣기를 멈춘다 (듣는 중이 아니면 아무것도). */
    fun stopCompare(s: AppState) {
        val was = compare ?: return
        compareRun++; compare = null
        if (was == Side.ORIG) { s.narrator.stop(); playing = false } else s.recorder.stopPlaying()
    }

    /** 내 소리만 (비교 듣기 전 · 원래 소리가 없을 때). */
    fun mine(s: AppState) {
        stopCompare(s)
        s.narrator.stop(); playing = false; s.recorder.play(file)
    }

    /** 녹음 시작 · 끝 (마이크 권한이 없으면 먼저 묻는다). */
    fun record(s: AppState) {
        if (!allowed) { askMic(); return }
        if (recording) { stopRecording(s); return }
        stopCompare(s)
        s.narrator.stop(); s.recorder.stopPlaying(); playing = false
        levels.clear(); pitches.clear(); times.clear(); onset = -1L
        // 저절로 멈추기 (core 의 AutoStop): 목소리가 들어갔고 · 원래 길이의 80% 를 지났고 · 그 뒤 1초 조용할 때만. 상한을 넘으면 언제나
        val stop = io.github.graviton94.todayletters.core.AutoStop(targetMs)
        val begin = android.os.SystemClock.elapsedRealtime()
        ring = stop.progress()
        s.pcm.onFrame = { db, hz ->
            if (recording) {
                val at = android.os.SystemClock.elapsedRealtime() - begin
                levels.add(db); pitches.add(hz); times.add(at)
                if (levels.size > 1500) { levels.removeAt(0); pitches.removeAt(0); times.removeAt(0) }
                val done = stop.frame(at, db, hz)
                onset = stop.onset; ring = stop.progress()
                if (done) stopRecording(s)
            }
        }
        file.parentFile?.mkdirs()
        pending.delete()
        result = null; voice = null; unclear = false
        recording = s.pcm.start(pending)
        if (!recording) ring = null
    }
}

/**
 * [ShadowTake] 하나 ([key] 가 바뀌면 새로). 녹음이 끝나 채점되면 [onScored] (점수, 이 화면에서 새로 녹음했는가).
 * 이 화면에서 새로 녹음할 때마다 (원래 소리가 있든 없든) [onVoice] (목소리가 들어갔는가).
 */
@Composable
fun rememberShadowTake(s: AppState, audio: String?, start: Int, end: Int, file: java.io.File, step: Boolean, key: Any,
                       onVoice: (Boolean) -> Unit = {},
                       /** 원래 소리가 없을 때 견줄 음절 수 ([ShadowTake.estimate]). */ estimate: Int? = null,
                       /** 비슷하게 읽으면 ([ShadowTake.sayLike], [AppState.sayLike]). */ sayLike: String? = null,
                       onScored: (io.github.graviton94.todayletters.core.Prosody.Score, Boolean) -> Unit = { _, _ -> }): ShadowTake {
    val ctx = LocalContext.current
    val t = remember(key) { ShadowTake(audio, start, end, file, step, estimate, sayLike).also { it.allowed = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED } }
    androidx.compose.runtime.SideEffect { if (sayLike != null && t.sayLike == null) t.sayLike = sayLike }
    val scored by androidx.compose.runtime.rememberUpdatedState(onScored)
    val voiced by androidx.compose.runtime.rememberUpdatedState(onVoice)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { t.allowed = it }
    t.askMic = { ask.launch(Manifest.permission.RECORD_AUDIO) }
    // 앱을 벗어나면 녹음은 그 자리에서 끝난다 (그때까지 녹음한 것은 남는다)
    LaunchedEffect(t, s.away) { t.stopRecording(s); t.stopCompare(s); t.playing = false }
    // 원래 소리의 음높이 선 · 길이 (칸이 바뀔 때 한 번): 녹음 전부터 판에 점선, 녹음 고리 · 저절로 멈추기의 기준
    LaunchedEffect(t) {
        if (!t.hasAudio) return@LaunchedEffect
        val pv = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val ref = io.github.graviton94.todayletters.data.decodeRange(ctx, t.audio!!, t.start, t.end) ?: return@withContext null
            val P = io.github.graviton94.todayletters.core.Prosody
            P.preview(P.analyze(ref, io.github.graviton94.todayletters.data.Wav.RATE))
        } ?: return@LaunchedEffect
        t.refCurve = pv.ref; t.refRange = pv.range; t.refMs = pv.ms
    }
    // 억양 · 리듬: 원래 소리와 내 녹음을 기기 안에서 비교 (녹음이 끝날 때마다 한 번: [ShadowTake.tick] 은 녹음을 끝낼 때만 바뀐다)
    // 목소리가 들어갔는가는 원래 소리가 없어도 잰다 (침묵 · 잡음만인 녹음은 통과가 아니고, 점수도 보이지 않는다)
    LaunchedEffect(t, t.tick) {
        t.result = null; t.voice = null; t.unclear = false; t.scoring = false
        if (t.recording) return@LaunchedEffect
        val P = io.github.graviton94.todayletters.core.Prosody
        val judge = io.github.graviton94.todayletters.core.SpeakCheck
        val rate = io.github.graviton94.todayletters.data.Wav.RATE
        val fresh = t.tick > 0
        // 녹음이 남지 않았다 (너무 짧음 · 마이크 오류): 막 녹음한 것이면 점수 없이 ‘다시 해 봐요’, 아직 녹음 전이면 아무것도
        if (!t.have) {
            if (fresh) { t.voice = false; t.misses++; t.rough++; s.cue(false); voiced(false) }
            return@LaunchedEffect
        }
        t.scoring = true
        val bg = kotlinx.coroutines.Dispatchers.Default
        val mt = kotlinx.coroutines.withContext(bg) {
            runCatching { P.analyze(io.github.graviton94.todayletters.data.Wav.samples(io.github.graviton94.todayletters.data.Wav.pcm(t.file)), rate) }.getOrNull()
        }
        val spoke = mt != null && P.spoke(mt)
        // 원래 소리의 분석 (음절 수 · 억양 · 리듬): 목소리가 들어간 녹음일 때만 (아니면 0점을 보이지 않는다)
        val rt = if (spoke && t.hasAudio) kotlinx.coroutines.withContext(bg) {
            io.github.graviton94.todayletters.data.decodeRange(ctx, t.audio!!, t.start, t.end)?.let { P.analyze(it, rate) }
        } else null
        // 채점이 안 된 것 (원래 · 내 소리가 너무 짧음, 완성도 0) 은 점수 없음으로: 0점 판을 보이지 않는다
        val score = if (rt != null) kotlinx.coroutines.withContext(bg) { P.score(rt, mt!!) }.takeIf { it.complete > 0 } else null
        val curves = if (rt != null) kotlinx.coroutines.withContext(bg) { P.curves(rt, mt!!) } else null
        // 통과 (core 의 SpeakCheck.judge, 한 곳): 목소리 + (음절 · 점수). 예전에 녹음해 둔 것 (이 화면에서 새로 한 것이 아님) 은 목소리만 본다
        val refSyl = if (!fresh) null else if (rt != null) P.syllables(rt).takeIf { it > 0 } else if (!t.hasAudio) t.estimate else null
        val mineSyl = if (mt != null) P.syllables(mt) else 0
        val total = score?.total
        val v = judge.judge(spoke, mineSyl, refSyl, t.misses, total)
        // 채점하는 동안 새로 녹음을 시작했으면 이 결과는 버린다
        if (t.recording) { t.scoring = false; return@LaunchedEffect }
        t.result = score?.let { Triple(it, curves?.ref, curves?.mine) }
        t.voice = v.pass; t.unclear = v.hint
        if (fresh && !v.pass) t.misses++
        // 봐주기 (두 번째는 목소리만으로 통과) 전의 판정: 두 번 안 되면 ‘비슷하게 읽으면’
        if (fresh && !judge.judge(spoke, mineSyl, refSyl, 0, total).pass) t.rough++
        t.scoring = false
        val ok = t.passed == true
        if (fresh) { score?.let { s.recordScore(it) }; s.cue(ok) }
        score?.let { scored(it, fresh) }
        if (fresh) voiced(ok)
    }
    return t
}

/**
 * 판 (시안 rec_controls 2 · D): 녹음 전에는 원래 소리의 오르내림 점선 (따라 할 것을 미리), 녹음 중에는 그 점선 위에 내 선이
 * 같은 시간축 · 같은 폭으로 그려지고, 녹음 뒤에는 두 선 (core 의 Prosody.curves) 과 점수 · 별 · 억양 · 리듬.
 * [preview] 가 아니면 녹음 전 점선을 보이지 않는다 (답을 아직 못 본 복습 말하기 · 회화 빈칸 전).
 */
@Composable
fun ShadowBoard(t: ShadowTake, preview: Boolean = true) {
    val p = Ink.palette
    val res = t.result
    val before = !t.recording && !t.have
    if (!(t.recording || (t.have && t.hasAudio) || (before && preview && t.refCurve != null))) return
    Column(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (t.refCurve != null || !before) Text("┅ " + stringResource(R.string.shadow_orig), style = Type.small.ui(), color = p.inkSoft)
            if (!before) Text("━ " + stringResource(R.string.shadow_mine), style = Type.small.ui(), color = p.giltText)
        }
        if (before) Canvas(Modifier.fillMaxWidth().height(96.dp)) { drawCurve(t.refCurve, p.inkSoft, dotted = true, range = 1f) }
        else if (t.recording) LiveTrace(t)
        else if (res == null) Text(stringResource(if (t.scoring) R.string.shadow_scoring else R.string.shadow_noscore), style = Type.small.ui(), color = p.inkSoft)
        else {
            // 두 선 (core 의 Prosody.curves): 옥타브 접기 · 다듬기 · 끊김, 같은 반음 폭 (-1..1), 내 선은 원래의 시간축에 맞춤
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                drawCurve(res.second, p.inkSoft, dotted = true, range = 1f); drawCurve(res.third, p.giltText, dotted = false, range = 1f)
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
            // 칭찬 한 줄은 통과 판정 ([ShadowTake.passed]) 을 따른다: 안 된 녹음에 ‘훌륭해요’ 가 붙지 않게
            val ok = t.passed == true
            Text(when {
                !ok -> stringResource(R.string.shadow_retry, P.PASS)
                stars >= 3 -> stringResource(R.string.read_great)
                stars == 2 -> stringResource(R.string.read_good)
                else -> stringResource(R.string.read_pass)
            }, style = Type.small.ui(), color = if (ok) p.giltText else p.inkSoft)
        }
        if (!before) Text(stringResource(R.string.shadow_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
    }
}

/**
 * 단추 셋 (시안 rec_controls 2 · A~C), 한 줄 · 같은 높이: 원래 소리 (56dp, 아래 칩 1× · 0.7×) · 말하기 (80dp, 금테 · 녹음 중 금빛 바탕에 멈춤 네모,
 * 둘레 고리가 원래 문장 길이만큼 차오름) · 내 소리 (56dp, 채점 뒤에는 ‘비교 듣기’: 원래 소리 → 내 소리, 다시 누르면 멈춤).
 * 그 위에 안내 한 줄 (비교 듣기 중이면 지금 나오는 쪽이 금빛). [canListen] 이 아니면 원래 소리를 막고 (빈칸 · 조각을 다 맞추기 전),
 * [canRecord] 가 아니면 녹음을 막는다.
 */
@Composable
fun ShadowControls(s: AppState, t: ShadowTake, canListen: Boolean = true, canRecord: Boolean = true,
                   /** 녹음 전 안내 한 줄 (기본: 이 문장을 따라 읽어 보세요). */ tip: Int = R.string.shadow_tap) {
    val p = Ink.palette
    val listen = t.hasAudio && canListen
    // 비교 듣기: 원래 소리를 들을 수 있고 녹음이 있을 때 (내 소리 단추가 바뀐다)
    val canCompare = listen && t.have && !t.recording
    val cmp = t.compare
    if (cmp != null) {
        val o = stringResource(R.string.shadow_orig); val m = stringResource(R.string.shadow_mine)
        val line = stringResource(R.string.shadow_compare_line, o, m)
        val lit = androidx.compose.ui.text.AnnotatedString.Builder(line).apply {
            val (w, at) = if (cmp == ShadowTake.Side.ORIG) o to line.indexOf(o) else m to line.lastIndexOf(m)
            if (at >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = p.giltText, fontWeight = FontWeight.SemiBold), at, at + w.length)
        }.toAnnotatedString()
        Text(lit, style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s2))
    } else Text(
        // 안내 한 줄: 스크롤 밖, 단추 바로 위 (단추에 가리지 않게). 목소리가 들리지 않은 녹음이면 ‘다시 해 봐요’,
        // 목소리는 들렸지만 음절이 원래와 어긋나면 ‘조금 더 또박또박 · 끝까지 말해 봐요’. 녹음 전 원래 선이 보이면 ‘선을 따라 오르내리듯’
        stringResource(when {
            t.recording -> if (t.targetMs != null) R.string.shadow_listening_auto else R.string.shadow_recording
            t.unclear && t.tick > 0 -> R.string.speak_clearer
            t.voice == false && t.tick > 0 -> R.string.talk_retry
            t.have -> R.string.shadow_check
            tip == R.string.shadow_tap && listen && t.refCurve != null -> R.string.shadow_follow_line
            else -> tip
        }),
        style = Type.small.ui(), color = if (t.recording) p.giltText else p.inkSoft, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s2),
    )
    // 두 번째로 안 되면: 비슷하게 읽으면 (원래 소리 단추 바로 위, 그 뒤로 늘)
    SayLikeLine(t)
    Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3, horizontal = Tokens.Space.s3), verticalAlignment = Alignment.Top) {
        // 원래 소리 + 칩 (1× · 0.7×). 원래 소리가 없으면 단추를 두지 않는다 (자리는 비워 말하기가 가운데). 답을 아직 못 봐서 못 듣는 때만 흐린 단추
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (t.hasAudio) {
                val on = t.playing && cmp == null
                RoundButton(stringResource(R.string.shadow_orig), 56.dp, filled = on, enabled = listen && !t.recording, glyph = if (on) Glyph.STOP else Glyph.PLAY) {
                    if (on) { s.narrator.stop(); t.playing = false } else t.original(s)
                }
                SpeedChip(t, enabled = listen && !t.recording)
            }
        }
        // 말하기 (녹음 시작 · 끝)
        val recStart = stringResource(R.string.a11y_record); val recStop = stringResource(R.string.a11y_record_stop)
        val rec = canRecord || t.recording
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(BUTTON_BOX), contentAlignment = Alignment.Center) {
                // 고리: 첫 목소리부터 원래 문장 길이만큼 차오른다 (core 의 AutoStop). 움직임 줄이기면 프레임마다 그 자리 그대로 (부드럽게 잇지 않음)
                val target = t.ring
                val eased by androidx.compose.animation.core.animateFloatAsState(target ?: 0f,
                    androidx.compose.animation.core.tween(160, easing = androidx.compose.animation.core.LinearEasing), label = "ring")
                if (t.recording && target != null) {
                    val shown = if (LocalStill.current) target else eased
                    Canvas(Modifier.size(BUTTON_BOX)) {
                        val w = 3.dp.toPx()
                        val inset = w / 2
                        val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        drawArc(p.hair, 0f, 360f, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                        drawArc(p.gilt, -90f, 360f * shown, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
                Box(
                    Modifier.size(80.dp).background(if (t.recording) p.gilt else if (rec) Color.Transparent else p.hide, CircleShape)
                        .border(1.5.dp, if (rec) p.gilt else p.hair, CircleShape)
                        .semantics { contentDescription = if (t.recording) recStop else recStart }
                        .pressable(enabled = rec, role = Role.Button) { t.record(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(22.dp)) {
                        if (t.recording) { val q = size.minDimension * 0.8f; drawRect(p.paper, androidx.compose.ui.geometry.Offset((size.width - q) / 2, (size.height - q) / 2), androidx.compose.ui.geometry.Size(q, q)) }
                        else drawCircle(if (rec) p.gilt else p.hideInk, size.minDimension / 2)
                    }
                }
            }
            Text(stringResource(if (t.recording) R.string.shadow_stop else if (t.have) R.string.shadow_again else R.string.shadow_talk), style = Type.small.ui(), color = if (rec) p.ink else p.inkSoft)
        }
        // 내 소리 → 채점 뒤 ‘비교 듣기’ (원래 소리 → 내 소리, 다시 누르면 멈춤). 원래 소리를 못 듣는 때는 내 소리만
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canCompare || cmp != null) RoundButton(stringResource(R.string.shadow_compare), 56.dp, filled = cmp != null, enabled = true,
                glyph = if (cmp != null) Glyph.STOP else Glyph.DOUBLE) { t.compareListen(s) }
            else RoundButton(stringResource(R.string.shadow_mine), 56.dp, filled = false, enabled = t.have && !t.recording, glyph = Glyph.PLAY) { t.mine(s) }
        }
    }
}

/** 둥근 단추 셋이 같은 높이에 서도록: 말하기 (80dp) + 고리 자리. */
private val BUTTON_BOX = 92.dp

/** 원래 소리 빠르기 칩 “1× · 0.7×” (고른 쪽은 금빛 글자 · 옅은 금빛 바탕). */
@Composable
private fun SpeedChip(t: ShadowTake, enabled: Boolean) {
    val p = Ink.palette
    Row(Modifier.border(1.dp, p.hair, androidx.compose.foundation.shape.RoundedCornerShape(Tokens.Radius.round))
        .background(Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(Tokens.Radius.round))
        .padding(1.dp)) {
        listOf(false to "1×", true to SLOW_TEXT).forEach { (slow, label) ->
            val on = t.slow == slow
            val said = stringResource(R.string.shadow_speed, label)
            Box(Modifier.heightIn(min = 30.dp)
                .background(if (on) p.gilt.copy(alpha = 0.16f) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(Tokens.Radius.round))
                .semantics { contentDescription = said; selected = on }
                .pressable(enabled = enabled, role = Role.RadioButton) { t.slow = slow }
                .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, style = Type.small.copy(fontSize = Tokens.Text.caps), color = if (!enabled) p.inkSoft.copy(alpha = 0.6f) else if (on) p.giltText else p.inkSoft)
            }
        }
    }
}

/** 반음 곡선 하나 (가운데 0, 위아래 ±[range], 채점 판의 두 선 · 미리 보기 선은 이미 -1..1 이라 1). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurve(c: FloatArray?, color: Color, dotted: Boolean, upTo: Float = 1f, range: Float = 8f) {
    if (c == null || c.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    var started = false
    c.forEachIndexed { k, v ->
        if (v.isNaN()) { started = false; return@forEachIndexed }   // 긴 쉼: 선을 끊는다
        val x = size.width * upTo * k / (c.size - 1); val y = size.height / 2 - v.coerceIn(-range, range) / range * size.height / 2.2f
        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(if (dotted) 1.5.dp.toPx() else 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
        pathEffect = if (dotted) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 8f)) else null))
}

/**
 * 녹음 중 판 (시안 rec_controls 2 · D): 원래 소리 점선 위에 내 음높이 선 (core 의 LiveLine). 가로는 같은 시간축
 * (첫 목소리부터, 원래 길이 = 판 너비, 더 길게 말하면 선 전체가 줄어든다), 세로는 원래 선과 같은 폭이라 채점 뒤 선과 모양이 이어진다.
 * 원래 선이 없으면 (원래 소리 없음) 크기 막대를 같은 시간축에 옅게 깔아 듣고 있음을 보인다.
 */
@Composable
private fun LiveTrace(t: ShadowTake) {
    val p = Ink.palette
    val ref = t.refCurve
    val pts = io.github.graviton94.todayletters.core.LiveLine.points(t.times, t.levels, t.pitches, t.onset, t.targetMs, t.refRange)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        drawCurve(ref, p.inkSoft, dotted = true, range = 1f)
        if (ref == null && t.times.isNotEmpty()) {
            val span = maxOf((t.targetMs ?: 0).toLong(), t.times.last(), 3000L).toFloat()
            val bw = (size.width * 40f / span).coerceAtLeast(1f)
            t.times.forEachIndexed { k, at ->
                val db = t.levels.getOrNull(k) ?: return@forEachIndexed
                val h = ((db + 60f) / 60f).coerceIn(0.02f, 1f) * size.height * 0.9f
                drawRect(p.hair, androidx.compose.ui.geometry.Offset(at / span * size.width, size.height / 2 - h / 2), androidx.compose.ui.geometry.Size(bw * 0.6f, h))
            }
        }
        fun y(v: Float) = size.height / 2 - v * size.height / 2.2f
        val path = androidx.compose.ui.graphics.Path(); var started = false
        pts.forEach { pt ->
            if (pt == null) { started = false; return@forEach }
            val x = pt.first * size.width
            if (!started) { path.moveTo(x, y(pt.second)); started = true } else path.lineTo(x, y(pt.second))
        }
        drawPath(path, p.giltText, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        pts.lastOrNull { it != null }?.let { drawCircle(p.giltText, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(it.first * size.width, y(it.second))) }
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

private enum class Glyph { PLAY, DOUBLE, STOP }

/** 둥근 단추 (원래 소리 · 내 소리 · 비교 듣기): [BUTTON_BOX] 칸 가운데라 말하기와 같은 높이 · 같은 이름표 줄. [filled] 면 금빛 바탕. */
@Composable
private fun RoundButton(label: String, diameter: androidx.compose.ui.unit.Dp, filled: Boolean, enabled: Boolean, glyph: Glyph, onClick: () -> Unit) {
    val p = Ink.palette
    val ring = if (enabled) p.gilt else p.hair
    Box(Modifier.size(BUTTON_BOX), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(diameter).background(if (filled) p.gilt else Color.Transparent, CircleShape).border(1.5.dp, ring, CircleShape)
                .pressable(enabled = enabled, role = Role.Button) { onClick() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val c = if (filled) p.paper else ring
                fun tri(x0: Float, x1: Float) = androidx.compose.ui.graphics.Path().apply { moveTo(x0, size.height * 0.18f); lineTo(x1, size.height / 2); lineTo(x0, size.height * 0.82f); close() }
                when (glyph) {
                    Glyph.PLAY -> drawPath(tri(size.width * 0.25f, size.width * 0.85f), c)
                    Glyph.DOUBLE -> { drawPath(tri(0f, size.width * 0.48f), c); drawPath(tri(size.width * 0.52f, size.width), c) }
                    Glyph.STOP -> { val q = size.minDimension * 0.6f; drawRect(c, androidx.compose.ui.geometry.Offset((size.width - q) / 2, (size.height - q) / 2), androidx.compose.ui.geometry.Size(q, q)) }
                }
            }
        }
    }
    Text(label, style = Type.small.ui(), color = if (enabled) p.ink else p.inkSoft, maxLines = 1)
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
    // 편지 카드 (첫 문장): 목소리 엽서 옆에
    var card by remember { mutableStateOf(false) }
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
            if (letter.messages.isNotEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).border(1.dp, t.ink).pressable {
                if (playing) { s.recorder.stopPlaying(); playing = false }
                card = true
            }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.letter_card_seal), style = Type.body.ui(), color = t.ink) }
            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).pressable { s.back() }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.close), style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = t.ink)
            }
        }
    }
    if (card) letter.messages.firstOrNull()?.let { m -> LetterCardSheet(s, work, letter, m) { card = false } }
}

private fun stringResource0(ctx: android.content.Context, id: Int, vararg args: Any) = ctx.getString(id, *args)

