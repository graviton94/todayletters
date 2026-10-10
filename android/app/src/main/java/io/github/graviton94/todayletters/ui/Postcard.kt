package io.github.graviton94.todayletters.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.Message
import io.github.graviton94.todayletters.data.Work
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 구절 엽서: 왼쪽은 그 편지의 그림, 오른쪽은 엽서 뒷면처럼 원문 · 번역 · 서명 · 우표.
 * 기기 안에서 그림 파일을 만들어 공유 시트로 보낸다 (서버 없음).
 */
fun sharePostcard(ctx: Context, w: Work, l: Letter, m: Message, learn: Lang, read: Lang) {
    val bmp = runCatching { postcard(ctx, w, l, m, learn, read) }.getOrNull() ?: return
    val dir = File(ctx.cacheDir, "postcards").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "postcard-${l.id}.jpg")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "“${Breaks(m.text[learn])}” — ${w.fullName}, ${l.date.take(4)}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.postcard_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 공유 글에는 줄바꿈 표시(U+2060)를 뺀다. */
private fun Breaks(t: String) = t.replace("⁠", "")

private const val W = 1800
private const val H = 1200

fun postcard(ctx: Context, w: Work, l: Letter, m: Message, learn: Lang, read: Lang): Bitmap {
    val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paper = Color.rgb(0xFB, 0xF6, 0xEA)
    val ink = Color.rgb(0x2A, 0x21, 0x18)
    val soft = Color.rgb(0x6E, 0x61, 0x50)
    val gilt = Color.rgb(0x8F, 0x6A, 0x27)
    c.drawColor(paper)

    // 종이 결: 가는 가로줄
    val grain = Paint().apply { color = Color.argb(10, 90, 60, 20) }
    for (y in 0 until H step 5) c.drawRect(0f, y.toFloat(), W.toFloat(), y + 1f, grain)

    // 왼쪽: 그림 (가운데를 채워 자르기)
    val half = W * 0.46f
    val pad = 48f
    val frame = RectF(pad, pad, half, H - pad)
    l.plate?.image?.takeIf { it.isNotEmpty() }?.let { file ->
        runCatching { ctx.assets.open("plates/$file").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { art ->
            val scale = maxOf(frame.width() / art.width, frame.height() / art.height)
            val sw = (frame.width() / scale).toInt(); val sh = (frame.height() / scale).toInt()
            val sx = (art.width - sw) / 2; val sy = (art.height - sh) / 2
            c.drawBitmap(art, Rect(sx, sy, sx + sw, sy + sh), frame, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
    c.drawRect(frame, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(90, 42, 33, 24) })

    // 오른쪽: 엽서 뒷면
    val left = half + 72f
    val right = W - 72f
    fun font(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: fallback
    val display = font(R.font.display_italic, Typeface.SERIF)
    val caps = font(R.font.caps, Typeface.SERIF)
    val body = if (read == Lang.KO) font(R.font.kr_regular, Typeface.SERIF) else font(R.font.text_regular, Typeface.SERIF)
    val learnFace = if (learn == Lang.KO) font(R.font.kr_semibold, Typeface.SERIF) else display

    // 우표: 톱니 테두리 안에 초상
    val stamp = RectF(right - 170f, 72f, right, 72f + 210f)
    val perf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    c.drawRect(stamp, perf)
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper }
    var x = stamp.left; while (x <= stamp.right) { c.drawCircle(x, stamp.top, 7f, hole); c.drawCircle(x, stamp.bottom, 7f, hole); x += 21f }
    var y = stamp.top; while (y <= stamp.bottom) { c.drawCircle(stamp.left, y, 7f, hole); c.drawCircle(stamp.right, y, 7f, hole); y += 21f }
    val inner = RectF(stamp.left + 16, stamp.top + 16, stamp.right - 16, stamp.bottom - 16)
    w.portrait.takeIf { it.isNotEmpty() }?.let { file ->
        runCatching { ctx.assets.open("portraits/$file").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { pic ->
            val sc = maxOf(inner.width() / pic.width, inner.height() / pic.height)
            val sw = (inner.width() / sc).toInt(); val sh = (inner.height() / sc).toInt()
            val sx = (pic.width - sw) / 2; val sy = ((pic.height - sh) / 4)
            c.drawBitmap(pic, Rect(sx, sy, sx + sw, sy + sh), inner, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
    // 소인: 겹친 동그라미와 물결
    val cancel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(150, 60, 50, 40) }
    val cx = stamp.left - 40f; val cy = stamp.centerY() + 30f
    c.drawCircle(cx, cy, 78f, cancel); c.drawCircle(cx, cy, 62f, cancel)
    val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = caps; textSize = 22f; color = cancel.color; textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
    c.drawText(l.place.uppercase().take(10), cx, cy - 6f, mark)
    c.drawText(l.date.take(4), cx, cy + 26f, mark)
    for (i in 0..3) {
        val wy = cy - 45f + i * 30f
        val path = android.graphics.Path().apply { moveTo(cx + 85f, wy); var px = cx + 85f; while (px < stamp.left + 90f) { quadTo(px + 15f, wy - 10f, px + 30f, wy); quadTo(px + 45f, wy + 10f, px + 60f, wy); px += 60f } }
        c.drawPath(path, cancel)
    }

    // 날짜 머리
    val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = caps; textSize = 26f; color = gilt; letterSpacing = 0.2f }
    c.drawText("${l.place.uppercase()} · ${l.date.take(10)}", left, 110f, head)

    // 원문 (배우는 언어) — 길면 글자를 줄인다
    val mainWidth = (right - left).toInt()
    val learnPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = learnFace; color = ink }
    val original = "“${m.text[learn]}”"
    var size = 64f
    var layout: StaticLayout
    while (true) {
        learnPaint.textSize = size
        layout = staticLayout(original, learnPaint, mainWidth, 1.18f)
        if (layout.height < H * 0.42f || size <= 36f) break
        size -= 4f
    }
    var top = 330f
    c.save(); c.translate(left, top); layout.draw(c); c.restore()
    top += layout.height + 40f

    // 번역 (읽는 언어)
    if (read != learn) {
        val readPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; textSize = 34f; color = soft }
        val tr = staticLayout(m.text[read], readPaint, mainWidth, 1.45f)
        c.save(); c.translate(left, top); tr.draw(c); c.restore()
        top += tr.height + 40f
    }

    // 서명
    val sign = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 44f; color = ink }
    c.drawText("— ${w.fullName}", left, minOf(top + 50f, H - 200f), sign)

    // 주소 줄과 앱 표시
    val rule = Paint().apply { color = Color.argb(70, 42, 33, 24); strokeWidth = 2f }
    c.drawLine(left, H - 140f, right, H - 140f, rule)
    val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = caps; textSize = 24f; color = soft; letterSpacing = 0.18f }
    c.drawText(ctx.getString(R.string.app_name).uppercase(), left, H - 92f, foot)
    l.plate?.let { pl ->
        val cap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 26f; color = soft; textAlign = Paint.Align.RIGHT }
        c.drawText(Breaks(pl.title[read]), right, H - 92f, cap)
    }
    return out
}

