package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Memory
import io.github.graviton94.todayletters.core.ReviewKind
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 낱말 카드 상세 (v21 1단계 F2): 낱말을 누르면 어디서든 (편지 방 · 카드함 찾기 · 복습 결과).
 * 이름표(어느 편지 · 번호) · 발음(0.7×) · 뜻 · 내 기억 6단계와 다음 복습 날 · 그 낱말이 나온 편지 문장 · 편지로 가기 · 말해 보기.
 * [key] 는 낱말 카드 열쇠 (작품:장:편지:번호). 카드함에 아직 없는 낱말이어도 보여 준다.
 */
@Composable
fun WordDetail(s: AppState, key: String, onClose: () -> Unit) {
    // 어느 화면에서 열든 맨 위에 (전체 화면 창)
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WordDetailBody(s, key, onClose)
    }
}

@Composable
internal fun WordDetailBody(s: AppState, key: String, onClose: () -> Unit) {
    val p = Ink.palette
    BackHandler(onBack = onClose)
    val found = remember(key) { s.cardWord(key) } ?: return
    val (w, cl, wi) = found
    val (chapter, letter) = cl
    val id = w.series.id
    val view = s.room(id)
    val word = letter.words.getOrNull(wi) ?: return
    val learn = word.text[view.learn]
    val audio = s.narrator.path(id, chapter, letter.id, "w${wi + 1}_${view.learn.code}")
    val card = remember(key, s.version) { s.cardMap()[key] }
    val letters = w.chapters.flatMap { it.letters }
    val n = letters.indexOf(letter) + 1
    LaunchedEffect(key) { s.narrator.play(audio) }
    // 그 낱말이 나온 문장 (편지 속 꼴로 찾는다)
    val stem = Breaks.plain(learn).substringBefore(" (").substringBefore(",").trim().lowercase()
    val mi = letter.messages.indexOfFirst { Breaks.plain(it.text[view.learn]).lowercase().contains(stem.take(maxOf(3, stem.length - 2))) }
    Box(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(stringResource(R.string.close), onClick = onClose) { Text("×", style = Type.title, color = p.ink) }
                Text(stringResource(R.string.word_card), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                // 이름표
                Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Caps("N° ${"%03d".format(wi + 1)} · LETTRE ${roman(n)}", p.inkSoft, small = true, decorative = true, modifier = Modifier.align(Alignment.End))
                    Text(learn, style = Type.display.copy(fontFamily = Faces.display).of(view.learn), color = p.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Box(Modifier.size(36.dp).border(1.dp, p.giltText, CircleShape).pressable { s.narrator.play(audio) }, contentAlignment = Alignment.Center) { SpeakerGlyph(p.giltText, 16.dp) }
                        Box(Modifier.heightIn(min = 36.dp).pressable { s.narrator.play(audio, speed = 0.7f) }, contentAlignment = Alignment.Center) { Capsule("0.7×") }
                        if (word.ipa.isNotEmpty() && view.learn == w.series.original) Text("[${word.ipa}]" + if (word.pos.isNotEmpty()) " · ${word.pos}" else "", style = Type.small, color = p.inkSoft)
                    }
                    Text(word.text[view.read], style = Type.title.of(view.read), color = p.ink)
                }
                // 내 기억
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val stages = io.github.graviton94.todayletters.core.Growth.Stage.entries
                    if (card == null) Text(stringResource(R.string.word_not_yet), style = Type.small.ui(), color = p.inkSoft)
                    else {
                        Text(stringResource(R.string.word_memory, card.box + 1, stringResource(stages[card.box.coerceIn(0, stages.lastIndex)].label)), style = Type.small.ui(), color = p.inkSoft)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            stages.indices.forEach { k -> Box(Modifier.weight(1f).height(5.dp).background(if (k <= card.box) p.giltText else p.hair)) }
                        }
                        Row { stages.forEach { st -> Text(stringResource(st.label), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk, modifier = Modifier.weight(1f)) } }
                        val days = (card.due - s.today).toInt()
                        val date = java.time.LocalDate.ofEpochDay(card.due)
                        Text(stringResource(R.string.word_next, if (days <= 0) stringResource(R.string.word_today) else stringResource(R.string.word_in_days, days), date.monthValue, date.dayOfMonth),
                            style = Type.small.ui(), color = p.ink)
                        if (card.lapsed) Text(stringResource(R.string.word_lapsed), style = Type.small.ui(), color = p.giltText)
                        if (card.box == Memory.TOP) Text(stringResource(R.string.word_keep_note), style = Type.small.ui(), color = p.inkSoft)
                    }
                }
                // 나온 문장
                if (mi >= 0) {
                    val m = letter.messages[mi]
                    val text = Breaks.plain(m.text[view.learn])
                    val at = text.lowercase().indexOf(stem.take(maxOf(3, stem.length - 2)))
                    val marked = buildAnnotatedString {
                        append(text)
                        if (at >= 0) {
                            val end = text.indexOfAny(charArrayOf(' ', ',', '.', ';', ':', '!', '?'), at).let { if (it < 0) text.length else it }
                            addStyle(SpanStyle(background = p.gilt.copy(alpha = 0.35f), fontStyle = FontStyle.Normal), at, end)
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(p.leaf).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().height(0.dp))
                        Text(marked, style = Type.base.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic).of(view.learn), color = p.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.letter_n, roman(n)) + " · " + dateLine(letter.date, letter.place) + " · " + letter.place,
                                style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, modifier = Modifier.weight(1f))
                            Box(Modifier.heightIn(min = 36.dp).pressable { s.narrator.play(s.narrator.path(id, chapter, letter.id, "m${mi + 1}_${view.learn.code}")) },
                                contentAlignment = Alignment.Center) { Capsule(stringResource(R.string.word_hear_sentence)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(Tokens.Space.s5), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.buttonSm).border(1.dp, p.ink).pressable {
                        onClose(); s.open(id, chapter, letter, Route.Words)
                    }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.word_open_letter), style = Type.small.ui(), color = p.ink) }
                }
                Box(Modifier.weight(1f)) {
                    Primary(stringResource(R.string.word_say), small = true) {
                        if (card != null) { onClose(); s.go(Route.Session(ReviewKind.SPEAK, only = listOf(key))) } else s.narrator.play(audio)
                    }
                }
            }
        }
    }
}
