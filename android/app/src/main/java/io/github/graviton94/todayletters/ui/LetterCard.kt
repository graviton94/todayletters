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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.github.graviton94.todayletters.BuildConfig
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Letter
import io.github.graviton94.todayletters.core.LetterCard
import io.github.graviton94.todayletters.core.Message
import io.github.graviton94.todayletters.data.AssetFiles
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.data.recipientOf
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.io.File

/**
 * 편지 카드 (시안 Gbowc2Rqd6VJJpfmMPP6jC 2, 사용자 고침: 말줄임 없이 문장 전체 · 카드 높이는 내용만큼).
 * 너비 1080px, 위에 그 편지의 그림 (4:3), 아래 원문 전체 · 앱 글자 언어의 번역 · 꼬리 줄 · 그림의 작가 · 제목 · 연도.
 * 밝은 종이 · 어두운 두 가지 (앱 테마). 기기 안에서 그린다 (서버 없음). 글 · 자르기는 core [LetterCard].
 */
private class CardInk(val paper: Int, val ink: Int, val soft: Int, val accent: Int, val rule: Int)

private val CARD_LIGHT = CardInk(Color.rgb(0xF7, 0xF1, 0xE6), Color.rgb(0x23, 0x1B, 0x16), Color.rgb(0x6E, 0x65, 0x5A), Color.rgb(0x8A, 0x73, 0x50), Color.rgb(0xDD, 0xD2, 0xC5))
private val CARD_DARK = CardInk(Color.rgb(0x1E, 0x17, 0x12), Color.rgb(0xEF, 0xE4, 0xD2), Color.rgb(0xA9, 0x99, 0x8A), Color.rgb(0xD9, 0xAE, 0x55), Color.rgb(0x3A, 0x2F, 0x27))

/** 카드 그림 한 장. [ui] 는 앱 글자 언어 (번역 줄 · 날짜 · 이름). */
fun letterCard(ctx: Context, w: Work, l: Letter, m: Message, learn: Lang, ui: Lang, dark: Boolean): Bitmap {
    val c0 = if (dark) CARD_DARK else CARD_LIGHT
    val W = LetterCard.WIDTH
    val pad = 72f
    val width = (W - pad * 2).toInt()
    fun font(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: fallback
    val learnFace = if (learn == Lang.KO) font(R.font.kr_regular, Typeface.SERIF) else font(R.font.text_regular, Typeface.SERIF)
    val uiFace = if (ui == Lang.KO) font(R.font.kr_regular, Typeface.SERIF) else font(R.font.text_regular, Typeface.SERIF)

    // 그림: 너비 1080 을 채울 만큼만 줄여서 읽는다
    val art = l.plate?.image?.takeIf { it.isNotEmpty() }?.let { file -> runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        AssetFiles.open(ctx, "plates/$file").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= W && bounds.outHeight / (sample * 2) >= W / LetterCard.RATIO) sample *= 2
        AssetFiles.open(ctx, "plates/$file").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull() }
    val artH = if (art != null) (W / LetterCard.RATIO).toInt() else 0

    // 글: 원문 전체 (말줄임 없음) · 번역 · 꼬리 줄 · 그림 줄
    val original = m.text[learn]
    val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = learnFace; color = c0.ink; textSize = LetterCard.originalPx(LetterCard.clean(original).length) }
    val ol = cardLayout(original, lp, width, 1.32f)
    val tr = LetterCard.translation(original, m.text[ui], learn, ui)
    val rp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = uiFace; color = c0.soft; textSize = 34f }
    val rl = tr?.let { cardLayout(it, rp, width, 1.45f) }

    val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = uiFace; color = c0.accent; textSize = 27f; letterSpacing = 0.03f }
    val footLeft = LetterCard.footer(w.name[ui], w.recipientOf(l)[ui], l.date, ui)
    val appName = ctx.getString(R.string.app_name)
    val appW = foot.measureText(appName)
    val leftL = cardLayout(footLeft, foot, width, 1.3f)
    // 꼬리 줄 왼쪽이 길어 앱 이름과 겹치면 앱 이름은 다음 줄 오른쪽에
    val oneRow = leftL.lineCount == 1 && leftL.getLineWidth(0) + 32f + appW <= width
    val creditText = l.plate?.let { pl ->
        LetterCard.credit(pl.credit.ifBlank { if (pl.kind.isEmpty()) w.fullName else "" }, pl.title[ui], pl.date)
    }.orEmpty()
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = uiFace; color = c0.soft; textSize = 22f }
    val cl = creditText.takeIf { it.isNotEmpty() }?.let { cardLayout(it, small, width, 1.3f) }

    val gapTop = 64f
    val gapTr = 26f
    val gapRule = 52f
    val gapFoot = 30f
    val footLine = foot.fontSpacing
    val footH = leftL.height + if (oneRow) 0f else footLine
    val gapCredit = 14f
    val bottom = 56f
    val H = (artH + gapTop + ol.height + (rl?.let { gapTr + it.height } ?: 0f) + gapRule + gapFoot + footH +
        (cl?.let { gapCredit + it.height } ?: 0f) + bottom).toInt()

    val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(c0.paper)
    if (!dark) {
        // 종이 결: 가는 가로줄 (엽서 · 작품 카드와 같은 결)
        val grain = Paint().apply { color = Color.argb(8, 90, 60, 20) }
        for (y in artH until H step 5) c.drawRect(0f, y.toFloat(), W.toFloat(), y + 1f, grain)
    }
    art?.let { a ->
        val k = LetterCard.crop(a.width, a.height)
        c.drawBitmap(a, Rect(k.left, k.top, k.right, k.bottom), RectF(0f, 0f, W.toFloat(), artH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    var top = artH + gapTop
    c.save(); c.translate(pad, top); ol.draw(c); c.restore()
    top += ol.height
    rl?.let { top += gapTr; c.save(); c.translate(pad, top); it.draw(c); c.restore(); top += it.height }
    top += gapRule
    c.drawRect(pad, top, W - pad, top + 2f, Paint().apply { color = c0.rule })
    top += gapFoot
    c.save(); c.translate(pad, top); leftL.draw(c); c.restore()
    val appY = if (oneRow) top - foot.fontMetrics.ascent else top + leftL.height - foot.fontMetrics.ascent
    c.drawText(appName, W - pad - appW, appY, foot)
    top += footH
    cl?.let { top += gapCredit; c.save(); c.translate(pad, top); it.draw(c); c.restore() }
    return out
}

private fun cardLayout(text: String, paint: TextPaint, width: Int, spacing: Float): StaticLayout {
    val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, spacing)
        .setIncludePad(false)
    if (Build.VERSION.SDK_INT >= 33) b.setLineBreakConfig(
        android.graphics.text.LineBreakConfig.Builder().setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build(),
    )
    return b.build()
}

/** 공유 글: 한 줄 + 플레이 스토어 주소. */
fun letterCardText(ctx: Context): String = LetterCard.clean(ctx.getString(R.string.letter_card_text, LetterCard.storeUrl(BuildConfig.APPLICATION_ID)))

/** 카드 PNG 를 공유 시트로 (cache/postcards, res/xml/file_paths.xml). */
fun shareLetterCard(ctx: Context, bmp: Bitmap, name: String) {
    runCatching {
        val dir = File(ctx.cacheDir, "postcards").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, letterCardText(ctx))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.letter_card_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 카드 그림을 지금 테마 (밝은 · 어두운) 와 앱 글자 언어로 그린다. 못 그리면 null. */
@Composable
fun rememberLetterCard(s: AppState, w: Work, l: Letter, m: Message): Bitmap? {
    val ctx = LocalContext.current
    val dark = Ink.isDark
    val ui = uiLang()
    val learn = s.room(w.series.id).learn
    return remember(l.id, m, learn, ui, dark) { runCatching { letterCard(ctx, w, l, m, learn, ui, dark) }.getOrNull() }
}

/** 카드 그림 보여 주기 (미리 보기 · 화면 사진). */
@Composable
fun LetterCardImage(bmp: Bitmap?, modifier: Modifier = Modifier) {
    bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.letter_card_title), modifier.shadow(10.dp), contentScale = ContentScale.Fit) }
}

