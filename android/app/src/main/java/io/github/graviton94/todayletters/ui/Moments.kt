package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Moment
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 위인의 편지가 요즘 메신저로 온다면: 문장 사이에 끼어드는 위치 공유 · 사진 공유.
 * 위치는 손으로 그린 1888년 아를 지도에 핀, 사진은 그날 그린 그림 (누르면 그림 속 산책).
 */
@Composable
fun MomentCard(s: AppState, work: Work, m: Moment, onMap: () -> Unit, onPhoto: () -> Unit, onMusic: (Moment.Music) -> Unit = {}) {
    val p = Ink.palette
    val lang = uiLang()
    when (m) {
        is Moment.Location -> {
            // 위치 카드: 그림 없이 이름 · 주소와 ‘지도에서 보기’ 하나 (지도는 휴대폰의 지도 앱이 보여 준다).
            val place = work.places.firstOrNull { it.id == m.place }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Column(Modifier.widthIn(max = 280.dp).fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caps(stringResource(R.string.moment_location).uppercase(), p.giltText, small = true, decorative = true)
                Text(m.title[lang], style = Type.heading.ui(), color = p.ink)
                Text(m.address, style = Type.small.copy(fontFamily = Faces.text), color = p.inkSoft)
                if (place != null) Box(Modifier.heightIn(min = 40.dp).pressable { openMaps(ctx, place, m.title[lang]) }, contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.map_open), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.ink)
                }
                Hair()
            }
        }
        is Moment.Photo -> if (m.sketch) SketchCard(m, lang) else Column(
            Modifier.widthIn(max = 260.dp).graphicsLayer { rotationZ = -1.2f }.pressable(onClick = onPhoto)
                .background(Color(0xFFFBF8F1)).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlateImage(m.image, Modifier.fillMaxWidth().aspectRatio(1.2f))
            Text(m.caption[lang], style = Type.small.ui(), color = Color(0xFF2A2118))
            Text(stringResource(R.string.moment_photo, if (lang == Lang.KO) josa(work.name[lang], "이", "가") else work.name[lang]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF6E6150))
        }
        is Moment.Notice -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                m.text[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(p.leaf).border(1.dp, p.hair, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        is Moment.Transfer -> TransferCard(m, lang, work)
        is Moment.Weather -> WeatherCard(m, lang)
        // 편지 속 그 곡: 녹음이 없으면 아무것도 놓지 않는다
        is Moment.Music -> if (m.playable) MusicChip(m, lang) { onMusic(m) }
    }
}

