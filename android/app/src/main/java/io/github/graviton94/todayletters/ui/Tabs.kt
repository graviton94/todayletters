package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

private val romans = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
    "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV", "XXV")
fun roman(n: Int) = romans.getOrElse(n - 1) { n.toString() }

@Composable
private fun Page(content: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) { content() }

/** 서재: 모든 작품 표지. */
@Composable
fun LibraryTab(s: AppState) {
    val p = Ink.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_library), s, help = "library", showBack = false, showSettings = true)
        Page {
            s.works.forEach { w ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { s.go(Route.Series(w.series.id)) }.padding(vertical = Tokens.Space.s2),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Portrait(w.portrait, w.fullName, 64.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Caps("Saison I · ${w.years}", small = true)
                        Text(w.title[uiLang()], style = Type.heading.ui(), color = p.ink)
                        Text("${w.name[uiLang()]} → ${w.recipient[uiLang()]} · ${stringResource(langLabel(w.series.original))}", style = Type.small.ui(), color = p.inkSoft)
                        if (w.credit.isNotEmpty()) Text(w.credit, style = Type.signature.copy(fontSize = Tokens.Text.small), color = p.inkSoft)
                    }
                }
                Hair()
            }
            Text(stringResource(R.string.library_next), style = Type.small.ui(), color = p.inkSoft)
        }
    }
}

/**
 * 단어장: 받은 편지의 낱말. 한 줄에 낱말 · 뜻 (누르면 발음). 위에 찾기, 아래로 20개씩 더 보기.
 * 같은 낱말은 한 번만 (처음 나온 편지 기준).
 */
@Composable
fun WordListScreen(s: AppState) {
    val p = Ink.palette
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(20) }
    data class Entry(val learn: String, val read: String, val ipa: String, val date: String, val audio: String, val lang: io.github.graviton94.todayletters.core.Lang, val readLang: io.github.graviton94.todayletters.core.Lang)
    val all = remember(s.version) {
        s.works.flatMap { w ->
            val id = w.series.id
            val room = s.room(id)
            w.chapters.flatMap { c ->
                c.letters.filter { s.progress(id, c.id, it.id).shown > 0 }.flatMap { l ->
                    l.words.mapIndexed { i, word ->
                        Entry(word.text[room.learn], word.text[room.read], if (room.learn == w.series.original) word.ipa else "", l.date,
                            s.narrator.path(id, c.id, l.id, "w${i + 1}_${room.learn.code}"), room.learn, room.read)
                    }
                }
            }
        }.distinctBy { it.learn.lowercase() }
    }
    fun norm(x: String) = java.text.Normalizer.normalize(io.github.graviton94.todayletters.core.Breaks.plain(x).lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    val shown = if (query.isBlank()) all else all.filter { norm(it.learn).contains(norm(query)) || norm(it.read).contains(norm(query)) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.words_search), s, help = null, showBack = true, showSettings = false)
        Column(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(horizontal = Tokens.Space.s3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                    drawCircle(p.inkSoft, size.width * 0.32f, androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.width * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    drawLine(p.inkSoft, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.width * 0.66f), androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.width * 0.95f), 1.5.dp.toPx())
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.words_search), style = Type.body.ui(), color = p.hideInk)
                    androidx.compose.foundation.text.BasicTextField(
                        query, { query = it; limit = 20 }, singleLine = true,
                        textStyle = Type.body.ui().copy(color = p.ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(p.giltText),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(stringResource(R.string.words_count, all.size), style = Type.small.ui(), color = p.inkSoft)
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Space.s5)) {
            items(shown.take(limit).size) { k ->
                val e = shown[k]
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable { s.narrator.play(e.audio) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Text(e.learn, style = Type.body.of(e.lang), color = p.ink, maxLines = 1)
                    if (e.ipa.isNotEmpty()) Text("[${e.ipa}]", style = Type.small, color = p.hideInk, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(e.read, style = Type.small.of(e.readLang), color = p.inkSoft, maxLines = 1)
                }
                Hair()
            }
            item {
                when {
                    shown.isEmpty() -> Text(stringResource(if (all.isEmpty()) R.string.words_note else R.string.words_none), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(vertical = Tokens.Space.s4))
                    shown.size > limit -> Box(Modifier.padding(vertical = Tokens.Space.s4)) { Secondary(stringResource(R.string.words_more, shown.size - limit)) { limit += 20 } }
                }
            }
        }
    }
}