/** 편지 카드 미리 보기 · ‘카드로 보내기’. 편지 방 문장 길게 누르기 · 내 구절 · 봉인한 편지에서 연다. */
@Composable
fun LetterCardSheet(s: AppState, w: Work, l: Letter, m: Message, onClose: () -> Unit) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val bmp = rememberLetterCard(s, w, l, m)
    androidx.activity.compose.BackHandler(onBack = onClose)
    // 아래 화면 (편지 방 · 봉인) 으로 누르기가 새지 않게 막는다
    Column(Modifier.fillMaxSize().background(p.hide).pointerInput(Unit) { detectTapGestures { } }.statusBarsSafe().navigationBarsSafe()) {
        Row(Modifier.fillMaxWidth().padding(end = Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
            IconButton(stringResource(R.string.close), onClick = onClose) {
                Text("×", style = Type.title, color = p.ink)
            }
            Text(stringResource(R.string.letter_card_title), style = Type.heading.ui(), color = p.ink)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            LetterCardImage(bmp, Modifier.fillMaxSize())
        }
        Box(Modifier.padding(Tokens.Space.s4)) {
            Primary(stringResource(R.string.letter_card_send), enabled = bmp != null) { bmp?.let { shareLetterCard(ctx, it, "letter-${l.id}") } }
        }
    }
}

/**
 * 길게 누르기 (편지 방 문장 → 카드): 안의 글이 누르기 · 낱말 뜻을 먼저 받아도 길게 누르면 이쪽이 가져간다.
 * 길게 누른 뒤의 손 떼기는 안쪽에 넘기지 않는다 (낱말 뜻이 같이 열리지 않게). 화면 읽기에는 ‘길게 누르기’ 동작 이름으로.
 */
@Composable
fun Modifier.longPressCard(label: String, onLong: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val act = rememberUpdatedState(onLong)
    return this
        .semantics { onLongClick(label) { act.value(); true } }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var gone = false
                    while (!gone) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull { it.id == down.id }
                        gone = ch == null || !ch.pressed || ch.isConsumed || (ch.position - down.position).getDistance() > slop
                    }
                    true
                }
                if (ended == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    act.value()
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                }
            }
        }
}
