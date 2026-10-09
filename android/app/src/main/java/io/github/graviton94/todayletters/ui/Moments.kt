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
fun MomentCard(s: AppState, work: Work, m: Moment, onMap: () -> Unit, onPhoto: () -> Unit) {
    val p = Ink.palette
    val lang = uiLang()
    when (m) {
        is Moment.Location -> {
            // 요즘 메신저의 위치 카드: 둥근 모서리 안에 옛 동판 지도, 이름 · 주소, 단추 둘. 지도는 카드 밖으로 나가지 않는다.
            val place = work.places.firstOrNull { it.id == m.place }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            Column(Modifier.widthIn(max = 280.dp)) {
                Column(
                    Modifier.clip(shape).background(CardInk).border(1.dp, CardEdge, shape)
                        .pressable { place?.let { openMaps(ctx, it, m.title[lang]) } ?: onMap() },
                ) {
                    ArlesMap(work, focus = m.place, modifier = Modifier.fillMaxWidth().height(150.dp), compact = true)
                    Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.title[lang], style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = CardText, maxLines = 1)
                        Text(m.address, style = Type.small.copy(fontFamily = Faces.text), color = CardSoft, maxLines = 1)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val btn = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            Box(Modifier.weight(1f).heightIn(min = 36.dp).clip(btn).background(CardButton).pressable { place?.let { openMaps(ctx, it, m.title[lang]) } }, contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.map_open), style = Type.small.ui(), color = Color(0xFFD2A955))
                            }
                            Box(Modifier.weight(1f).heightIn(min = 36.dp).clip(btn).background(CardButton).pressable(onClick = onMap), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.map_places), style = Type.small.ui(), color = CardText)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.moment_location, work.name[lang]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            }
        }
        is Moment.Photo -> if (m.sketch) SketchCard(m, lang) else Column(
            Modifier.widthIn(max = 260.dp).graphicsLayer { rotationZ = -1.2f }.pressable(onClick = onPhoto)
                .background(Color(0xFFFBF8F1)).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlateImage(m.image, Modifier.fillMaxWidth().aspectRatio(1.2f))
            Text(m.caption[lang], style = Type.small.ui(), color = Color(0xFF2A2118))
            Text(stringResource(R.string.moment_photo, work.name[lang]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF6E6150))
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
    }
}

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
            Canvas(Modifier.size(38.dp)) {
                val c = Offset(size.width / 2, size.height / 2); val r = size.minDimension / 2
                for (k in 0 until 6) {
                    val a = Math.toRadians(k * 60.0 - 90); val e = Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat())
                    drawLine(ink, c, e, 1.3.dp.toPx())
                    val m1 = Offset(c.x + r * 0.6f * kotlin.math.cos(a).toFloat(), c.y + r * 0.6f * kotlin.math.sin(a).toFloat())
                    for (s in listOf(-1, 1)) {
                        val b = a + s * Math.toRadians(35.0)
                        drawLine(ink, m1, Offset(m1.x + r * 0.3f * kotlin.math.cos(b).toFloat(), m1.y + r * 0.3f * kotlin.math.sin(b).toFloat()), 1.1.dp.toPx())
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(m.title[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft)
                Text(m.value[lang], style = Type.title.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = ink)
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
    val bmp = remember(m.image) { runCatching { ctx.assets.open("sketches/${m.image}").use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    var full by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.graphicsLayer { rotationZ = -0.8f }.background(Color(0xFFF4EBD5)).padding(6.dp).pressable { full = true }) {
            if (bmp != null) androidx.compose.foundation.Image(bmp, m.caption[lang], Modifier.fillMaxWidth().aspectRatio(1.3f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        Text(m.caption[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
    }
    if (full && bmp != null) androidx.compose.ui.window.Dialog(onDismissRequest = { full = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xF0120D09)).clickable { full = false }.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(Tokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            androidx.compose.foundation.Image(bmp, m.caption[lang], Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
            Text(m.caption[lang], style = Type.small.ui(), color = Color(0xFFEADFC8))
            if (m.credit.isNotEmpty()) Text(m.credit, style = Type.small.copy(fontSize = Tokens.Text.caps), color = Color(0xFFA8977C))
        }
    }
}

/** 지도 시트: 지금까지 나온 장소에 핀, 누르면 그 장소 이야기. */
@Composable
fun MapSheet(s: AppState, work: Work, onClose: () -> Unit) {
    val p = Ink.palette
    val lang = uiLang()
    androidx.activity.compose.BackHandler(onBack = onClose)
    val seen = work.chapters.flatMap { c -> c.letters.filter { s.progress(work.series.id, c.id, it.id).shown > 0 }.flatMap { it.moments } }
        .filterIsInstance<Moment.Location>().map { it.place }.toSet() + "station"
    var pick by remember { mutableStateOf(seen.lastOrNull { it != "station" } ?: "station") }
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(p.paper).clickable(enabled = false) {}.navigationBarsPadding().padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.map_title), style = Type.heading.ui(), color = p.ink)
            Box(Modifier.fillMaxWidth().aspectRatio(0.95f).border(Tokens.Stroke.hair, p.line)) {
                ArlesMap(work, focus = pick, modifier = Modifier.fillMaxSize(), visible = seen, onPick = { pick = it })
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            work.places.firstOrNull { it.id == pick }?.let { pl ->
                Text(pl.name[lang], style = Type.body.ui(), color = p.ink)
                Text(pl.note[lang], style = Type.small.ui(), color = p.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.close), small = true, onClick = onClose) }
                    Box(Modifier.weight(1f)) { Primary(stringResource(R.string.map_open), small = true) { openMaps(ctx, pl, pl.name[lang]) } }
                }
            }
        }
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

/**
 * 1888년 아를 시가도 같은 동판 지도: 론강(두 강둑 선 + 물결 결) · 성곽 안 옛 시가(기울어진 격자 블록, 빗금) ·
 * 원형 경기장 · 남쪽 운하 · 철길 · 나침반 · 표제 띠. 장소 좌표(0~1)는 이 그림 위의 자리.
 * [compact] 면 [focus] 를 가운데 두고 1.6배로 (위치 카드). [visible] 에 든 장소만 핀.
 */
@Composable
fun ArlesMap(work: Work, focus: String?, modifier: Modifier, compact: Boolean = false, visible: Set<String>? = null, onPick: (String) -> Unit = {}) {
    val lang = uiLang()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val caps = remember { runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.caps) }.getOrNull() }
    val italic = remember { runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.display_italic) }.getOrNull() }
    val fp = work.places.firstOrNull { it.id == focus }
    BoxWithConstraints(modifier.clipToBounds().background(MapPaper)) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        // 지도 한 장의 크기 (가로 기준, 세로는 0.95 비율) 와 보이는 창
        val z = if (compact) 1.6f else 1f
        val mw = W * z; val mh = (if (compact) W / 0.95f else H) * z
        val ox = if (compact && fp != null) (W / 2 - fp.x * mw).coerceIn(W - mw, 0f) else 0f
        val oy = if (compact && fp != null) (H / 2 - fp.y * mh).coerceIn(H - mh, 0f) else 0f
        Canvas(Modifier.fillMaxSize()) {
            translate(ox, oy) { drawEngraved(mw, mh) }
            if (!compact) cartouche(caps, italic)
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0x5C785523), center = Offset(W / 2, H * 0.45f), radius = maxOf(W, H) * 0.75f))
            compass(W, H)
        }
        val d = LocalDensity.current
        work.places.filter { visible == null || it.id in visible || it.id == focus }.forEach { pl ->
            if (compact && pl.id != focus) return@forEach
            val x = with(d) { (ox + pl.x * mw).toDp() }; val y = with(d) { (oy + pl.y * mh).toDp() }
            Column(
                Modifier.offset(x - 40.dp, y - 34.dp).size(80.dp, 52.dp).clickable(enabled = !compact) { onPick(pl.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Pin(if (pl.id == focus) Color(0xFF9A3B22) else Color(0xFF3D4C6E), 24.dp)
                if (!compact) Text(pl.name[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF2A2118), maxLines = 1)
            }
        }
    }
}