/** ‘편지 속 그 곡 듣기’ 칩: 둥근 재생 단추 · 이름 · 곡 이름과 길이. 누르면 듣기 시트. */
@Composable
private fun MusicChip(m: Moment.Music, lang: Lang, onClick: () -> Unit) {
    val p = Ink.palette
    Row(
        Modifier.fillMaxWidth().border(1.dp, p.giltText.copy(alpha = 0.6f)).background(p.leaf).pressable(onClick = onClick)
            .padding(horizontal = Tokens.Space.s3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        Box(Modifier.size(40.dp).background(p.ink, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { PlayGlyph(p.paper, false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.music_chip), style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
            Text(stringResource(R.string.music_chip_sub, m.piece[lang]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** 재생 ▶ · 멈춤 ❚❚ 모양. */
@Composable
fun PlayGlyph(c: Color, pause: Boolean, size: androidx.compose.ui.unit.Dp = 14.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    if (pause) {
        drawRect(c, Offset(w * 0.18f, w * 0.1f), androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.8f))
        drawRect(c, Offset(w * 0.6f, w * 0.1f), androidx.compose.ui.geometry.Size(w * 0.22f, w * 0.8f))
    } else drawPath(Path().apply { moveTo(w * 0.22f, w * 0.08f); lineTo(w * 0.92f, w * 0.5f); lineTo(w * 0.22f, w * 0.92f); close() }, c)
}

/** 유튜브에서 그 곡 찾기 (앱 안에 넣지 않고 브라우저 · 유튜브 앱으로). */
fun openYouTube(ctx: android.content.Context, query: String) {
    val uri = android.net.Uri.parse("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))
    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** 0:24 꼴. */
private fun clock(ms: Int): String { val t = (ms / 1000).coerceAtLeast(0); return "${t / 60}:${(t % 60).toString().padStart(2, '0')}" }

/**
 * 편지 속 그 곡 듣기 시트: 곡 이름(원어 · 앱 글자) · 60초 발췌의 진행 · 10초 앞뒤 · 재생/멈춤 · 출처 한 줄(연주 · 라이선스)
 * · 유튜브에서 전곡 듣기. 여는 동안 편지 낭독은 잠깐 멈추고(이어 듣기 중이었으면 닫을 때 다시), 닫으면 발췌도 멈춘다.
 */
@Composable
fun MusicSheet(s: AppState, work: Work, m: Moment.Music, onClose: () -> Unit) {
    val p = Ink.palette
    val ui = uiLang()
    val original = work.series.original
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var playing by remember(m) { mutableStateOf(false) }
    var at by remember(m) { androidx.compose.runtime.mutableIntStateOf(0) }
    var total by remember(m) { androidx.compose.runtime.mutableIntStateOf(60_000) }
    androidx.compose.runtime.DisposableEffect(m) {
        // 낭독을 멈춘다: 이어 듣기(알림이 있는 것)는 잠깐 멈춤, 한 문장 듣기는 그만
        val resume = io.github.graviton94.todayletters.data.Playback.now != null && !io.github.graviton94.todayletters.data.Playback.paused
        if (resume) io.github.graviton94.todayletters.data.Playback.pause() else s.narrator.stop()
        playing = s.music.play(m.path(work.series.id)) { playing = false; at = total }
        total = s.music.duration()
        onDispose {
            s.music.stop()
            if (resume) io.github.graviton94.todayletters.data.Playback.play()
        }
    }
    androidx.compose.runtime.LaunchedEffect(m) {
        while (true) {
            kotlinx.coroutines.delay(200)
            if (s.music.loaded != null) { at = s.music.position(); playing = s.music.playing }
        }
    }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).clickable(enabled = false) {}.navigationBarsPadding()
                .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(p.hair))
            Caps(deco(original, "Écouter", "Hören", "Escuchar").uppercase() + " · " + stringResource(R.string.music_caps), p.giltText, small = true)
            Text(m.piece[original], style = Type.title.copy(fontFamily = Faces.display).of(original), color = p.ink)
            if (ui != original) Text(m.piece[ui], style = Type.small.ui(), color = p.inkSoft)
            // 진행: 막대 무늬 (곡마다 같은 모양) 위에 들은 만큼 금빛
            val frac = if (total > 0) (at.toFloat() / total).coerceIn(0f, 1f) else 0f
            val bars = remember(m.audio) { java.util.Random(m.audio.hashCode().toLong()).let { r -> List(40) { 0.25f + r.nextFloat() * 0.75f } } }
            Canvas(Modifier.fillMaxWidth().height(36.dp).padding(top = Tokens.Space.s2)) {
                val step = size.width / bars.size
                bars.forEachIndexed { k, h ->
                    val bh = size.height * h
                    drawRect(if ((k + 0.5f) / bars.size <= frac) p.giltText else p.giltText.copy(alpha = 0.3f),
                        Offset(k * step + step * 0.2f, (size.height - bh) / 2), androidx.compose.ui.geometry.Size(step * 0.6f, bh))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(clock(at), style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.music_excerpt, clock(total)), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.heightIn(min = 44.dp).pressable { s.music.skip(-10_000); at = s.music.position() }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.music_back10), style = Type.small.ui(), color = p.inkSoft)
                }
                val label = stringResource(if (playing) R.string.narration_pause else R.string.narration_play)
                Box(Modifier.size(64.dp).background(p.ink, androidx.compose.foundation.shape.CircleShape)
                    .androidx_desc(label).pressable {
                        when {
                            s.music.loaded == null -> { playing = s.music.play(m.path(work.series.id)) { playing = false; at = total }; total = s.music.duration() }
                            playing -> { s.music.pause(); playing = false }
                            else -> { s.music.resume(); playing = true }
                        }
                    }, contentAlignment = Alignment.Center) { PlayGlyph(p.paper, playing, 22.dp) }
                Box(Modifier.heightIn(min = 44.dp).pressable { s.music.skip(10_000); at = s.music.position() }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.music_fwd10), style = Type.small.ui(), color = p.inkSoft)
                }
            }
            // 출처 한 줄: 연주 · 라이선스
            val credit = listOf(m.performer, m.license).filter { it.isNotBlank() }.joinToString(" · ")
            if (credit.isNotEmpty()) Text(credit, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            val composer = work.fullName.trim().split(" ").lastOrNull().orEmpty().ifEmpty { work.sender }
            Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.buttonSm).border(1.dp, p.ink)
                .pressable { openYouTube(ctx, composer + " " + io.github.graviton94.todayletters.core.Breaks.plain(m.piece[Lang.EN])) },
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.music_youtube), style = Type.body.ui(), color = p.ink)
            }
        }
    }
}

private fun Modifier.androidx_desc(label: String) = this.then(Modifier.semantics { contentDescription = label })

