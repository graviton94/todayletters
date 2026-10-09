package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Memory
import io.github.graviton94.todayletters.core.Plays
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 오늘 (첫 이름표): 오늘의 편지 한 장과 그 편지의 5단계, 오늘의 복습, 이번 주 우표, 갤러리.
 * 큰 버튼은 하나 (이어서 할 일). 나머지는 눌러서 들어가는 카드.
 */
@Composable
fun Inbox(s: AppState) {
    val p = Ink.palette
    s.version
    val work = s.works.first()
    val id = work.series.id
    val modes = Plays.inLetter.filter { it in s.seriesSettings(id).modes }
    val open = s.openable(id)
    val current = open.firstOrNull { (c, l) -> !s.progress(id, c, l.id).done }
    val due = s.dueCards()
    val counts = Memory.counts(s.cards())
    val now = LocalTime.now().hour
    val greet = when { now < 12 -> R.string.greet_morning; now < 18 -> R.string.greet_day; else -> R.string.greet_evening }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1, top = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).uppercase(), style = Type.caps, color = p.giltText)
                Text(stringResource(greet, work.recipient[uiLang()]), style = Type.title.ui(), color = p.ink)
            }
            IconButton(stringResource(R.string.help), onClick = { s.coachAgain("inbox") }) { HelpGlyph(p.ink) }
            IconButton(stringResource(R.string.settings), onClick = { s.settingsOpen = true }) { Gear(p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            // 오늘의 편지
            Slip(seed = 21, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    if (current != null) {
                        val (c, l) = current
                        val pr = s.progress(id, c, l.id)
                        val idx = work.chapters.flatMap { it.letters }.indexOf(l) + 1
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                            Portrait(work.portrait, work.fullName, 52.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(if (pr.shown > 0) R.string.letter_reading else R.string.letter_new) + " · " + stringResource(R.string.letter_n, roman(idx)), style = Type.small.ui(), color = Color(0xFF7A5A20))
                                Text(stringResource(R.string.today_letter_title, work.name[uiLang()]), style = Type.heading.ui(), color = p.slipInk, maxLines = 1)
                                Text(dateLine(l.date, l.place), style = Type.small.ui(), color = p.slipSoft)
                            }
                            Postmark(l.place, dayOf(l.date), l.date.take(4))
                        }
                        val learn = s.seriesSettings(id).learn
                        Text("« " + l.messages.first().text[learn] + " »", style = Type.target.of(learn), color = p.slipInk, maxLines = 2)
                        StepBar(read = pr.shown >= l.messages.size, modes = modes, replied = pr.replied, done = pr.done)
                        val next = when {
                            pr.shown < l.messages.size -> stringResource(R.string.today_open)
                            else -> modes.firstOrNull { it !in pr.replied }?.let { stringResource(R.string.room_next, stringResource(modeLabel(it))) } ?: stringResource(R.string.room_done_today)
                        }
                        Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).pressable { s.open(id, c, l, Route.Inbox) }.background(p.slipInk), contentAlignment = Alignment.Center) {
                            Text(next, style = Type.body.ui(), color = p.slip)
                        }
                    } else if (s.doneCount(id) >= work.chapters.sumOf { it.letters.size }) {
                        // 다 읽음: ‘내일 도착’ 대신 장 끝 카드 (다음 장은 준비 중)
                        val letters = work.chapters.flatMap { it.letters }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Text(stringResource(R.string.chapter_end_caps), style = Type.caps, color = Color(0xFF7A5A20), modifier = Modifier.clearAndSetSemantics { })
                            Text(stringResource(R.string.chapter_end_title), style = Type.heading.ui(), color = p.slipInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                letters.forEachIndexed { k, l -> PlateThumb(l.plate?.image.orEmpty(), true, Modifier.size(46.dp).graphicsLayer { rotationZ = (k - letters.size / 2) * 3f }) }
                            }
                            Text(stringResource(R.string.chapter_end_stats, letters.size, letters.sumOf { it.words.size }, letters.count { it.plate != null }), style = Type.small.ui(), color = p.slipSoft)
                            Box(Modifier.fillMaxWidth().height(1.dp).background(p.slipInk.copy(alpha = 0.15f)))
                            Text(stringResource(R.string.chapter_end_next), style = Type.small.ui(), color = p.slipInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).background(p.slipInk).pressable { s.go(Route.Words) }, contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.chapter_end_review, due.size), style = Type.body.ui().copy(fontSize = 15.sp), color = p.slip)
                            }
                            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).border(1.dp, p.slipInk.copy(alpha = 0.5f)).pressable { s.go(Route.Chapter(id, 1)) }, contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.chapter_end_again), style = Type.body.ui().copy(fontSize = 15.sp), color = p.slipInk)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                            Portrait(work.portrait, work.fullName, 52.dp)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.today_all_done), style = Type.heading.ui(), color = p.slipInk)
                                Text(stringResource(R.string.letter_eta), style = Type.small.ui(), color = p.slipSoft)
                            }
                        }
                    }
                }
            }
            // 오늘의 복습 · 이어 읽기
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Column(
                    Modifier.weight(1f).background(p.leaf).border(Tokens.Stroke.hair, p.hair).pressable { s.go(Route.Words) }.padding(Tokens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
                ) {
                    Text(stringResource(R.string.review_today), style = Type.small.ui(), color = p.giltText)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (s.reviewedToday && due.isEmpty()) "✓" else "${due.size}", style = Type.display.copy(fontFamily = Faces.display), color = p.ink)
                        if (due.isNotEmpty()) Text(stringResource(R.string.review_minutes, (due.size / 4).coerceAtLeast(1)), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    BoxBar(counts)
                }
                Column(
                    Modifier.width(128.dp).background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
                ) {
                    Text(stringResource(R.string.streak_title), style = Type.small.ui(), color = p.giltText)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${s.streak}", style = Type.display.copy(fontFamily = Faces.display), color = p.ink)
                        Text(stringResource(R.string.streak_days), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            WeekStamps(s, work.portrait)
            // 갤러리 미리보기
            val plates = work.chapters.flatMap { c -> c.letters.map { c to it } }
            val got = plates.count { (c, l) -> s.progress(id, c.id, l.id).done }
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(stringResource(R.string.gallery_peek, got, plates.size), style = Type.small.ui(), color = p.giltText)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    plates.forEachIndexed { k, (c, l) ->
                        val done = s.progress(id, c.id, l.id).done
                        val img = l.plate?.image.orEmpty()
                        Box(Modifier.size(92.dp).pressable(enabled = done) { s.go(Route.Artwork(id, k + 1, Route.Letter(id, work.chapters.indexOf(c) + 1, c.letters.indexOf(l) + 1, Route.Gallery))) }) {
                            PlateThumb(img, done, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

/** 편지 하루 5단계 막대: 한 일 금빛 · 지금 할 일 먹색 · 남은 일 흐리게. */
@Composable
fun StepBar(read: Boolean, modes: List<ReplyMode>, replied: Set<ReplyMode>, done: Boolean) {
    val p = Ink.palette
    val next = if (!read) -1 else modes.indexOfFirst { it !in replied }
    val steps = buildList {
        add(R.string.step_read_short to (if (read) 2 else 1))
        modes.forEachIndexed { i, m -> add((when (m) { ReplyMode.MATCH -> R.string.step_match; ReplyMode.ALOUD -> R.string.step_aloud; else -> R.string.step_reply }) to when { m in replied -> 2; i == next -> 1; else -> 0 }) }
        add(R.string.step_plate to (if (done) 2 else if (read && next < 0) 1 else 0))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { (label, st) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(when (st) { 2 -> Color(0xFF8F6A27); 1 -> p.slipInk; else -> p.slipSoft.copy(alpha = 0.3f) }))
                Text(stringResource(label), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (st > 0) p.slipInk else p.slipSoft, maxLines = 1)
            }
        }
    }
}

/** 낱말 카드 칸 막대: 기억 · 거의 · 익히는 중 · 새. */
@Composable
fun BoxBar(counts: IntArray) {
    val p = Ink.palette
    val total = counts.sum().coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)) {
        listOf(3 to p.correct, 2 to p.giltText, 1 to p.gilt, 0 to p.hair).forEach { (b, c) ->
            if (counts[b] > 0) Box(Modifier.weight(counts[b].toFloat() / total).fillMaxSize().background(c))
        }
        if (counts.sum() == 0) Box(Modifier.weight(1f).fillMaxSize().background(p.hair))
    }
}

/** 이번 주 우표: 편지를 끝낸 요일엔 초상이 든 우표에 소인, 오늘은 실선, 남은 날은 점선. */
@Composable
fun WeekStamps(s: AppState, portrait: String) {
    val p = Ink.palette
    val done = s.weekDone()
    val today = LocalDate.now().dayOfWeek.value - 1
    val days = stringResource(R.string.week_days).split(",")
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (i in done) Box(Modifier.size(38.dp, 46.dp).background(Color(0xFF6E89A8)).dashed(p.paper).padding(4.dp), contentAlignment = Alignment.Center) {
                    Portrait(portrait, "", 26.dp)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val step = 4.dp.toPx()
                        var y = size.height * 0.3f
                        while (y < size.height * 0.7f) { drawLine(Color(0x8C2A2118), androidx.compose.ui.geometry.Offset(-4f, y + 6f), androidx.compose.ui.geometry.Offset(size.width + 4f, y - 6f), 1.2.dp.toPx()); y += step }
                    }
                } else Box(
                    Modifier.size(38.dp, 46.dp).then(if (i == today) Modifier.border(1.5.dp, p.ink) else Modifier.dashed(p.line)),
                    contentAlignment = Alignment.Center,
                ) { if (i == today) Text(stringResource(R.string.today_short), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.ink) }
                Text(d, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (i == today) p.ink else p.inkSoft)
            }
        }
    }
}

/** 그림 썸네일: 받은 그림은 원래 색, 아직이면 흐린 흑백. */
@Composable
fun PlateThumb(file: String, unlocked: Boolean, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp = androidx.compose.runtime.remember(file) {
        runCatching { ctx.assets.open("plates/$file").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } }.getOrNull()
    }
    if (bmp == null) Box(modifier.background(Ink.palette.hide)) else Image(
        bmp, null, modifier, contentScale = ContentScale.Crop,
        colorFilter = if (unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        alpha = if (unlocked) 1f else 0.35f,
    )
}
