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

/** 편지함: 진행 중인 작품마다 대화방 한 줄. */
@Composable
fun Inbox(s: AppState) {
    val p = Ink.palette
    s.version
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_inbox), s, help = "inbox", showBack = false, showSettings = true)
        Page {
            s.works.forEach { w ->
                val id = w.series.id
                val waiting = s.waiting(id)
                val next = s.openable(id).firstOrNull { (c, l) -> !s.progress(id, c, l.id).done }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.Button) {
                        next?.let { (c, l) -> s.open(id, c, l, Route.Inbox) } ?: s.go(Route.Series(id))
                    },
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Portrait(w.portrait, w.fullName, Tokens.Size.portraitRow)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                        Text(w.title[uiLang()], style = Type.heading.ui(), color = p.ink)
                        val learn = s.seriesSettings(id).learn
                        Text(
                            next?.second?.messages?.firstOrNull()?.text?.get(learn) ?: stringResource(R.string.inbox_all_read),
                            style = Type.small.of(learn), color = if (waiting > 0) p.giltText else p.inkSoft, maxLines = 1,
                        )
                    }
                    if (waiting > 0) Box(Modifier.size(Tokens.Size.icon + Tokens.Space.s1).background(p.fill, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$waiting", style = Type.small, color = p.onFill)
                    }
                }
                Hair()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Caps("Note", Ink.palette.giltText, small = true)
                Text(stringResource(R.string.inbox_you_are_theo), style = Type.small.ui(), color = p.inkSoft)
            }
        }
    }
}

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

/** 단어장: 받은 편지의 낱말 (배우는 언어 · 읽는 언어). */
@Composable
fun WordsTab(s: AppState) {
    val p = Ink.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_words), s, help = null, showBack = s.route !is Route.Tab, showSettings = s.route is Route.Tab)
        Page {
            s.works.forEach { w ->
                val id = w.series.id
                val room = s.room(id)
                w.chapters.forEach { c ->
                    c.letters.filter { s.progress(id, c.id, it.id).shown > 0 }.forEach { l ->
                        Caps(l.date)
                        l.words.forEach { word ->
                            Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch), verticalAlignment = Alignment.CenterVertically) {
                                Text(word.text[room.learn], style = Type.target.of(room.learn), color = p.ink, modifier = Modifier.weight(1f))
                                Text(word.text[room.read], style = Type.body.of(room.read), color = p.inkSoft)
                            }
                            Hair()
                        }
                    }
                }
            }
            Text(stringResource(R.string.words_note), style = Type.small.ui(), color = p.inkSoft)
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

@Composable
fun ChapterScreen(s: AppState, r: Route.Chapter) {
    val p = Ink.palette
    val w = s.work(r.series)
    val c = w.chapters[r.chapter - 1]
    val openable = s.openable(r.series).map { it.second.id }.toSet()
    s.version
    Column(Modifier.fillMaxSize()) {
        TopBar(w.sender, s, help = null, showBack = true, showSettings = false)
        Page {
            Caps("Chapitre ${roman(r.chapter)}")
            Text(c.title[w.series.original], style = Type.display, color = p.ink)
            Text(c.title[s.app.read], style = Type.body.of(s.app.read), color = p.inkSoft)
            c.letters.forEachIndexed { i, l ->
                val pr = s.progress(r.series, c.id, l.id)
                val can = l.id in openable
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(enabled = can, role = Role.Button) { s.open(r.series, c.id, l, Route.Library) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
                ) {
                    Text(roman(i + 1), style = Type.numeral, color = if (can) p.giltText else p.inkSoft)
                    Column(Modifier.weight(1f)) {
                        Text("${l.place} · ${l.date}", style = Type.label, color = if (can) p.ink else p.inkSoft)
                        Text(
                            stringResource(if (pr.done) R.string.letter_done else if (can) R.string.letter_open else R.string.letter_tomorrow),
                            style = Type.small.ui(), color = p.inkSoft,
                        )
                    }
                }
                Hair()
            }
        }
    }
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
                    Triple(ReplyMode.CONSTELLATION, R.string.mode_constellation, R.string.mode_constellation_d),
                    Triple(ReplyMode.ALOUD, R.string.mode_aloud, R.string.mode_aloud_d),
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
        }
    }
}

/** 답장 방식 옆의 작은 그림: 별자리는 이어진 별, 따라 읽기는 소리 물결. */
@Composable
private fun ModePreview(m: ReplyMode) {
    val p = Ink.palette
    androidx.compose.foundation.Canvas(Modifier.size(62.dp, 38.dp).border(Tokens.Stroke.hair, p.hair)) {
        val w = size.width; val h = size.height
        if (m == ReplyMode.CONSTELLATION) {
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
