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
import androidx.compose.runtime.LaunchedEffect
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
    // 들어올 때 한 번 정산 (혼자 읽기가 올랐거나 새 업적이 있으면 보상 순간)
    LaunchedEffect(Unit) { s.settle() }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)).uppercase(), style = Type.caps, color = p.giltText, modifier = Modifier.weight(1f))
                    if (s.streak > 0) Text(stringResource(R.string.streak_line, s.streak), style = Type.small.ui(), color = p.inkSoft)
                }
                Text(stringResource(greet, work.recipient[uiLang()]), style = Type.title.ui(), color = p.ink)
            }
            IconButton(stringResource(R.string.help), onClick = { s.coachAgain("inbox") }) { HelpGlyph(p.ink) }
            IconButton(stringResource(R.string.settings), onClick = { s.settingsOpen = true }) { Gear(p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            val letters = work.chapters.flatMap { it.letters }
            val focus = if (current == null) s.focusLetter(id) else null
            // 오늘의 편지: 읽는 중인 편지 → 그날의 단계 · 다 읽었으면 → 이 편지를 얼마나 혼자 읽나 (북극성)
            Slip(seed = 21, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    if (current != null) {
                        val (c, l) = current
                        val pr = s.progress(id, c, l.id)
                        val idx = letters.indexOf(l) + 1
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
                    } else if (focus != null) {
                        FocusLetter(s, id, focus.first, focus.second, letters.indexOf(focus.second) + 1, due.size)
                    } else if (s.doneCount(id) >= letters.size) {
                        // 다 읽음: ‘내일 도착’ 대신 장 끝 카드 (다음 장은 준비 중)
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Text(stringResource(R.string.chapter_end_caps), style = Type.caps, color = Color(0xFF7A5A20), modifier = Modifier.clearAndSetSemantics { })
                            Text(stringResource(R.string.chapter_end_title, work.chapters.last().title[uiLang()]), style = Type.heading.ui(), color = p.slipInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            val last = work.chapters.last().letters
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                last.forEachIndexed { k, l -> PlateThumb(l.plate?.image.orEmpty(), true, Modifier.size(46.dp).graphicsLayer { rotationZ = (k - last.size / 2) * 3f }) }
                            }
                            Text(stringResource(R.string.chapter_end_stats, last.size, last.sumOf { it.words.size }, last.count { it.plate != null }), style = Type.small.ui(), color = p.slipSoft)
                            Box(Modifier.fillMaxWidth().height(1.dp).background(p.slipInk.copy(alpha = 0.15f)))
                            Text(stringResource(R.string.chapter_end_next), style = Type.small.ui(), color = p.slipInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).background(p.slipInk).pressable { s.go(Route.Words) }, contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.chapter_end_review, due.size), style = Type.body.ui().copy(fontSize = 15.sp), color = p.slip)
                            }
                            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).border(1.dp, p.slipInk.copy(alpha = 0.5f)).pressable { s.go(Route.Chapter(id, work.chapters.size)) }, contentAlignment = Alignment.Center) {
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
            // 오늘의 복습 · 다음 편지
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
                // 다음 편지: 기다림도 고리의 일부 (펜팔처럼)
                val upcoming = letters.firstOrNull { l -> open.none { it.second == l } }
                val waitingNow = open.any { (c, l) -> !s.progress(id, c, l.id).done && s.progress(id, c, l.id).shown == 0 }
                Column(
                    Modifier.width(140.dp).background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
                ) {
                    Text(stringResource(R.string.next_title), style = Type.small.ui(), color = p.giltText)
                    val days = upcoming?.let { s.daysUntil(id, it.id) }
                    Text(
                        when {
                            waitingNow -> stringResource(R.string.next_arrived)
                            days == null -> stringResource(R.string.next_none)
                            days <= 1 -> stringResource(R.string.letter_eta)
                            else -> stringResource(R.string.next_in, days)
                        },
                        style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink,
                    )
                    upcoming?.let { Text(dateLine(it.date, it.place), style = Type.small.ui(), color = p.inkSoft, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * 오늘의 북극성: 다 읽은 편지를 얼마나 혼자 읽나. 편지 낱말 가운데 떠올리기 단계 이상인 비율을 고리로,
 * 첫 문장에서 아직 혼자 못 읽는 낱말에 밑줄. 낱말은 복습(늘어나는 간격)으로만 오르므로 버튼도 그 길로.
 */
@Composable
private fun FocusLetter(s: AppState, id: String, chapter: String, l: io.github.graviton94.todayletters.core.Letter, idx: Int, due: Int) {
    val p = Ink.palette
    val g = s.letterGrowth(id, chapter, l)
    val learn = s.seriesSettings(id).learn
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        Box(contentAlignment = Alignment.BottomCenter) {
            AloneRing(g.pct, 104.dp, p.slipSoft.copy(alpha = 0.25f), Color(0xFFB8913E), p.slipInk)
            Text(stringResource(R.string.focus_ring), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.slipSoft, modifier = Modifier.padding(bottom = 22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text((stringResource(R.string.letter_n, roman(idx)) + " · " + l.place).uppercase(), style = Type.caps, color = Color(0xFF7A5A20), maxLines = 1)
            Text(l.status?.get(uiLang()) ?: dateLine(l.date, l.place), style = Type.heading.ui(), color = p.slipInk)
            Text(stringResource(R.string.focus_alone, g.known, g.total), style = Type.small.ui(), color = p.slipSoft)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.slipInk.copy(alpha = 0.12f)))
    // 첫 문장: 아직 혼자 못 읽는 낱말에 밑줄
    val first = l.messages.first().text[learn]
    val cm = s.cardMap()
    val marks = wordMarks(first, l.words.map { it.text[learn] })
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append("« $first »")
        marks.forEach { (range, k) ->
            if (!io.github.graviton94.todayletters.core.Growth.known(cm[s.cardKey(id, chapter, l.id, k)]))
                addStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF9A3B22), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), range.first + 2, range.last + 3)
        }
    }
    Text(text, style = Type.target.of(learn), color = p.slipInk, maxLines = 3)
    Text(stringResource(R.string.focus_hint), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.slipSoft)
    val room = Route.Letter(id, s.work(id).chapters.indexOfFirst { it.id == chapter } + 1, s.work(id).chapters.first { it.id == chapter }.letters.indexOf(l) + 1, Route.Inbox)
    @Composable fun big(label: String, onClick: () -> Unit) = Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).pressable(onClick = onClick).background(p.slipInk), contentAlignment = Alignment.Center) {
        Text(label, style = Type.body.ui(), color = p.slip)
    }
    when {
        // 봉인됐는데 아직 못 본 편지 → 봉인 화면
        s.isSealed(id, chapter, l) -> big(stringResource(R.string.focus_sealed)) { s.go(Route.Seal(room)) }
        // 혼자 다 읽는데 따라 읽기가 남음 → 봉인까지 한 걸음
        g.known == g.total && g.total > 0 && !s.isShadowed(id, chapter, l) -> big(stringResource(R.string.focus_shadow)) { s.go(Route.Shadow(room)) }
        due > 0 -> big(stringResource(R.string.focus_review, due)) { s.go(Route.Session()) }
        else -> g.nextDue?.let { d ->
            Text(if (d <= 1) stringResource(R.string.focus_due_tomorrow) else stringResource(R.string.focus_due_in, d), style = Type.small.ui(), color = p.slipInk)
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).border(1.dp, p.slipInk.copy(alpha = 0.5f)).pressable { s.open(id, chapter, l, Route.Inbox) }, contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.focus_reread), style = Type.body.ui().copy(fontSize = 15.sp), color = p.slipInk)
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
        listOf(5 to p.correct, 4 to p.correct.copy(alpha = 0.7f), 3 to p.giltText, 2 to p.gilt, 1 to p.gilt.copy(alpha = 0.5f), 0 to p.hair).forEach { (b, c) ->
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

/** 오늘의 일: 편지 · 복습 · 손님. 다 하면 보너스 우표 (엔진 공통). */
@Composable
fun DayTasksCard(s: AppState) {
    val p = Ink.palette
    val tasks = s.dayTasks()
    if (tasks.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tasks_title), style = Type.small.ui(), color = p.giltText, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.tasks_today, io.github.graviton94.todayletters.core.Rewards.todayTotal(s.wallet, s.today)), style = Type.small.ui(), color = p.inkSoft)
        }
        tasks.forEach { t ->
            val label = when (t.kind) { "letter" -> R.string.task_letter; "review" -> R.string.task_review; else -> R.string.task_visitor }
            val gain = when (t.kind) { "letter" -> "+10"; "review" -> "+12"; else -> "+4" }
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).pressable(enabled = !t.done) {
                when (t.kind) { "review" -> s.go(Route.Words); "visitor" -> s.go(Route.Visitor); else -> s.go(Route.Library) }
            }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                CheckMark(t.done, 22.dp)
                Text(stringResource(label), style = Type.body.ui(), color = if (t.done) p.inkSoft else p.ink, modifier = Modifier.weight(1f))
                Text(gain, style = Type.small.copy(fontFamily = Faces.display), color = p.giltText)
            }
        }
        Text(stringResource(R.string.tasks_bonus), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
    }
}