private fun staticLayout(text: String, paint: TextPaint, width: Int, spacing: Float): StaticLayout {
    val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, spacing)
        .setIncludePad(false)
    if (Build.VERSION.SDK_INT >= 33) b.setLineBreakConfig(
        android.graphics.text.LineBreakConfig.Builder().setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build(),
    )
    return b.build()
}

// ── 작품 카드 (v21 3단계 E5) ─────────────────────────────────────────

/** 카드 크기: 세로 · 정사각 · 이야기(9:16). */
enum class CardFormat(val w: Int, val h: Int, val art: Float) { PORTRAIT(1200, 1560, 0.50f), SQUARE(1400, 1400, 0.46f), STORY(1080, 1920, 0.46f) }

/** 그 편지 날의 달까지 (1888년 9월 · September 1888). */
private fun monthLine(date: String, read: Lang): String = runCatching {
    val m = java.time.YearMonth.parse(date.take(7))
    if (read == Lang.KO) "${m.year}년 ${m.monthValue}월" else m.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH))
}.getOrDefault(date.take(4))

/**
 * 작품 카드: 위는 그 편지의 그림, 아래는 그 문장 (원문 · 번역) · 그림 이름 · 서명. 테두리는 E3 에서 산 것.
 * 기기 안에서 그림 파일을 만든다 (서버 없음).
 */
