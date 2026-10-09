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
 * 확인은 스스로: 원래 소리와 내 소리를 번갈아 듣고 다음 마디로.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowScreen(s: AppState, r: Route.Shadow) {
    val gold = Color(0xFFD2A955); val night = Color(0xFF17110C); val text = Color(0xFFEADFC8); val soft = Color(0xFFA8977C); val line = Color(0xFF4A3B2B)
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val view = s.room(id)
    val chunks = remember(letter.id) { s.shadowChunks(id, chapter, letter) }
    val flat = remember(chunks) { chunks.flatMapIndexed { i, cs -> cs.indices.map { j -> i to j } } }
    val first = remember(flat) { flat.indexOfFirst { (i, j) -> !s.take(id, chapter, letter.id, i, j).exists() }.let { if (it < 0) flat.lastIndex else it } }
    var at by remember(letter.id) { mutableIntStateOf(first.coerceAtLeast(0)) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }   // 녹음이 바뀌면 화면을 다시 그린다
    var finished by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { s.recorder.release(); s.narrator.stop() } }
    if (flat.isEmpty()) { s.back(); return }
    val (mi, cj) = flat[at]
    val chunk = chunks[mi][cj]
    val audio = s.narrator.path(id, chapter, letter.id, "m${mi + 1}_${view.learn.code}")
    val hasAudio = chunk.start >= 0
    val mine = s.take(id, chapter, letter.id, mi, cj)
    tick
    val have = mine.exists()

    fun original() {
        s.recorder.stop(); recording = false
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
            Box(Modifier.fillMaxWidth((at + if (have) 1 else 0).toFloat() / flat.size).height(3.dp).background(gold))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(stringResource(if (hasAudio) R.string.shadow_hint else R.string.shadow_noaudio), style = Type.small.ui(), color = soft)
            // 지금 문장의 마디들: 한 마디 금빛 바탕 · 지금 마디 금빛 테 · 남은 마디 흐리게
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunks[mi].forEachIndexed { j, c ->
                    val done = s.take(id, chapter, letter.id, mi, j).exists()
                    val now = j == cj
                    Box(
                        Modifier.then(if (done && !now) Modifier.background(gold) else Modifier)
                            .border(if (now) 1.5.dp else 1.dp, if (now) gold else if (done) gold else line)
                            .pressable { s.recorder.stop(); recording = false; at = flat.indexOf(mi to j) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(c.text, style = Type.target.of(view.learn).copy(fontSize = 19.sp), color = if (done && !now) night else if (now) text else soft)
                    }
                }
            }
            if (view.showRead) Text(letter.messages[mi].text[view.read], style = Type.base.of(view.read), color = Color(0xFFC9BBA0))
            Text(
                stringResource(if (recording) R.string.shadow_recording else if (have) R.string.shadow_check else R.string.shadow_tap),
                style = Type.small.ui(), color = if (recording) Color(0xFFE08A6E) else soft,
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
                            if (recording) { s.recorder.stop(); recording = false; tick++ }
                            else { s.narrator.stop(); playing = false; recording = s.recorder.start(mine, adts = true) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) { if (recording) drawRect(Color(0xFFF4EBD5)) else drawCircle(Color(0xFFF4EBD5), size.minDimension / 2) }
                }
                Text(stringResource(if (recording) R.string.shadow_stop else R.string.shadow_talk), style = Type.small.ui(), color = text)
            }
            RoundButton(stringResource(R.string.shadow_mine), 56.dp, Color.Transparent, if (have) gold else line, enabled = have && !recording) {
                s.narrator.stop(); playing = false; s.recorder.play(mine)
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3)) {
            val last = at == flat.lastIndex
            val all = flat.all { (i, j) -> s.take(id, chapter, letter.id, i, j).exists() }
            Primary(stringResource(if (last && all) R.string.shadow_finish else R.string.shadow_next), enabled = have && !recording) {
                s.narrator.stop(); s.recorder.stopPlaying(); playing = false
                if (!last) at++
                else if (all) {
                    finished = true
                    if (s.shadowDone(id, chapter, letter)) { s.back(); s.go(Route.Seal(r.room)) }
                }
                else at = flat.indexOfFirst { (i, j) -> !s.take(id, chapter, letter.id, i, j).exists() }
            }
        }
    }
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
private fun RoundButton(label: String, size: androidx.compose.ui.unit.Dp, fill: Color, ring: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(size).background(fill, CircleShape).border(1.dp, ring, CircleShape).pressable(enabled = enabled, role = Role.Button) { onClick() }
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
 * 완독 봉인 (모든 시리즈 공통): 혼자 다 읽고 다 따라 읽은 편지. 원문 첫 문장 위에 밀랍 봉인,
 * 내 낭독 듣기, 그 편지에서 모은 물건 · 장소, 다음 편지, 목소리 엽서로 보내기.
 */
@Composable
fun SealScreen(s: AppState, r: Route.Seal) {
    val gold = Color(0xFFD2A955); val text = Color(0xFFEADFC8); val soft = Color(0xFFA8977C)
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val id = r.room.series
    val work = s.work(id)
    val view = s.room(id)
    val letters = work.chapters.flatMap { it.letters }
    val idx = letters.indexOf(letter) + 1
    val reading = remember(letter.id) { s.myReading(id, chapter, letter) }
    val chunks = remember(letter.id) { s.shadowChunks(id, chapter, letter).sumOf { it.size } }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(letter.id) { s.sealSeen(id, chapter, letter) }
    DisposableEffect(Unit) { onDispose { s.recorder.stopPlaying() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF120D09)).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        Text(stringResource(R.string.seal_caps, roman(idx)).uppercase(), style = Type.caps, color = gold, modifier = Modifier.padding(top = Tokens.Space.s5))
        Text(stringResource(R.string.seal_title), style = Type.heading.ui(), color = text, textAlign = TextAlign.Center)
        Box(Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(bottom = 18.dp)) {
            Slip(seed = 41, tilt = -1.2f, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text(dateLine(letter.date, letter.place).uppercase(), style = Type.caps.copy(fontSize = 10.sp), color = Color(0xFF8F6A27))
                    Text(letter.messages.first().text[view.learn], style = Type.target.of(view.learn), color = Ink.palette.slipInk)
                    Text(stringResource(R.string.seal_stats, letter.words.size, chunks), style = Type.small.ui(), color = Ink.palette.slipSoft)
                }
            }
            // 밀랍 봉인: 편지 번호
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 4.dp).size(76.dp).graphicsLayer { rotationZ = -8f }
                    .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFC4563A), Color(0xFF7E2018))), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(roman(idx), style = Type.title.copy(fontFamily = Faces.display, fontWeight = FontWeight.SemiBold), color = Color(0xFFF4EBD5)) }
        }
        // 내 낭독
        if (reading != null) Row(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().border(1.dp, gold).pressable {
                if (playing) { s.recorder.stopPlaying(); playing = false } else { playing = true; s.recorder.play(reading) { playing = false } }
            }.padding(Tokens.Space.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Box(Modifier.size(44.dp).background(gold, CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(16.dp)) {
                    if (playing) drawRect(Color(0xFF17110C))
                    else drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.2f, size.height); close() }, Color(0xFF17110C))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.seal_mine, roman(idx)), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = text)
                Text(stringResource(R.string.seal_mine_d, chunks), style = Type.small.ui(), color = soft)
            }
        }
        // 이 편지에서 모은 물건 · 장소
        val items = letter.words.filter { it.icon.isNotEmpty() }
        val places = letter.moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Location>()
        if (items.isNotEmpty() || places.isNotEmpty()) Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            items.take(2).forEach { w -> Chip("${w.icon} ${firstSense(w.text[view.read])} (${w.text[view.learn]})", Modifier.weight(1f)) }
            places.take(1).forEach { pl -> Chip("📍 ${pl.title[uiLang()]}", Modifier.weight(1f)) }
        }
        Box(Modifier.height(Tokens.Space.s3))
        val upcoming = letters.getOrNull(idx)
        upcoming?.let { u ->
            val d = s.daysUntil(id, u.id)
            Text(
                when { d <= 0 -> stringResource(R.string.next_arrived); d == 1 -> stringResource(R.string.letter_eta); else -> stringResource(R.string.next_in, d) } + " · " + stringResource(R.string.next_title),
                style = Type.small.ui(), color = soft,
            )
        }
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (reading != null) Primary(stringResource(R.string.seal_send)) {
                val share = stringResource0(ctx, R.string.seal_share_text, letter.messages.first().text[view.learn].replace("⁠", ""), work.fullName)
                runCatching {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", reading)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/aac"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, share)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.seal_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            Secondary(stringResource(R.string.close)) { s.back() }
        }
    }
}

private fun stringResource0(ctx: android.content.Context, id: Int, vararg args: Any) = ctx.getString(id, *args)

@Composable
private fun Chip(label: String, modifier: Modifier) {
    Box(modifier.border(1.dp, Color(0xFF33281D)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(label, style = Type.small.ui(), color = Color(0xFFC9BBA0), maxLines = 2)
    }
}
