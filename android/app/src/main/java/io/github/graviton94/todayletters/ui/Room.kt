package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.Plays
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 편지 = 대화방. 메시지가 입력 중 표시 → 타이핑 → 낭독 순서로 하나씩 도착한다.
 * 진도는 메시지 단위로 저장되어, 나갔다 와도 그 자리에서 이어진다.
 */
@Composable
fun Room(s: AppState, r: Route.Letter) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r)
    val id = r.series
    val view = s.room(id)
    s.version
    val total = letter.messages.size
    var shown by remember(letter.id) { mutableIntStateOf(s.progress(id, chapter, letter.id).shown) }
    var typing by remember(letter.id) { mutableStateOf(-1) }   // 지금 타이핑 중인 메시지 번호
    var typed by remember(letter.id) { mutableIntStateOf(0) }  // 그 메시지에서 보인 글자 수
    var dots by remember(letter.id) { mutableStateOf(false) }
    var showRead by remember(letter.id, s.version) { mutableStateOf(view.showRead) }
    var words by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    val pace = when (view.pace) { TypingPace.CALM -> 1f; TypingPace.QUICK -> 0.5f; TypingPace.INSTANT -> 0f }
    fun audio(i: Int) = s.narrator.path(id, chapter, letter.id, "m${i + 1}_${view.learn.code}")

    DisposableEffect(letter.id) { onDispose { s.narrator.stop() } }

    // 도착: 남은 메시지를 하나씩
    LaunchedEffect(letter.id) {
        while (shown < total) {
            val i = shown
            if (pace > 0f) { dots = true; delay((Tokens.Motion.typingDotsMs * pace).toLong()); dots = false }
            val line = letter.messages[i].text[view.learn]
            typing = i; typed = 0
            if (pace > 0f) while (typed < line.length) {
                delay((Tokens.Motion.typeCharMs * pace).toLong().coerceAtLeast(8)); typed = (typed + Tokens.Motion.typeChunk).coerceAtMost(line.length)
            }
            typing = -1
            shown = i + 1
            s.save(id, chapter, letter.id, s.progress(id, chapter, letter.id).reveal(total).copy(shown = shown))
            list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            if (s.app.sound) suspendCancellableCoroutine { c -> s.narrator.play(audio(i)) { if (c.isActive) c.resume(Unit) } }
            if (pace > 0f) delay((Tokens.Motion.betweenMessagesMs * pace).toLong())
        }
    }

    val progress = s.progress(id, chapter, letter.id)
    val modes = s.seriesSettings(id).modes.filter { it in Plays.inLetter }
    val nextPlay = modes.firstOrNull { it !in progress.replied }
    val reply = Exercises.replyFor(letter, view.learn).second

    Column(Modifier.fillMaxSize()) {
        TopBar(
            if (typing >= 0 || dots) "Vincent · écrit…" else "Vincent", s, help = "room", showBack = true, showSettings = false,
        ) {
            IconButton(stringResource(R.string.toggle_small_line), onClick = { showRead = !showRead }) {
                Text(if (view.read.code == "ko") "한" else view.read.code.uppercase(), style = Type.label.ui(), color = if (showRead) p.giltText else p.inkSoft)
            }
            IconButton(stringResource(R.string.room_info), onClick = { s.go(Route.RoomInfo(r)) }) { Lines(p.ink) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Box(Modifier.weight(1f)) { Hair() }
                    Postmark(letter.place, dayOf(letter.date), letter.date.take(4))
                    Box(Modifier.weight(1f)) { Hair() }
                }
            }
            itemsIndexed(letter.messages.take(maxOf(shown, typing + 1))) { i, m ->
                val isTyping = i == typing
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalAlignment = Alignment.Top) {
                    if (i == 0) SealMark(Tokens.Seals.vincent, "V", Tokens.Size.avatar) else Box(Modifier.heightIn(min = Tokens.Size.avatar).padding(start = Tokens.Size.avatar))
                    Box(Modifier.clickable(enabled = !isTyping, role = Role.Button) { s.narrator.play(audio(i)) }) {
                        Incoming {
                            val line = m.text[view.learn]
                            Pair2(
                                if (isTyping) line.take(typed) else line, view.learn,
                                if (showRead && !isTyping) m.text[view.read] else null, view.read, caret = isTyping,
                            )
                            if (i == total - 1 && !isTyping) Text("t. à t. Vincent", style = Type.signature, color = p.inkSoft, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
            if (dots) item { Row { Box(Modifier.padding(start = Tokens.Size.avatar + Tokens.Space.s2)) { Twinkle(s.reducedMotion) } } }
            if (shown >= total) {
                letter.note?.let { note ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                            Hair()
                            Caps("Note du conservateur", p.giltText, small = true)
                            val lang = if (s.app.ui.code == "ko") io.github.graviton94.todayletters.core.Lang.KO else io.github.graviton94.todayletters.core.Lang.EN
                            Text(note[lang], style = Type.body.of(lang), color = p.ink)
                            Hair()
                        }
                    }
                }
                if (ReplyMode.CONSTELLATION in progress.replied) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Outgoing { Pair2(reply[view.learn], view.learn, if (showRead) reply[view.read] else null, view.read, onFill = true) }
                    }
                }
                if (ReplyMode.ALOUD in progress.replied) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Outgoing {
                            Row(Modifier.clickable(role = Role.Button) { s.recorder.play() }, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                                Text("▶", style = Type.label, color = p.onFill)
                                Text(stringResource(R.string.voice_done), style = Type.small.ui(), color = p.onFillSoft)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Hair()
            if (shown >= total && letter.words.isNotEmpty()) Secondary(stringResource(R.string.room_words, letter.words.size)) { words = true }
            when {
                shown < total -> Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.buttonSm).border(Tokens.Stroke.hair, p.hair), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.room_writing), style = Type.label.ui(), color = p.inkSoft)
                }
                nextPlay != null -> Primary(stringResource(R.string.room_reply)) { s.go(Route.Play(r, nextPlay)) }
                else -> Primary(stringResource(R.string.room_finish)) {
                    s.save(id, chapter, letter.id, progress.complete(s.seriesSettings(id).modes).copy(done = true))
                    s.go(Route.Done(r))
                }
            }
        }
    }
    if (words) WordSheet(s, r, onClose = { words = false })
}