/** 화첩: 다 읽은 편지의 그림. */
@Composable
fun GalleryTab(s: AppState) {
    val p = Ink.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_gallery), s, help = null, showBack = false, showSettings = true)
        Page {
            var any = false
            s.works.forEach { w ->
                val id = w.series.id
                w.chapters.forEachIndexed { ci, c ->
                    c.letters.forEachIndexed { li, l ->
                        val plate = l.plate ?: return@forEachIndexed
                        if (!s.progress(id, c.id, l.id).done) return@forEachIndexed
                        any = true
                        val room = Route.Letter(id, ci + 1, li + 1)
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.go(Route.Artwork(id, li + 1, room)) }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            PlateImage(plate.image, Modifier.fillMaxWidth().aspectRatio(1.4f))
                            Text(plate.title[s.app.read], style = Type.label.of(s.app.read), color = p.ink)
                            Text("${plate.date} · ${plate.collection}", style = Type.small, color = p.inkSoft)
                        }
                    }
                }
            }
            if (!any) Text(stringResource(R.string.gallery_empty), style = Type.body.ui(), color = p.inkSoft)
        }
    }
}

/** 작품 표지: 소개 · 진도 · 목차 · ⚙ 작품 설정. */
@Composable
fun SeriesCover(s: AppState, id: String) {
    val p = Ink.palette
    val w = s.work(id)
    s.version
    Column(Modifier.fillMaxSize()) {
        TopBar(w.sender, s, help = "series", showBack = true, showSettings = false) {
            IconButton(stringResource(R.string.series_settings), onClick = { s.go(Route.SeriesSettings(id)) }) { Gear(p.ink) }
        }
        Page {
            Caps("Saison I · Table des matières")
            Text(w.title[w.series.original], style = Type.display, color = p.ink)
            Text(w.title[s.app.read], style = Type.body.of(s.app.read), color = p.inkSoft)
            Rule()
            w.chapters.forEachIndexed { i, c ->
                val done = c.letters.count { s.progress(id, c.id, it.id).done }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.Button) { s.go(Route.Chapter(id, i + 1)) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
                ) {
                    Text(roman(i + 1), style = Type.numeral, color = p.giltText)
                    Column(Modifier.weight(1f)) {
                        Text("${c.title[w.series.original]} · ${c.title[s.app.read]}", style = Type.label.of(s.app.read), color = p.ink)
                        Text(stringResource(R.string.chapter_meta, c.letters.size, done), style = Type.small.ui(), color = p.inkSoft)
                    }
                    if (c.free) Text(stringResource(R.string.free), style = Type.small.ui(), color = p.onFill, modifier = Modifier.background(p.fill).padding(horizontal = Tokens.Space.s2))
                }
                Hair()
            }
        }
    }
}

