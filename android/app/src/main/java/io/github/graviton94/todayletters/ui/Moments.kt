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
            // 위치 카드: 그림 없이 이름 · 주소와 ‘지도에서 보기’ 하나 (지도는 휴대폰의 지도 앱이 보여 준다).
            val place = work.places.firstOrNull { it.id == m.place }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Column(Modifier.widthIn(max = 280.dp).fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caps(stringResource(R.string.moment_location, work.name[lang]).uppercase(), p.giltText, small = true, decorative = true)
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
