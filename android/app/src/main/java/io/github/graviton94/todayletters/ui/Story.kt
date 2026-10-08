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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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

/**
 * 오늘의 편지 완료: 밀랍 도장이 찍히고(진동), 오늘 한 일이 하나씩 체크되고, 이어 읽은 날 · 그림 · 다음 편지가 언제 오는지.
 * 뒤로 가면 편지함으로 (대화방으로 되돌아가지 않음).
 */
@Composable
fun Done(s: AppState, room: Route.Letter) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(room)
    val view = s.room(room.series)
    val work = s.work(room.series)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val home = { s.go(Route.Inbox) }
    androidx.activity.compose.BackHandler(onBack = home)
    val t = remember { androidx.compose.animation.core.Animatable(if (s.reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        t.animateTo(0.35f, androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        if (s.app.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        t.animateTo(1f, androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing))
    }
    fun win(a: Float, b: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(((t.value - a) / (b - a)).coerceIn(0f, 1f))
    val stamp = win(0f, 0.35f)
    val modes = s.seriesSettings(room.series).modes.filter { it in io.github.graviton94.todayletters.core.Plays.inLetter }
    val waiting = s.waiting(room.series)
    val next = s.openable(room.series).firstOrNull { (c, l) -> !s.progress(room.series, c, l.id).done }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.25f)) {
        Scroll {
            Box(Modifier.fillMaxWidth().padding(top = Tokens.Space.s5), contentAlignment = Alignment.Center) {
                // 밀랍 도장: 위에서 크게 내려와 찍힌다
                Box(
                    Modifier.size(96.dp).graphicsLayer {
                        val k = 1.8f - 0.8f * stamp
                        scaleX = k; scaleY = k; alpha = stamp; rotationZ = -12f * (1f - stamp)
                    }.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFC25546), Color(0xFF9A3027), Color(0xFF6E1C15))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Portrait(work.portrait, work.fullName, 60.dp)
                }
            }
            Text(stringResource(R.string.done_title), style = Type.title.ui(), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = win(0.3f, 0.5f) })
            if (s.streak > 0) Text(stringResource(R.string.done_streak, s.streak), style = Type.body.ui(), color = p.giltText, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = win(0.4f, 0.6f) })
            // 오늘 한 일
            Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                val items = listOf(R.string.ob0_d1) + modes.map { modeLabel(it) } + listOf(R.string.ob0_d5)
                items.forEachIndexed { k, label ->
                    val a = win(0.45f + k * 0.08f, 0.55f + k * 0.08f)
                    Row(Modifier.graphicsLayer { alpha = 0.3f + 0.7f * a }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        CheckMark(a > 0.5f, 20.dp)
                        Text(stringResource(label), style = Type.body.ui(), color = p.ink)
                    }
                }
            }
            letter.plate?.let { pl ->
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = win(0.75f, 0.95f) }.pressable { s.go(Route.Artwork(room.series, room.letter, room)) }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    PlateImage(pl.image, Modifier.fillMaxWidth().aspectRatio(1.6f))
                    Text(pl.title[view.read], style = Type.heading.of(view.read), color = p.ink)
                    Text("${pl.title[work.series.original]}, ${pl.date}", style = Type.small, color = p.inkSoft)
                }
            }
            Text(
                stringResource(if (waiting > 0) R.string.done_next_now else R.string.done_next_tomorrow),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.graphicsLayer { alpha = win(0.8f, 1f) },
            )
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (letter.plate != null) Secondary(stringResource(R.string.done_story)) { s.go(Route.Artwork(room.series, room.letter, room)) }
            if (next != null) Primary(stringResource(R.string.done_open_next)) { s.open(room.series, next.first, next.second, Route.Inbox) }
            else Primary(stringResource(R.string.done_home), onClick = home)
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