/** 낱말 카드: 이 편지의 낱말 (원어 · 영어 · 한국어 세 칸, 누르면 발음). */
@Composable
fun WordSheet(s: AppState, r: Route.Letter, onClose: () -> Unit) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(r)
    val view = s.room(r.series)
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).clickable(enabled = false) {}.padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Rule()
            Caps("Mots · ${letter.date}", p.giltText)
            letter.words.forEachIndexed { i, w ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) {
                        s.narrator.play(s.narrator.path(r.series, chapter, letter.id, "w${i + 1}_${view.learn.code}"))
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(w.text[view.learn], style = Type.heading.of(view.learn), color = p.ink)
                        if (w.ipa.isNotEmpty() && view.learn == r.let { s.work(it.series).series.original }) Text("[${w.ipa}] · ${w.pos}", style = Type.small, color = p.inkSoft)
                    }
                    Text(w.text[view.read], style = Type.body.of(view.read), color = p.inkSoft)
                }
                Hair()
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
}

@Composable
fun Lines(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(Tokens.Size.icon)) {
    val w = size.width; val h = size.height
    for (k in 0..2) drawLine(c, androidx.compose.ui.geometry.Offset(0f, h * (0.25f + k * 0.25f)), androidx.compose.ui.geometry.Offset(w, h * (0.25f + k * 0.25f)), 1.5f * density)
}

/** "1888-02-21" → "21 FÉVR." */
fun dayOf(date: String): String = runCatching {
    val d = java.time.LocalDate.parse(date.take(10))
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH))
}.getOrDefault(date)