private val MapPaper = Color(0xFFF1E6CC)
private val Engrave = Color(0xFF4A3A22)

private fun DrawScope.drawEngraved(w: Float, h: Float) {
    val px = w / 300f   // 굵기 기준 (지도 폭 300 일 때 1)
    // 론강: 북동에서 들어와 시가 북서를 감아 남서로. 진한 강둑 두 줄 사이를 옅게 채우고 물결 결
    val river = Path().apply {
        moveTo(w * 1.05f, h * 0.02f)
        cubicTo(w * 0.72f, h * 0.06f, w * 0.48f, h * 0.10f, w * 0.34f, h * 0.22f)
        cubicTo(w * 0.20f, h * 0.36f, w * 0.18f, h * 0.62f, -w * 0.05f, h * 0.94f)
    }
    drawPath(river, Engrave, style = Stroke(32f * px))
    drawPath(river, Color(0xFFD9D4BC), style = Stroke(29.5f * px))
    for (k in listOf(-9f, -4f, 1f, 6f)) {
        translate(0f, k * px) { drawPath(river, Color(0xCC6C7470), style = Stroke(0.6f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * px, 6f * px)))) }
    }
    // 남쪽 운하 (아를–부크): 랑글루아 다리 쪽으로
    drawLine(Engrave, Offset(w * 0.56f, h * 0.64f), Offset(w * 0.76f, h * 1.02f), 7f * px)
    drawLine(Color(0xFFD9D4BC), Offset(w * 0.56f, h * 0.64f), Offset(w * 0.76f, h * 1.02f), 5f * px)
    // 성 밖으로 나가는 길 두 줄
    drawLine(Engrave, Offset(w * 0.30f, h * 0.52f), Offset(w * 0.16f, h * 1.02f), 0.6f * px)
    drawLine(Engrave, Offset(w * 0.32f, h * 0.53f), Offset(w * 0.18f, h * 1.02f), 0.6f * px)
    // 철길 (역에서 북동으로): 선 + 침목
    val r0 = Offset(w * 0.62f, h * 0.30f); val r1 = Offset(w * 1.03f, h * 0.10f)
    drawLine(Color(0xFF2A2118), r0, r1, 1f * px)
    for (i in 1..13) {
        val t = i / 14f; val c = Offset(r0.x + (r1.x - r0.x) * t, r0.y + (r1.y - r0.y) * t)
        drawLine(Color(0xFF2A2118), Offset(c.x - 1.5f * px, c.y - 3f * px), Offset(c.x + 1.5f * px, c.y + 3f * px), 0.7f * px)
    }
    // 성곽 안 옛 시가: 기울어진 격자 블록에 빗금
    val cx = w * 0.52f; val cy = h * 0.47f; val rx = w * 0.21f; val ry = h * 0.21f
    drawOval(Engrave, Offset(cx - rx - 7 * px, cy - ry - 7 * px), androidx.compose.ui.geometry.Size((rx + 7 * px) * 2, (ry + 7 * px) * 2), style = Stroke(1.1f * px))
    drawOval(Engrave, Offset(cx - rx - 11 * px, cy - ry - 11 * px), androidx.compose.ui.geometry.Size((rx + 11 * px) * 2, (ry + 11 * px) * 2), style = Stroke(0.5f * px))
    val bw = 17f * px; val bh = 12f * px; val gap = 3.2f * px
    rotate(-14f, Offset(cx, cy)) {
        for (i in -9..9) for (j in -8..8) {
            val x = cx + i * (bw + gap); val y = cy + j * (bh + gap)
            val dx = (x - cx) / rx; val dy = (y - cy) / ry
            if (dx * dx + dy * dy > 0.88f || (i * 7 + j * 3) % 11 == 0) continue
            val tl = Offset(x - bw / 2, y - bh / 2)
            drawRect(Color(0xFFE6D8B8), tl, androidx.compose.ui.geometry.Size(bw, bh))
            clipRect(tl.x, tl.y, tl.x + bw, tl.y + bh) {
                var s = -bh; while (s < bw) { drawLine(Color(0xFF7A6440), Offset(tl.x + s, tl.y + bh), Offset(tl.x + s + bh, tl.y), 0.55f * px); s += 2.6f * px }
            }
            drawRect(Engrave, tl, androidx.compose.ui.geometry.Size(bw, bh), style = Stroke(0.6f * px))
        }
    }
    // 원형 경기장
    val ac = Offset(w * 0.58f, h * 0.50f); val ar = androidx.compose.ui.geometry.Size(34f * px, 26f * px)
    drawOval(MapPaper, Offset(ac.x - ar.width / 2, ac.y - ar.height / 2), ar)
    clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(ac.x - ar.width / 2, ac.y - ar.height / 2, ac.x + ar.width / 2, ac.y + ar.height / 2)) }) {
        var s = -ar.height; while (s < ar.width) { drawLine(Color(0xFF2A2118), Offset(ac.x - ar.width / 2 + s, ac.y + ar.height / 2), Offset(ac.x - ar.width / 2 + s + ar.height * 0.6f, ac.y - ar.height / 2), 0.55f * px); s += 2.2f * px }
    }
    drawOval(Color(0xFF2A2118), Offset(ac.x - ar.width / 2, ac.y - ar.height / 2), ar, style = Stroke(1.2f * px))
    drawOval(MapPaper, Offset(ac.x - 8 * px, ac.y - 5.5f * px), androidx.compose.ui.geometry.Size(16 * px, 11 * px))
    drawOval(Color(0xFF2A2118), Offset(ac.x - 8 * px, ac.y - 5.5f * px), androidx.compose.ui.geometry.Size(16 * px, 11 * px), style = Stroke(0.8f * px))
}