/** 챕터: 그림 표지 + 편지의 길 (세로 선 위에 편지마다 마디). 다 읽은 편지는 그림이 붙고, 앞으로 올 편지는 흐리게. */
@Composable
fun ChapterScreen(s: AppState, r: Route.Chapter) {
    val p = Ink.palette
    val w = s.work(r.series)
    val c = w.chapters[r.chapter - 1]
    val openable = s.openable(r.series).map { it.second.id }.toSet()
    s.version
    val readN = c.letters.count { s.progress(r.series, c.id, it.id).done }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            PlateImage(c.letters.firstOrNull { it.plate != null }?.let { l -> if (s.progress(r.series, c.id, l.id).done) l.plate?.image else c.letters.getOrNull(2)?.plate?.image }.orEmpty(), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color(0x73170F0A), 0.45f to androidx.compose.ui.graphics.Color(0x0D170F0A), 1f to p.paper)))
            IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(androidx.compose.ui.graphics.Color(0xFFF7F0E1)) }
            Column(Modifier.align(Alignment.BottomStart).padding(start = Tokens.Space.s5, bottom = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caps("Chapitre ${roman(r.chapter)} · ${c.letters.firstOrNull()?.place ?: ""} ${c.letters.firstOrNull()?.date?.take(4) ?: ""}", p.giltText, small = true)
                Text(c.title[w.series.original], style = Type.display, color = p.ink)
                Text(stringResource(R.string.chapter_meta, c.letters.size, readN) + " · " + c.title[uiLang()], style = Type.small.ui(), color = p.inkSoft)
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3)) {
            c.letters.forEachIndexed { i, l ->
                val pr = s.progress(r.series, c.id, l.id)
                val can = l.id in openable
                val days = s.daysUntil(r.series, l.id)
                val reading = can && !pr.done
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    // 길: 마디와 이어지는 선
                    Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 26.dp).size(16.dp).then(
                            when { pr.done -> Modifier.background(p.giltText, androidx.compose.foundation.shape.CircleShape)
                                   reading -> Modifier.background(p.leaf, androidx.compose.foundation.shape.CircleShape).border(2.dp, p.ink, androidx.compose.foundation.shape.CircleShape)
                                   else -> Modifier.dashed(p.line) }))
                        if (i < c.letters.lastIndex) Box(Modifier.width(1.5.dp).weight(1f).background(p.line))
                    }
                    Row(
                        Modifier.weight(1f).padding(bottom = Tokens.Space.s2)
                            .then(if (reading) Modifier.background(p.leaf).border(1.dp, p.giltText.copy(alpha = 0.7f)) else Modifier)
                            .pressable(enabled = can) { s.open(r.series, c.id, l, Route.Library) }.padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s3),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("LETTRE ${roman(i + 1)}", style = Type.capsSm, color = p.giltText)
                            Text(dateLine(l.date, l.place), style = Type.body.ui(), color = if (can) p.ink else p.hideInk)
                            Text(
                                when {
                                    pr.done -> stringResource(R.string.letter_done) + (l.plate?.let { " · " + it.title[uiLang()] } ?: "")
                                    pr.shown > 0 -> stringResource(R.string.letter_reading)
                                    can -> stringResource(R.string.letter_new)
                                    days == 1 -> stringResource(R.string.letter_eta)
                                    else -> stringResource(R.string.letter_eta_n, days)
                                },
                                style = Type.small.ui(), color = when { pr.done -> p.giltText; can -> p.ink; else -> p.hideInk }, maxLines = 1,
                            )
                        }
                        if (pr.done) PlateThumb(l.plate?.image.orEmpty(), true, Modifier.size(52.dp))
                        else if (!can) Lock(p.hideInk)
                    }
                }
            }
        }
    }
}

/** 자물쇠 (아직 오지 않은 편지). */
@Composable
private fun Lock(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
    val w = size.width
    val st = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx())
    drawRect(c, androidx.compose.ui.geometry.Offset(w * 0.18f, w * 0.45f), androidx.compose.ui.geometry.Size(w * 0.64f, w * 0.45f), style = st)
    drawArc(c, 180f, 180f, false, androidx.compose.ui.geometry.Offset(w * 0.3f, w * 0.12f), androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.66f), style = st)
}

