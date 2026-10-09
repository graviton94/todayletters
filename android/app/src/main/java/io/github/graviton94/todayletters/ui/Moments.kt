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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
            val place = work.places.firstOrNull { it.id == m.place }
            Column(Modifier.widthIn(max = 280.dp).pressable(onClick = onMap).background(p.slip).border(Tokens.Stroke.hair, p.line)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
                    ArlesMap(work, focus = m.place, modifier = Modifier.fillMaxSize(), compact = true)
                }
                Row(Modifier.padding(Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Pin(Color(0xFFA2382A), 14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(m.title[lang], style = Type.body.ui(), color = p.slipInk, maxLines = 1)
                        Text(m.address, style = Type.small.copy(fontFamily = Faces.display), color = p.slipSoft, maxLines = 1)
                    }
                }
                Text(stringResource(R.string.moment_location, work.name[lang]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.slipSoft,
                    modifier = Modifier.padding(start = Tokens.Space.s3, end = Tokens.Space.s3, bottom = Tokens.Space.s3))
                @Suppress("UNUSED_EXPRESSION") place
            }
        }
        is Moment.Photo -> Column(
            Modifier.widthIn(max = 260.dp).graphicsLayer { rotationZ = -1.2f }.pressable(onClick = onPhoto)
                .background(Color(0xFFFBF8F1)).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlateImage(m.image, Modifier.fillMaxWidth().aspectRatio(1.2f))
            Text(m.caption[lang], style = Type.small.ui(), color = Color(0xFF2A2118))
            Text(stringResource(R.string.moment_photo, work.name[lang]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF6E6150))
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
            work.places.firstOrNull { it.id == pick }?.let { pl ->
                Text(pl.name[lang], style = Type.body.ui(), color = p.ink)
                Text(pl.note[lang], style = Type.small.ui(), color = p.inkSoft)
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
}

/**
 * 손으로 그린 1888년 아를: 론강 · 성벽 안 옛 도시 · 남쪽 운하 · 철길. 장소마다 핀.
 * [visible] 에 든 장소만 핀을 보이고 (편지에서 나온 곳), [focus] 는 붉은 핀.
 */
@Composable
fun ArlesMap(work: Work, focus: String?, modifier: Modifier, compact: Boolean = false, visible: Set<String>? = null, onPick: (String) -> Unit = {}) {
    val lang = uiLang()
    BoxWithConstraints(modifier.background(Color(0xFFEEE5D0))) {
        Canvas(Modifier.fillMaxSize()) { drawArles() }
        work.places.filter { visible == null || it.id in visible || it.id == focus }.forEach { pl ->
            if (compact && pl.id != focus) return@forEach
            val x = maxWidth * pl.x; val y = maxHeight * pl.y
            Column(
                Modifier.offset(x - 40.dp, y - 34.dp).size(80.dp, 52.dp).clickable { onPick(pl.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Pin(if (pl.id == focus) Color(0xFFA2382A) else Color(0xFF3D4C6E), 22.dp)
                if (!compact) Text(pl.name[lang], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF2A2118), maxLines = 1)
            }
        }
    }
}

private fun DrawScope.drawArles() {
    val w = size.width; val h = size.height
    val ink = Color(0x592A2118)
    // 론강: 북동에서 들어와 도시 서쪽을 감아 남서로
    val river = Path().apply {
        moveTo(w * 0.78f, -10f)
        cubicTo(w * 0.55f, h * 0.18f, w * 0.18f, h * 0.22f, w * 0.2f, h * 0.48f)
        cubicTo(w * 0.22f, h * 0.7f, w * 0.05f, h * 0.85f, -10f, h * 0.98f)
    }
    drawPath(river, Color(0xFFB9C7DA), style = Stroke(w * 0.07f))
    drawPath(river, Color(0x553D4C6E), style = Stroke(1.2f))
    // 성벽 안 옛 도시
    val town = Path().apply {
        moveTo(w * 0.32f, h * 0.36f); cubicTo(w * 0.45f, h * 0.28f, w * 0.66f, h * 0.32f, w * 0.7f, h * 0.45f)
        cubicTo(w * 0.72f, h * 0.6f, w * 0.52f, h * 0.68f, w * 0.38f, h * 0.62f); cubicTo(w * 0.28f, h * 0.56f, w * 0.26f, h * 0.44f, w * 0.32f, h * 0.36f); close()
    }
    drawPath(town, Color(0xFFE4D6B8))
    drawPath(town, ink, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
    // 골목 몇 줄
    for (k in 0..4) drawLine(Color(0x332A2118), Offset(w * (0.36f + k * 0.07f), h * 0.36f), Offset(w * (0.33f + k * 0.07f), h * 0.62f), 1f)
    // 철길 (북쪽, 역으로)
    drawLine(Color(0x802A2118), Offset(w * 0.4f, h * 0.12f), Offset(w * 1.02f, h * 0.2f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    // 남쪽 운하 (아를-부크 운하)
    drawLine(Color(0xFF9FB4CC), Offset(w * 0.55f, h * 0.66f), Offset(w * 0.78f, h * 1.02f), w * 0.025f)
    // 원형 경기장
    drawOval(Color(0x592A2118), Offset(w * 0.53f, h * 0.47f), androidx.compose.ui.geometry.Size(w * 0.07f, h * 0.05f), style = Stroke(1.5f))
    // 나침반
    drawLine(ink, Offset(w * 0.92f, h * 0.86f), Offset(w * 0.92f, h * 0.76f), 1.5f)
    drawLine(ink, Offset(w * 0.9f, h * 0.79f), Offset(w * 0.92f, h * 0.76f), 1.5f)
    drawLine(ink, Offset(w * 0.94f, h * 0.79f), Offset(w * 0.92f, h * 0.76f), 1.5f)
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