private fun DrawScope.compass(w: Float, h: Float) {
    val c = Offset(w - 28.dp.toPx(), h - 28.dp.toPx()); val r = 12.dp.toPx()
    drawCircle(Engrave, r, c, style = Stroke(0.6.dp.toPx()))
    drawPath(Path().apply { moveTo(c.x, c.y - r * 1.15f); lineTo(c.x + r * 0.27f, c.y); lineTo(c.x, c.y + r * 1.15f); lineTo(c.x - r * 0.27f, c.y); close() }, Engrave)
}

/** 표제 띠: ARLES · plan de la ville · 1888 (큰 지도에만). */
private fun DrawScope.cartouche(caps: android.graphics.Typeface?, italic: android.graphics.Typeface?) {
    val x = 10.dp.toPx(); val y = size.height - 42.dp.toPx(); val bw = 104.dp.toPx(); val bh = 32.dp.toPx()
    drawRect(Color(0xFFF4EBD5), Offset(x, y), androidx.compose.ui.geometry.Size(bw, bh))
    drawRect(Engrave, Offset(x, y), androidx.compose.ui.geometry.Size(bw, bh), style = Stroke(0.8.dp.toPx()))
    drawRect(Engrave, Offset(x + 3.dp.toPx(), y + 3.dp.toPx()), androidx.compose.ui.geometry.Size(bw - 6.dp.toPx(), bh - 6.dp.toPx()), style = Stroke(0.4.dp.toPx()))
    drawContext.canvas.nativeCanvas.apply {
        val p1 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = caps; textSize = 10.dp.toPx(); color = 0xFF2A2118.toInt(); textAlign = android.graphics.Paint.Align.CENTER; letterSpacing = 0.25f }
        drawText("ARLES", x + bw / 2, y + 15.dp.toPx(), p1)
        val p2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = italic; textSize = 8.5f * density; color = 0xFF4A3A22.toInt(); textAlign = android.graphics.Paint.Align.CENTER }
        drawText("plan de la ville · 1888", x + bw / 2, y + 25.dp.toPx(), p2)
    }
}

/** 지도 핀. */
@Composable
fun Pin(c: Color, size: androidx.compose.ui.unit.Dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val path = Path().apply {
        moveTo(w / 2, w); cubicTo(w * 0.1f, w * 0.55f, w * 0.12f, w * 0.05f, w / 2, w * 0.05f)
        cubicTo(w * 0.88f, w * 0.05f, w * 0.9f, w * 0.55f, w / 2, w); close()
    }
    drawPath(path, c)
    drawCircle(Color(0xFFFBF6EA), w * 0.14f, Offset(w / 2, w * 0.38f))
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