/** 작품 설정: 배울 언어 · 번역 줄 · 답장 방식 · 하루 편지 수. */
@Composable
fun SeriesSettingsScreen(s: AppState, id: String) {
    val p = Ink.palette
    val w = s.work(id)
    s.version
    val cur = s.seriesSettings(id)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.series_settings), s, help = null, showBack = true, showSettings = false)
        Page {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Portrait(w.portrait, w.fullName, Tokens.Size.avatar)
                Text(w.title[uiLang()], style = Type.heading.ui(), color = p.ink)
            }
            Caps(stringResource(R.string.set_learn), p.giltText, small = true)
            Choices(Langs.learnChoices(w.series, s.app.read).map { it to stringResource(langLabel(it)) }, cur.learn) { s.update(id, cur.copy(learn = it)) }

            Caps(stringResource(R.string.ob_modes), p.giltText, small = true)
            Column {
                listOf(
                    Triple(ReplyMode.MATCH, R.string.mode_match, R.string.mode_match_d),
                    Triple(ReplyMode.ALOUD, R.string.mode_aloud, R.string.mode_aloud_d),
                    Triple(ReplyMode.CONSTELLATION, R.string.mode_constellation, R.string.mode_constellation_d),
                ).forEach { (m, label, desc) ->
                    val on = m in cur.modes
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Checkbox) {
                            val next = if (on) cur.modes - m else cur.modes + m
                            if (next.any { it in io.github.graviton94.todayletters.core.Plays.inLetter }) s.update(id, cur.copy(modes = next))
                        },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
                    ) {
                        CheckMark(on)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(label), style = Type.body.ui(), color = p.ink)
                            Text(stringResource(desc), style = Type.small.ui(), color = p.inkSoft)
                        }
                        ModePreview(m)
                    }
                    Hair()
                }
                Text(stringResource(R.string.modes_note), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(top = Tokens.Space.s2))
            }

            Caps(stringResource(R.string.set_show_read), p.giltText, small = true)
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), cur.showRead) { s.update(id, cur.copy(showRead = it)) }

            Caps(stringResource(R.string.ob_pace), p.giltText, small = true)
            Choices(listOf(1, 2, 3).map { it to stringResource(R.string.letters_n, it) }, cur.lettersPerDay) { s.update(id, cur.copy(lettersPerDay = it)) }
            Text(stringResource(R.string.perday_note), style = Type.small.ui(), color = p.inkSoft)

            Caps(stringResource(R.string.set_notice), p.giltText, small = true)
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), cur.arrivalNotice) { s.update(id, cur.copy(arrivalNotice = it)) }
            if (cur.arrivalNotice) {
                var hour by remember { mutableStateOf(s.store.noticeHour) }
                Choices(listOf(7, 8, 12, 19, 21).map { it to stringResource(R.string.notice_hour, it) }, hour) {
                    hour = it; s.store.noticeHour = it; io.github.graviton94.todayletters.data.Notices.schedule(s.ctx)
                }
            }
            Text(stringResource(R.string.notice_note), style = Type.small.ui(), color = p.inkSoft)
        }
    }
}

/** 답장 방식 옆의 작은 그림: 별자리는 이어진 별, 따라 읽기는 소리 물결. */
@Composable
private fun ModePreview(m: ReplyMode) {
    val p = Ink.palette
    androidx.compose.foundation.Canvas(Modifier.size(62.dp, 38.dp).border(Tokens.Stroke.hair, p.hair)) {
        val w = size.width; val h = size.height
        if (m == ReplyMode.MATCH) {
            drawRect(p.line, androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.24f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawRect(p.line, androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.56f), androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.24f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawLine(p.gilt, androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.32f), androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.68f), 1.dp.toPx())
        } else if (m == ReplyMode.CONSTELLATION) {
            val a = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.68f)
            val b = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.3f)
            val c = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.58f)
            drawLine(p.gilt, a, b, 1.dp.toPx())
            drawCircle(p.gilt, 3.dp.toPx(), a); drawCircle(p.gilt, 3.dp.toPx(), b)
            drawCircle(p.gilt, 3.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        } else {
            val bars = listOf(0.25f, 0.5f, 0.75f, 0.4f, 0.62f, 0.3f, 0.18f)
            bars.forEachIndexed { i, k ->
                val x = w * (0.2f + i * 0.1f)
                drawLine(p.inkSoft, androidx.compose.ui.geometry.Offset(x, h * (0.5f - k / 2)), androidx.compose.ui.geometry.Offset(x, h * (0.5f + k / 2)), 2.5.dp.toPx())
            }
        }
    }
}

@Composable
fun Bordered(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, Ink.palette.hair).padding(Tokens.Space.s4)) { content() }
