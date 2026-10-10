package io.github.graviton94.todayletters.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import io.github.graviton94.todayletters.core.Tier

/**
 * 액자 (v21 3단계 D2 · E3): 레이크스 미술관의 실제 빈 액자 사진 넷(CC0, assets/frames)에 작품을 끼운다.
 * 사진마다 안쪽 창의 자리(비율)를 재 두었고, 작품이 세로인데 액자가 가로면 액자를 90도 돌린다.
 *   SKETCH   얇은 나무 액자          RP-L-534
 *   DRAWING  나무 액자 + 매트        SK-L-6754
 *   PAINTING 도금 조각 액자          RP-L-513
 *   MASTER   장식 금박 액자          RP-L-515
 */
object Frames {
    enum class Kind(val file: String, val l: Float, val t: Float, val r: Float, val b: Float, val sound: String, val source: String) {
        SKETCH("frames/sketch.jpg", 0.0263f, 0.0444f, 0.9717f, 0.9663f, "sketch", "RP-L-534"),
        DRAWING("frames/drawing.jpg", 0.1578f, 0.1379f, 0.8271f, 0.8645f, "drawing", "SK-L-6754"),
        PAINTING("frames/painting.jpg", 0.0501f, 0.0609f, 0.9517f, 0.9434f, "painting", "RP-L-513"),
        MASTER("frames/master.jpg", 0.1749f, 0.2067f, 0.8360f, 0.8065f, "master", "RP-L-515"),
    }

    /** 등급의 기본 액자. 편지 자료(편지의 그림)는 유화 액자. */
    fun kindOf(t: Tier) = when (t) {
        Tier.SKETCH -> Kind.SKETCH
        Tier.DRAWING -> Kind.DRAWING
        Tier.PAINTING, Tier.LETTER -> Kind.PAINTING
        Tier.MASTER -> Kind.MASTER
    }

    private val cache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun load(ctx: Context, path: String, max: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
        ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()

    /** [art] (assets 경로) 를 [kind] 액자에 끼운 그림. 액자가 없으면 작품만. */
    fun framed(ctx: Context, art: String, kind: Kind): Bitmap? {
        val key = "$art|$kind"
        cache.get(key)?.let { return it }
        val pic = load(ctx, art, 1000) ?: return null
        var frame = load(ctx, kind.file, 1000) ?: return pic
        var (l, t, r, b) = listOf(kind.l, kind.t, kind.r, kind.b)
        // 작품과 액자의 방향이 다르면 액자를 시계 방향으로 90도
        if ((pic.height > pic.width) != (frame.height > frame.width)) {
            frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(90f) }, true)
            val (l0, t0, r0, b0) = listOf(l, t, r, b)
            l = 1 - b0; t = l0; r = 1 - t0; b = r0
        }
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val win = RectF(l * out.width, t * out.height, r * out.width, b * out.height)
        // 가운데를 채워 자르기
        val sc = maxOf(win.width() / pic.width, win.height() / pic.height)
        val sw = (win.width() / sc).toInt(); val sh = (win.height() / sc).toInt()
        val sx = (pic.width - sw) / 2; val sy = (pic.height - sh) / 2
        c.drawBitmap(pic, Rect(sx, sy, sx + sw, sy + sh), win, Paint(Paint.FILTER_BITMAP_FLAG))
        // 액자 안쪽 그늘: 위 · 왼쪽이 살짝 어둡게
        val shadeTop = Paint().apply { shader = LinearGradient(0f, win.top, 0f, win.top + win.height() * 0.06f, 0x55000000, 0, Shader.TileMode.CLAMP) }
        c.drawRect(win.left, win.top, win.right, win.top + win.height() * 0.06f, shadeTop)
        val shadeLeft = Paint().apply { shader = LinearGradient(win.left, 0f, win.left + win.width() * 0.05f, 0f, 0x33000000, 0, Shader.TileMode.CLAMP) }
        c.drawRect(win.left, win.top, win.left + win.width() * 0.05f, win.bottom, shadeLeft)
        cache.put(key, out)
        return out
    }
}
