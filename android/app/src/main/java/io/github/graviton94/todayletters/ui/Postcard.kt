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