fun artCard(ctx: Context, w: Work, l: Letter, m: Message, learn: Lang, read: Lang, format: CardFormat,
            border: io.github.graviton94.todayletters.core.Border, translation: Boolean): Bitmap {
    val W = format.w; val H = format.h
    val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paper = Color.rgb(0xFB, 0xF7, 0xEE)
    val ink = Color.rgb(0x2A, 0x1F, 0x10)
    val soft = Color.rgb(0x76, 0x67, 0x4F)
    c.drawColor(paper)
    val grain = Paint().apply { color = Color.argb(9, 90, 60, 20) }
    for (y in 0 until H step 5) c.drawRect(0f, y.toFloat(), W.toFloat(), y + 1f, grain)

    // 테두리
    val stroke = { col: Int, width: Float, inset: Float ->
        c.drawRect(inset + width / 2, inset + width / 2, W - inset - width / 2, H - inset - width / 2,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width; color = col })
    }
    val b0 = W * 0.025f
    when (border) {
        io.github.graviton94.todayletters.core.Border.PLAIN -> stroke(Color.rgb(0xCD, 0xBE, 0x9F), 3f, b0)
        io.github.graviton94.todayletters.core.Border.POSTMARK -> { stroke(Color.rgb(0x3D, 0x4C, 0x6E), 6f, b0); stroke(Color.rgb(0x3D, 0x4C, 0x6E), 3f, b0 + 16f) }
        io.github.graviton94.todayletters.core.Border.GILT -> { stroke(Color.rgb(0xC9, 0xA4, 0x56), 22f, b0); stroke(Color.rgb(0x8A, 0x65, 0x26), 3f, b0 + 30f) }
        io.github.graviton94.todayletters.core.Border.PAINT -> {
            val bw = 30f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = bw
                shader = android.graphics.LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                    intArrayOf(Color.rgb(0x9F, 0xB3, 0xC8), Color.rgb(0xE8, 0xD9, 0xA8), Color.rgb(0xC7, 0x9A, 0x8A)), null, android.graphics.Shader.TileMode.CLAMP)
            }
            c.drawRect(b0 + bw / 2, b0 + bw / 2, W - b0 - bw / 2, H - b0 - bw / 2, p)
        }
    }

    // 그림
    val pad = W * 0.09f
    val artRect = RectF(pad, pad, W - pad, pad + H * format.art)
    l.plate?.image?.takeIf { it.isNotEmpty() }?.let { file ->
        runCatching { ctx.assets.open("plates/$file").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { art ->
            val scale = maxOf(artRect.width() / art.width, artRect.height() / art.height)
            val sw = (artRect.width() / scale).toInt(); val sh = (artRect.height() / scale).toInt()
            val sx = (art.width - sw) / 2; val sy = (art.height - sh) / 2
            c.drawBitmap(art, Rect(sx, sy, sx + sw, sy + sh), artRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    fun font(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: fallback
    val display = font(R.font.display_italic, Typeface.SERIF)
    val body = if (read == Lang.KO) font(R.font.kr_regular, Typeface.SERIF) else font(R.font.text_regular, Typeface.SERIF)
    val learnFace = if (learn == Lang.KO) font(R.font.kr_semibold, Typeface.SERIF) else display
    val width = (W - pad * 2).toInt()

    // 문장: 남은 자리에 맞춰 글자 크기를 줄인다
    val footH = H * 0.09f
    var top = artRect.bottom + H * 0.035f
    val room = H - pad - footH - top
    val q = if (learn == Lang.FR) "« ${Breaks(m.text[learn])} »" else "“${Breaks(m.text[learn])}”"
    val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = learnFace; color = ink }
    val rp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; color = soft }
    var size = W * 0.05f
    var ql: StaticLayout; var rl: StaticLayout?
    while (true) {
        lp.textSize = size; rp.textSize = size * 0.56f
        ql = staticLayout(q, lp, width, 1.2f)
        rl = if (translation && read != learn) staticLayout(Breaks(m.text[read]), rp, width, 1.45f) else null
        if (ql.height + (rl?.height?.plus(size * 0.6f) ?: 0f) <= room || size <= W * 0.026f) break
        size -= 2f
    }
    c.save(); c.translate(pad, top); ql.draw(c); c.restore()
    top += ql.height + size * 0.6f
    rl?.let { c.save(); c.translate(pad, top); it.draw(c); c.restore() }

    // 아래: 그림 이름 · 소장처 (왼쪽), 서명 (오른쪽)
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; textSize = W * 0.022f; color = soft }
    val fy = H - pad - footH * 0.25f
    l.plate?.let { pl ->
        c.drawText(listOf(Breaks(pl.title[read]), pl.date).filter { it.isNotBlank() }.joinToString(" · "), pad, fy - small.textSize * 1.4f, small)
        c.drawText(pl.collection.substringBefore(" · "), pad, fy, small)
    }
    val sign = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = W * 0.03f; color = ink; textAlign = Paint.Align.RIGHT }
    c.drawText("— ${w.sender}, ${monthLine(l.date, read)}", W - pad, fy, sign)
    val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(R.font.caps, Typeface.SERIF); textSize = W * 0.016f; color = Color.argb(140, 0x76, 0x67, 0x4F); letterSpacing = 0.2f; textAlign = Paint.Align.RIGHT }
    c.drawText(ctx.getString(R.string.app_name).uppercase(), W - pad, fy - sign.textSize * 1.3f, mark)
    return out
}

