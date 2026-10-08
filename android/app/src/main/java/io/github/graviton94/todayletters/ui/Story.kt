package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

@Composable
private fun Scroll(content: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) { content() }

/** 해설 언어: 앱 글자 언어 (영어 · 한국어). */
@Composable
private fun noteLang(@Suppress("UNUSED_PARAMETER") s: AppState) = uiLang()

/** 편지 완료: 그 편지와 이어진 그림이 도착한다. */
@Composable
fun Done(s: AppState, room: Route.Letter) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(room)
    val view = s.room(room.series)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.done_title), s, help = null, showBack = true, showSettings = false)
        Scroll {
            Caps("Lettre ${roman(room.letter)} · Reçue", p.giltText)
            letter.plate?.let { pl ->
                PlateImage(pl.image, Modifier.fillMaxWidth().aspectRatio(1.3f))
                Row(Modifier.fillMaxWidth()) {
                    Text("${pl.title[s.work(room.series).series.original]}, ${pl.date}", style = Type.small, color = p.inkSoft, modifier = Modifier.weight(1f))
                    Caps("PL. ${roman(room.letter)}", small = true)
                }
                Text(pl.title[view.read], style = Type.heading.of(view.read), color = p.ink)
            }
            Rule()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
                Stat("${letter.words.size}", stringResource(R.string.stat_words))
                Stat("${s.progress(room.series, chapter, letter.id).replied.size}", stringResource(R.string.stat_replies))
                Stat("${s.doneCount(room.series)}", stringResource(R.string.stat_letters))
            }
            Hair()
            Text(stringResource(R.string.done_next), style = Type.small.ui(), color = p.inkSoft)
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (letter.plate != null) Secondary(stringResource(R.string.done_story)) { s.go(Route.Artwork(room.series, room.letter, room)) }
            Primary(stringResource(R.string.ob_continue)) { s.go(room.from) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val p = Ink.palette
    Column {
        Text(value, style = Type.numeral, color = p.giltText)
        Text(label, style = Type.small.ui(), color = p.inkSoft)
    }
}

/** 작품 상세: 그림 · 소장처 · 큐레이터 노트. */
@Composable
fun Artwork(s: AppState, r: Route.Artwork) {
    val p = Ink.palette
    val room = r.from as? Route.Letter
    val letter = room?.let { s.letterOf(it).second }
    val pl = letter?.plate
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_gallery), s, help = null, showBack = true, showSettings = false)
        Scroll {
            if (pl == null) return@Scroll
            PlateImage(pl.image, Modifier.fillMaxWidth().aspectRatio(1.1f))
            Caps("Huile sur toile · ${pl.date}")
            Text(pl.title[s.work(r.series).series.original], style = Type.title, color = p.ink)
            Text(pl.title[s.app.read], style = Type.body.of(s.app.read), color = p.inkSoft)
            Text(pl.collection, style = Type.small, color = p.inkSoft)
            letter.note?.let { note ->
                Rule()
                Caps("Note du conservateur", p.giltText, small = true)
                val lang = noteLang(s)
                Text(note[lang], style = Type.body.of(lang), color = p.ink)
            }
        }
    }
}

/** 대화방 정보: 보내는 사람 · 이 작품의 편지들(시간 순) · 작품 설정으로 가기. 앞으로 올 편지는 가려 둔다. */
@Composable
fun RoomInfo(s: AppState, room: Route.Letter) {
    val p = Ink.palette
    val w = s.work(room.series)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.room_info), s, help = null, showBack = true, showSettings = false)
        Scroll {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
                Portrait(w.portrait, w.fullName, Tokens.Size.avatarLg)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(w.fullName, style = Type.heading.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.ink)
                    if (w.credit.isNotEmpty()) Text(w.credit, style = Type.signature.copy(fontSize = Tokens.Text.small), color = p.inkSoft)
                    Text(stringResource(R.string.info_vincent), style = Type.small.ui(), color = p.inkSoft)
                }
            }
            Rule()
            Caps("Chronologie", p.giltText)
            w.chapters.forEach { c ->
                c.letters.forEach { l ->
                    val seen = s.progress(room.series, c.id, l.id).shown > 0
                    Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Text(l.date, style = Type.small, color = p.inkSoft)
                        Text(if (seen) l.place else stringResource(R.string.info_future), style = Type.label.ui(), color = if (seen) p.ink else p.hideInk)
                    }
                    Hair()
                }
            }
            Box(Modifier.padding(top = Tokens.Space.s2)) {
                Secondary(stringResource(R.string.series_settings)) { s.go(Route.SeriesSettings(room.series)) }
            }
        }
    }
}
