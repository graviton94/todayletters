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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * 오늘 (시리즈 안 첫 이름표): 이어 읽기 · 오늘의 편지 한 장 (채운 단추 하나) · 오늘의 할 일 셋 · 일요일 낭독회 · 이달의 전시.
 * 그림 없이 활자와 여백만 (그림은 편지가 도착할 때 · 봉인 · 갤러리에서).
 */
@Composable
fun Inbox(s: AppState) {
    val p = Ink.palette
    s.version
    // 들어올 때 한 번 정산 (혼자 읽기가 올랐거나 새 이정표가 있으면 보상 순간)
    LaunchedEffect(Unit) { s.settle() }
    val work = s.currentWork
    val id = work.series.id
    val learn = s.seriesSettings(id).learn
    val modes = Plays.inLetter.filter { it in s.seriesSettings(id).modes }
    val open = s.openable(id)
    val letters = work.chapters.flatMap { it.letters }
    val current = open.firstOrNull { (c, l) -> !s.progress(id, c, l.id).done }
    val focus = if (current == null) s.focusLetter(id) else null
    val due = s.dueCards().count { it.key.startsWith("$id:") }

    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5),
        ) {
            StreakBlock(s, work)
            Hair()
            // 오늘의 편지
            val (c, l) = current ?: focus ?: (null to null)
            if (c != null && l != null) {
                val pr = s.progress(id, c, l.id)
                val idx = letters.indexOf(l) + 1
                val g = s.letterGrowth(id, c, l)
                val room = Route.Letter(id, work.chapters.indexOfFirst { it.id == c } + 1, work.chapters.first { it.id == c }.letters.indexOf(l) + 1, Route.Inbox)
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Caps("${work.kit.place.substringBefore(" ")} · ${stringResource(R.string.letter_n, roman(idx))}".uppercase(), p.giltText, small = true, decorative = true)
                    Text(l.status?.get(uiLang()) ?: dateLine(l.date, l.place), style = Type.title.ui(), color = p.ink)
                    Text(dateLine(l.date, l.place) + if (pr.done) " · " + stringResource(R.string.focus_alone, g.known, g.total) else "", style = Type.small.ui(), color = p.inkSoft)
                }
                if (pr.done) {
                    Bar(g.pct / 100f)
                    // 첫 문장: 아직 혼자 못 읽는 낱말에 점선 밑줄
                    val first = l.messages.first().text[learn]
                    val cm = s.cardMap()
                    val marks = wordMarks(first, l.words.map { it.text[learn] })
                    val text = androidx.compose.ui.text.buildAnnotatedString {
                        append(first)
                        marks.forEach { (range, k) ->
                            if (!io.github.graviton94.todayletters.core.Growth.known(cm[s.cardKey(id, c, l.id, k)]))
                                addStyle(androidx.compose.ui.text.SpanStyle(color = p.giltText, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), range.first, range.last + 1)
                        }
                    }
                    Text(text, style = Type.target.of(learn), color = p.ink, maxLines = 3)
                } else {
                    Text(l.messages.first().text[learn], style = Type.target.of(learn), color = p.ink, maxLines = 2)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when {
                        !pr.done -> Primary(if (pr.shown < l.messages.size) stringResource(R.string.today_open)
                            else modes.firstOrNull { it !in pr.replied }?.let { stringResource(R.string.room_next, stringResource(modeLabel(it))) } ?: stringResource(R.string.room_done_today)) { s.open(id, c, l, Route.Inbox) }
                        s.isSealed(id, c, l) -> Primary(stringResource(R.string.focus_sealed)) { s.go(Route.Seal(room)) }
                        g.known == g.total && g.total > 0 && !s.isShadowed(id, c, l) -> Primary(stringResource(R.string.focus_shadow)) { s.go(Route.Shadow(room)) }
                        due > 0 -> Primary(stringResource(R.string.focus_review, due)) { s.go(Route.Session()) }
                        else -> g.nextDue?.let { d -> Text(if (d <= 1) stringResource(R.string.focus_due_tomorrow) else stringResource(R.string.focus_due_in, d), style = Type.small.ui(), color = p.inkSoft) }
                    }
                }
                // 편지 목록을 위로 (v18): 왼쪽 오늘의 편지 다시 읽기 · 오른쪽 전체 편지 목록
                LetterLinks(s, id, work, reread = if (pr.shown > 0) ({ s.open(id, c, l, Route.Inbox) }) else null)
            } else {
                Text(stringResource(if (s.doneCount(id) >= letters.size) R.string.chapter_end_next else R.string.today_all_done), style = Type.heading.ui(), color = p.ink)
                if (due > 0) Primary(stringResource(R.string.focus_review, due)) { s.go(Route.Session()) }
                LetterLinks(s, id, work, reread = null)
            }
            // 갑자기 한 문제 (F7): 오래전 ‘내 것’ 낱말, 하루 한 번
            s.surprise()?.let { c -> SurpriseCard(s, c) }
            // 오늘의 할 일
            Column {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(stringResource(R.string.quests_title), style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.quests_bonus), style = Type.small.ui(), color = p.giltText)
                }
                s.quests.forEach { q ->
                    val n = s.questCount(q).coerceAtMost(q.goal)
                    val done = s.questDone(q)
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(enabled = !done) { questGo(s, q) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Box(Modifier.size(16.dp).then(if (done) Modifier.background(p.giltText, CircleShape) else Modifier.border(1.5.dp, p.hideInk, CircleShape)))
                        Text(stringResource(questLabel(q), q.goal), style = Type.body.ui().copy(textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                            color = if (done) p.inkSoft else p.ink, modifier = Modifier.weight(1f))
                        Text(if (done) "✓" else "$n / ${q.goal}", style = Type.small, color = p.inkSoft)
                    }
                    Hair()
                }
            }
            // 일요일 낭독회 (일요일, 또는 이번 주 녹음이 있으면)
            val sunday = io.github.graviton94.todayletters.core.Recital.isSunday(s.today)
            if (sunday || (!s.recitalDone && s.weekTakes().isNotEmpty())) {
                Row(Modifier.fillMaxWidth().pressable { s.go(Route.Recital) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.recital_title), style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(stringResource(if (s.recitalDone) R.string.recital_done else if (sunday) R.string.recital_today else R.string.recital_sunday), style = Type.small.ui(), color = p.inkSoft)
                    }
                    Text("›", style = Type.heading, color = p.inkSoft)
                }
            }
            // 이달의 전시
            s.exhibition(id)?.let { e ->
                val ep = s.exhibitionPieces(id)
                Row(Modifier.fillMaxWidth().background(p.hide).pressable { s.go(Route.Exhibition) }.padding(Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    AssetImage((ep.firstOrNull { s.owns(id, it) } ?: ep.firstOrNull())?.image.orEmpty(), Modifier.size(44.dp), sample = 8)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.expo_line, e.title[uiLang()]), style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(stringResource(R.string.expo_meta, s.daysLeftInMonth, ep.count { s.owns(id, it) }, ep.size), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                    Text("›", style = Type.heading, color = p.inkSoft)
                }
            }
            // 목차 · 다음 편지
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val upcoming = letters.firstOrNull { l -> open.none { it.second == l } }
                val days = upcoming?.let { s.daysUntil(id, it.id) }
                Text(stringResource(R.string.next_title) + " · " + when { days == null -> stringResource(R.string.next_none); days <= 1 -> stringResource(R.string.letter_eta); else -> stringResource(R.string.next_in, days) },
                    style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun questLabel(q: io.github.graviton94.todayletters.core.Quest) = when (q) {
    io.github.graviton94.todayletters.core.Quest.SHADOW_PASS -> R.string.quest_shadow
    io.github.graviton94.todayletters.core.Quest.REREAD -> R.string.quest_reread
    io.github.graviton94.todayletters.core.Quest.DICTATION_RUN -> R.string.quest_dictation
    io.github.graviton94.todayletters.core.Quest.REVIEW_DONE -> R.string.quest_review
    io.github.graviton94.todayletters.core.Quest.MEANING_RUN -> R.string.quest_meaning
}

/** 할 일을 누르면 그 일을 하러 간다. */
private fun questGo(s: AppState, q: io.github.graviton94.todayletters.core.Quest) {
    val id = s.current
    val w = s.currentWork
    val done = s.openable(id).filter { (c, l) -> s.progress(id, c, l.id).done }
    val last = done.lastOrNull()
    fun room(c: String, l: io.github.graviton94.todayletters.core.Letter) =
        Route.Letter(id, w.chapters.indexOfFirst { it.id == c } + 1, w.chapters.first { it.id == c }.letters.indexOf(l) + 1, Route.Inbox)
    when (q) {
        io.github.graviton94.todayletters.core.Quest.SHADOW_PASS -> last?.let { (c, l) -> s.go(Route.Shadow(room(c, l))) } ?: s.go(Route.Words)
        io.github.graviton94.todayletters.core.Quest.REREAD -> last?.let { (c, l) -> s.open(id, c, l, Route.Inbox) }
        io.github.graviton94.todayletters.core.Quest.DICTATION_RUN -> s.go(Route.Session(io.github.graviton94.todayletters.core.ReviewKind.DICTATION))
        io.github.graviton94.todayletters.core.Quest.MEANING_RUN -> s.go(Route.Session(io.github.graviton94.todayletters.core.ReviewKind.MEANING))
        io.github.graviton94.todayletters.core.Quest.REVIEW_DONE -> s.go(Route.Session())
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


/** 오늘 화면의 두 단추: 오늘의 편지 다시 읽기 (있을 때) | 전체 편지 목록, 아래에 장마다 읽은 수. */
@Composable
private fun LetterLinks(s: AppState, id: String, work: io.github.graviton94.todayletters.data.Work, reread: (() -> Unit)?) {
    val p = Ink.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (reread != null) OutlineButton(stringResource(R.string.today_reread), Modifier.weight(1f), reread)
            OutlineButton(stringResource(R.string.today_all_letters), Modifier.weight(1f)) { s.go(Route.Series(id)) }
        }
        val counts = work.chapters.mapIndexed { k, ch ->
            stringResource(R.string.today_chapter_count, "${k + 1}", ch.letters.count { s.progress(id, ch.id, it.id).done }, ch.letters.size)
        }
        Text(counts.joinToString("  ·  "), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft,
            modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val p = Ink.palette
    Box(modifier.heightIn(min = Tokens.Size.buttonSm).border(1.dp, p.ink).pressable { onClick() }.padding(horizontal = Tokens.Space.s2),
        contentAlignment = Alignment.Center) {
        Text(text, style = Type.small.ui(), color = p.ink, maxLines = 1)
    }
}

/**
 * 기억 확인 (F7): ‘내 것’이 된 지 오래된 낱말 하나를 툭. 낱말을 보고 뜻을 떠올린 뒤 펼쳐서 스스로 판정.
 * 기억났어요 → 일정 그대로 + 우표 하나, 가물가물 → 두 단계 내려가 내일 다시.
 */
@Composable
private fun SurpriseCard(s: AppState, c: io.github.graviton94.todayletters.core.Card) {
    val p = Ink.palette
    val found = remember(c.key) { s.cardWord(c.key) } ?: return
    val (w, cl, wi) = found
    val view = s.room(w.series.id)
    val word = cl.second.words.getOrNull(wi) ?: return
    var open by remember(c.key) { mutableStateOf(false) }
    val audio = s.narrator.path(w.series.id, cl.first, cl.second.id, "w${wi + 1}_${view.learn.code}")
    Column(Modifier.fillMaxWidth().border(1.dp, p.giltText).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Caps("SOUVENIR", p.giltText, small = true, decorative = true)
        Text(stringResource(R.string.surprise_title), style = Type.small.ui(), color = p.inkSoft)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(word.text[view.learn], style = Type.title.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display).of(view.learn), color = p.ink, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).border(1.dp, p.giltText, CircleShape).pressable { s.narrator.play(audio) }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.giltText, 16.dp) }
        }
        if (!open) Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable { open = true }, contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.surprise_reveal), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.ink)
        } else {
            Text(word.text[view.read], style = Type.heading.of(view.read), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.weight(1f).heightIn(min = Tokens.Size.buttonSm).border(1.dp, p.ink).pressable { s.surpriseDone(c, false) }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.surprise_hazy), style = Type.small.ui(), color = p.ink)
                }
                Box(Modifier.weight(1f)) { Primary(stringResource(R.string.surprise_yes), small = true) { s.surpriseDone(c, true) } }
            }
        }
    }
}