/** 그림 파일을 공유 시트로. */
fun shareImage(ctx: Context, bmp: Bitmap, name: String, text: String) {
    val dir = File(ctx.cacheDir, "postcards").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "$name.jpg")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.card_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 작품 카드로 보내기 (E5): 미리 보기 · 크기 셋 · 테두리 · 번역 줄 넣기 · 공유. */
@androidx.compose.runtime.Composable
fun ArtCardSheet(s: AppState, w: Work, l: Letter, m: Message, onClose: () -> Unit) {
    val p = io.github.graviton94.todayletters.design.Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val view = s.room(w.series.id)
    var format by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CardFormat.PORTRAIT) }
    var translation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var borders by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    s.version
    val border = io.github.graviton94.todayletters.core.Border.entries.firstOrNull { it.name == s.store.border } ?: io.github.graviton94.todayletters.core.Border.PLAIN
    val bmp = androidx.compose.runtime.remember(format, translation, border, l.id, m) {
        runCatching { artCard(ctx, w, l, m, view.learn, view.read, format, border, translation) }.getOrNull()
    }
    androidx.activity.compose.BackHandler(onBack = onClose)
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFE6DCC8)).statusBarsPadding().navigationBarsPadding(),
    ) {
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(end = io.github.graviton94.todayletters.design.Tokens.Space.s4),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(androidx.compose.ui.res.stringResource(R.string.close), onClick = onClose) {
                androidx.compose.material3.Text("×", style = io.github.graviton94.todayletters.design.Type.title, color = p.ink)
            }
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.card_title), style = io.github.graviton94.todayletters.design.Type.heading.ui(), color = p.ink)
        }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center) {
            bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, androidx.compose.ui.Modifier.fillMaxSize().shadow(12.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
        }
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
            listOf(CardFormat.PORTRAIT to R.string.card_portrait, CardFormat.SQUARE to R.string.card_square, CardFormat.STORY to R.string.card_story).forEach { (f, label) ->
                Capsule(androidx.compose.ui.res.stringResource(label), if (format == f) CapsuleKind.FILLED else CapsuleKind.OUTLINE) { format = f }
            }
        }
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.card_border, androidx.compose.ui.res.stringResource(borderName(border))),
                style = io.github.graviton94.todayletters.design.Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                color = p.inkSoft, modifier = androidx.compose.ui.Modifier.pressable { borders = true }.padding(6.dp))
            if (view.read != view.learn) androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(if (translation) R.string.card_tr_on else R.string.card_tr_off),
                style = io.github.graviton94.todayletters.design.Type.small.ui(), color = p.inkSoft,
                modifier = androidx.compose.ui.Modifier.pressable { translation = !translation }.padding(6.dp))
        }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
            Primary(androidx.compose.ui.res.stringResource(R.string.card_share)) {
                bmp?.let { shareImage(ctx, it, "card-${l.id}", "“${Breaks(m.text[view.learn])}” — ${w.fullName}, ${l.date.take(4)}") }
            }
        }
    }
    if (borders) BorderSheet(s, w) { borders = false }
}
