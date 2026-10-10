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
 * 따라 읽기 (모든 시리즈 공통): 편지 문장을 낭독 마디째. 원래 소리 한 마디 → 내 목소리 한 마디.
 * 마디마다 녹음이 남고 (기기 안에만), 끝까지 하면 그 녹음을 이어 붙인 ‘내 낭독’ 한 편이 된다.
 * 채점은 억양 · 리듬 (core 의 Prosody, 기기 안에서): 70점을 넘으면 그 마디 통과. 봉인은 모든 마디 통과.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowScreen(s: AppState, r: Route.Shadow) {
    val gold = Color(0xFFD2A955); val night = Color(0xFF17110C); val text = Color(0xFFEADFC8); val soft = Color(0xFFA8977C); val line = Color(0xFF4A3B2B); val coral = Color(0xFFE08A6E)
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val view = s.room(id)
    val chunks = remember(letter.id) { s.shadowChunks(id, chapter, letter) }
    val flat = remember(chunks) { chunks.flatMapIndexed { i, cs -> cs.indices.map { j -> i to j } } }
    fun ok(i: Int, j: Int) = s.take(id, chapter, letter.id, i, j).exists() && (chunks[i][j].start < 0 || s.passed(id, chapter, letter.id, i, j))
    val first = remember(flat) { flat.indexOfFirst { (i, j) -> !ok(i, j) }.let { if (it < 0) flat.lastIndex else it } }
    var at by remember(letter.id) { mutableIntStateOf(first.coerceAtLeast(0)) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }   // 녹음이 바뀌면 화면을 다시 그린다
    var finished by remember { mutableStateOf(false) }
    var unpassed by remember { mutableStateOf(0) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { s.pcm.stop(); s.recorder.release(); s.narrator.stop() } }
    if (flat.isEmpty()) return
    val (mi, cj) = flat[at]
    val chunk = chunks[mi][cj]
    val audio = s.narrator.path(id, chapter, letter.id, "m${mi + 1}_${view.learn.code}")
    val hasAudio = chunk.start >= 0
    val mine = s.take(id, chapter, letter.id, mi, cj)
    tick
    val have = mine.exists()
    val passed = s.passed(id, chapter, letter.id, mi, cj)

    // 억양 · 리듬: 원래 낭독의 이 마디와 내 녹음을 기기 안에서 비교 (녹음이 바뀔 때마다)
    var result by remember(at, tick) { mutableStateOf<Triple<io.github.graviton94.todayletters.core.Prosody.Score, FloatArray?, FloatArray?>?>(null) }
    var scoring by remember(at, tick) { mutableStateOf(false) }
    LaunchedEffect(at, tick) {
        if (!have || !hasAudio) return@LaunchedEffect
        scoring = true
        result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val ref = io.github.graviton94.todayletters.data.decodeRange(ctx, audio, chunk.start, chunk.end) ?: return@withContext null
            val me = io.github.graviton94.todayletters.data.Wav.samples(io.github.graviton94.todayletters.data.Wav.pcm(mine))
            val P = io.github.graviton94.todayletters.core.Prosody
            val rt = P.analyze(ref, io.github.graviton94.todayletters.data.Wav.RATE); val mt = P.analyze(me, io.github.graviton94.todayletters.data.Wav.RATE)
            Triple(P.score(rt, mt), P.semitones(P.trim(rt).f0), P.semitones(P.trim(mt).f0))
        }
        scoring = false
        result?.first?.let { s.recordScore(it); if (it.passed) s.markPassed(id, chapter, letter.id, mi, cj) }
    }

    fun original() {
        if (recording) { s.pcm.stop(); recording = false; tick++ }
        if (!hasAudio) return
        playing = s.narrator.playRange(audio, chunk.start, chunk.end) { playing = false }
    }
    // 새 마디에 오면 원래 소리를 한 번 들려준다
    LaunchedEffect(at) { if (!have) original() }

    Column(Modifier.fillMaxSize().background(night)) {
        TopBar(stringResource(R.string.shadow_title), s, help = null, showBack = true, showSettings = false) {
            Text(stringResource(R.string.shadow_count, at + 1, flat.size), style = Type.caps, color = gold, modifier = Modifier.padding(end = Tokens.Space.s4))
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF33281D))) {
            Box(Modifier.fillMaxWidth(flat.count { (i, j) -> ok(i, j) }.toFloat() / flat.size).height(3.dp).background(gold))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(stringResource(if (hasAudio) R.string.shadow_hint else R.string.shadow_noaudio), style = Type.small.ui(), color = soft)
            // 지금 문장의 마디들: 통과 금빛 바탕 · 녹음했지만 아직 산호색 테 · 지금 마디 금빛 테 · 남은 마디 흐리게
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunks[mi].forEachIndexed { j, c ->
                    val done = ok(mi, j)
                    val tried = !done && s.take(id, chapter, letter.id, mi, j).exists()
                    val now = j == cj
                    Box(
                        Modifier.then(if (done && !now) Modifier.background(gold) else Modifier)
                            .border(if (now) 1.5.dp else 1.dp, if (now) gold else if (done) gold else if (tried) coral else line)
                            .pressable { if (recording) { s.pcm.stop(); recording = false; tick++ }; at = flat.indexOf(mi to j) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(c.text, style = Type.target.of(view.learn).copy(fontSize = 19.sp), color = if (done && !now) night else if (now) text else soft)
                    }
                }
            }
            if (view.showRead) Text(letter.messages[mi].text[view.read], style = Type.base.of(view.read), color = Color(0xFFC9BBA0))
            // 채점 판: 두 음높이 곡선과 억양 · 리듬
            if (have && hasAudio && !recording) Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFF33281D)).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("━ " + stringResource(R.string.shadow_orig), style = Type.small.ui(), color = gold)
                    Text("┅ " + stringResource(R.string.shadow_mine), style = Type.small.ui(), color = coral)
                }
                val res = result
                if (res == null) Text(stringResource(if (scoring) R.string.shadow_scoring else R.string.shadow_noscore), style = Type.small.ui(), color = soft)
                else {
                    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                        fun curve(c: FloatArray?, color: Color, dotted: Boolean) {
                            if (c == null || c.size < 2) return
                            val path = androidx.compose.ui.graphics.Path()
                            c.forEachIndexed { k, v ->
                                val x = size.width * k / (c.size - 1); val y = size.height / 2 - v.coerceIn(-8f, 8f) / 8f * size.height / 2.2f
                                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                pathEffect = if (dotted) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 9f)) else null))
                        }
                        drawLine(line, androidx.compose.ui.geometry.Offset(0f, size.height - 1), androidx.compose.ui.geometry.Offset(size.width, size.height - 1))
                        curve(res.second, gold, false); curve(res.third, coral, true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Meter(stringResource(R.string.shadow_intonation), res.first.intonation, Modifier.weight(1f), gold, text, soft)
                        Meter(stringResource(R.string.shadow_rhythm), res.first.rhythm, Modifier.weight(1f), gold, text, soft)
                    }
                    Text(stringResource(if (res.first.passed) R.string.shadow_pass else R.string.shadow_retry), style = Type.small.ui(), color = if (res.first.passed) gold else coral)
                }
                Text(stringResource(R.string.shadow_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft)
            }
            Text(
                stringResource(if (recording) R.string.shadow_recording else if (have) R.string.shadow_check else R.string.shadow_tap),
                style = Type.small.ui(), color = if (recording) coral else soft,
            )
        }
        // 원래 소리 · 말하기 · 내 소리
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            RoundButton(stringResource(R.string.shadow_orig), 56.dp, if (playing) gold else Color.Transparent, gold, enabled = hasAudio) {
                if (playing) { s.narrator.stop(); playing = false } else original()
            }
            val recStart = stringResource(R.string.a11y_record); val recStop = stringResource(R.string.a11y_record_stop)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(80.dp).background(if (recording) Color(0xFF9A3B22) else Color(0xFF3A2A1E), CircleShape).border(1.dp, Color(0xFF9A3B22), CircleShape)
                        .semantics { contentDescription = if (recording) recStop else recStart }
                        .pressable(role = Role.Button) {
                            if (!allowed) { ask.launch(Manifest.permission.RECORD_AUDIO); return@pressable }
                            if (recording) { s.pcm.stop(); recording = false; tick++ }
                            else { s.narrator.stop(); s.recorder.stopPlaying(); playing = false; recording = s.pcm.start(mine) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) { if (recording) drawRect(Color(0xFFF4EBD5)) else drawCircle(Color(0xFFF4EBD5), size.minDimension / 2) }
                }
                Text(stringResource(if (recording) R.string.shadow_stop else if (have) R.string.shadow_again else R.string.shadow_talk), style = Type.small.ui(), color = text)
            }
            RoundButton(stringResource(R.string.shadow_mine), 56.dp, Color.Transparent, if (have) gold else line, enabled = have && !recording) {
                s.narrator.stop(); playing = false; s.recorder.play(mine)
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3)) {
            val last = at == flat.lastIndex
            val allTried = flat.all { (i, j) -> s.take(id, chapter, letter.id, i, j).exists() }
            Primary(stringResource(if (last && allTried) R.string.shadow_finish else R.string.shadow_next), enabled = have && !recording && !scoring) {
                s.narrator.stop(); s.recorder.stopPlaying(); playing = false
                when {
                    !last -> at++
                    !allTried -> at = flat.indexOfFirst { (i, j) -> !s.take(id, chapter, letter.id, i, j).exists() }
                    else -> {
                        unpassed = flat.count { (i, j) -> !ok(i, j) }
                        if (unpassed == 0) {
                            finished = true
                            if (s.shadowDone(id, chapter, letter)) { s.back(); s.go(Route.Seal(r.room)) }
                        }
                    }
                }
            }
        }
    }
    // 아직 통과 못 한 마디가 있으면: 그 마디로 (봉인은 모든 마디 통과)
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

@Composable
private fun Meter(label: String, value: Int, modifier: Modifier, gold: Color, text: Color, soft: Color) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Type.small.ui(), color = soft)
        Text("$value", style = Type.title.copy(fontFamily = Faces.display), color = text)
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF33281D))) { Box(Modifier.fillMaxWidth(value / 100f).height(4.dp).background(gold)) }
    }
}

@Composable
private fun RoundButton(label: String, diameter: androidx.compose.ui.unit.Dp, fill: Color, ring: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(diameter).background(fill, CircleShape).border(1.dp, ring, CircleShape).pressable(enabled = enabled, role = Role.Button) { onClick() }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val c = if (fill == Color.Transparent) ring else Color(0xFF17110C)
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.25f, size.height * 0.15f); lineTo(size.width * 0.85f, size.height / 2); lineTo(size.width * 0.25f, size.height * 0.85f); close() }
                drawPath(path, c)
            }
        }
        Text(label, style = Type.small.ui(), color = Color(0xFFA8977C))
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