/** 송금 카드: 테오가 보낸 돈은 오른쪽 푸른 쪽지(테오 쪽), 빈센트가 낸 돈은 왼쪽 편지지. */
@Composable
private fun TransferCard(m: Moment.Transfer, lang: Lang, work: Work) {
    val theo = !m.outgoing
    val bg = if (theo) Color(0xFFC9D2D8) else Color(0xFFE9DCC0)
    val ink = if (theo) Color(0xFF24303A) else Color(0xFF2A2118)
    val soft = if (theo) Color(0xFF4A5865) else Color(0xFF6A5B47)
    val shape = if (theo) androidx.compose.foundation.shape.RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp) else androidx.compose.foundation.shape.RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (theo) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 260.dp).fillMaxWidth(0.72f).clip(shape).background(bg)) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).background(ink, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text("F", style = Type.caps.copy(fontSize = Tokens.Text.small), color = bg)
                }
                Column {
                    Text(m.label[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft)
                    Text(m.amount, style = Type.title.copy(fontFamily = Faces.display, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = ink)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.15f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 7.dp)) {
                Text(m.memo[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft, modifier = Modifier.weight(1f), maxLines = 1)
                Text(stringResource(if (theo) R.string.transfer_received else R.string.transfer_paid), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft)
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") work
}

/** 날씨 공유 카드: 큰 값(눈 60cm)과 편지 원문 한 토막. */
@Composable
private fun WeatherCard(m: Moment.Weather, lang: Lang) {
    val bg = Color(0xFF24303A); val ink = Color(0xFFE3EAF0); val soft = Color(0xFF9FB0BE)
    Column(Modifier.widthIn(max = 270.dp).fillMaxWidth(0.74f).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)).background(bg)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                // 큰 값이 없는 날씨(한 줄 글뿐인 것)는 그 글을 조금 크게
                val value = m.value[lang]
                Text(m.title[lang], style = if (value.isBlank()) Type.body.ui() else Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (value.isBlank()) ink else soft)
                if (value.isNotBlank()) Text(value, style = Type.title.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = ink)
            }
        }
        if (m.quote.isNotEmpty()) Text("“${m.quote}”", style = Type.small.copy(fontFamily = Faces.text, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = soft,
            modifier = Modifier.fillMaxWidth().background(Color(0x10FFFFFF)).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

/** 편지 원본의 스케치: 사진처럼 비스듬히, 위쪽 그림 부분만. 누르면 편지 한 장 전체. */
@Composable
private fun SketchCard(m: Moment.Photo, lang: Lang) {
    val p = Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp = rememberPicture("sketches/${m.image}").bmp
    var full by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.graphicsLayer { rotationZ = -0.8f }.background(Color(0xFFF4EBD5)).padding(6.dp).pressable { full = true }) {
            if (bmp != null) androidx.compose.foundation.Image(bmp, m.caption[lang], Modifier.fillMaxWidth().aspectRatio(1.3f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        Text(m.caption[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
    }
    if (full && bmp != null) FullDialog(onDismissRequest = { full = false }) {
        ZoomViewer(bmp, m.caption[lang], m.credit, onClose = { full = false })
    }
}

/** 위치 카드 색 (테마와 상관없이 메신저 카드처럼). */
private val CardInk = Color(0xFF241B13)
private val CardEdge = Color(0xFF3A2E22)
private val CardText = Color(0xFFEADFC8)
private val CardSoft = Color(0xFFA8977C)
private val CardButton = Color(0xFF33281D)

/** 그 장소의 실제 좌표로 구글 지도를 연다 (앱이 없으면 브라우저). */
fun openMaps(ctx: android.content.Context, place: io.github.graviton94.todayletters.core.Place, label: String) {
    if (place.lat == 0.0 && place.lng == 0.0) return
    val uri = android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${place.lat},${place.lng}")
    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    @Suppress("UNUSED_EXPRESSION") label
}

/** 테오의 답장: 푸른 얇은 편지지 (오른쪽), 펜글씨. */
@Composable
fun TheoNote(learn: String, learnLang: Lang, read: String?, readLang: Lang, signer: String) {
    Column(
        Modifier.widthIn(max = 260.dp).graphicsLayer { rotationZ = 0.6f; shadowElevation = 4.dp.toPx() }
            .background(Color(0xFFC9D2D8)).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(learn, style = Type.heading.copy(fontFamily = Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic).of(learnLang), color = Color(0xFF24303A))
        if (read != null) Text(read, style = Type.small.of(readLang), color = Color(0xFF4A5865))
        Text("— $signer", style = Type.signature, color = Color(0xFF4A5865), modifier = Modifier.align(Alignment.End))
    }
}

/** 책갈피 (내 구절). */
@Composable
fun Bookmark(on: Boolean) {
    val p = Ink.palette
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val path = Path().apply { moveTo(w * 0.22f, w * 0.08f); lineTo(w * 0.78f, w * 0.08f); lineTo(w * 0.78f, w * 0.92f); lineTo(w * 0.5f, w * 0.7f); lineTo(w * 0.22f, w * 0.92f); close() }
        if (on) drawPath(path, Color(0xFF8F6A27)) else drawPath(path, Color(0xFF8F6A27), style = Stroke(1.4.dp.toPx()))
    }
}
