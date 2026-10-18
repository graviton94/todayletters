package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Start
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 처음 시작 실력 칸의 이름. */
@Composable
fun levelLabel(l: Start.Level): String = stringResource(when (l) {
    Start.Level.NEW -> R.string.start_level_new
    Start.Level.GREETINGS -> R.string.start_level_hi
    Start.Level.SLOW_READ -> R.string.start_level_read
})

/** 왜 칸의 이름. */
@Composable
fun whyLabel(w: Start.Why): String = stringResource(when (w) {
    Start.Why.TRAVEL -> R.string.why_travel
    Start.Why.ARTS -> R.string.why_arts
    Start.Why.WORK -> R.string.why_work
    Start.Why.CURIOUS -> R.string.why_curious
})

/** 왜에 맞춘 응원 한 줄. */
@Composable
fun cheerLine(w: Start.Why): String = stringResource(when (w) {
    Start.Why.TRAVEL -> R.string.cheer_travel
    Start.Why.ARTS -> R.string.cheer_arts
    Start.Why.WORK -> R.string.cheer_work
    Start.Why.CURIOUS -> R.string.cheer_curious
})

/** 하루 분량 칸의 이름 (두 가지뿐: 편지 한 통 · 편지 한 통 + 복습 더). */
@Composable
fun amountLabel(a: Start.Amount): String = stringResource(if (a == Start.Amount.MORE) R.string.amount_more else R.string.amount_one)

/** 세로로 늘어선 고르기 칸 (한 줄 이름표, 고른 것은 채운 동그라미). 처음 시작 · 작품 설정이 같이 쓴다. */
@Composable
fun <T> OptionRows(options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    val p = Ink.palette
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { this.selected = on }
                    .background(if (on) p.leaf else Color.Transparent).border(if (on) 1.6.dp else 1.dp, if (on) p.ink else p.line)
                    .clickable(role = Role.RadioButton) { onPick(v) }.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = Type.body.ui().copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = p.ink, maxLines = 1,
                    modifier = Modifier.weight(1f))
                Box(Modifier.size(18.dp).then(if (on) Modifier.background(p.ink, CircleShape) else Modifier.border(1.4.dp, p.line, CircleShape)))
            }
        }
    }
}

/**
 * 처음 시작 (시안 onboard.html 1): 작품을 처음 열 때 한 화면씩 셋 — 실력 · 왜 · 하루 분량. 1 · 2 는 고르면 다음으로, 3 은 ‘시작하기’.
 * 뒤로 가면 앞 화면 (첫 화면에서 뒤로는 실력을 ‘처음이에요’ 로 두고 시작). 답은 [AppState.finishStart].
 */
@Composable
fun StartSheet(s: AppState, id: String, startAt: Int = 0) {
    val p = Ink.palette
    val w = s.work(id)
    val lang = s.seriesSettings(id).learn
    var page by remember(id) { mutableIntStateOf(startAt) }
    var level by remember(id) { mutableStateOf<Start.Level?>(if (startAt > 0) Start.Level.NEW else null) }
    var why by remember(id) { mutableStateOf<Start.Why?>(if (startAt > 1) Start.Why.ARTS else null) }
    var amount by remember(id) { mutableStateOf(Start.Amount.ONE) }
    val done = { s.finishStart(id, level ?: Start.Level.NEW, why, amount) }
    BackHandler { if (page > 0) page-- else done() }
    Box(Modifier.fillMaxSize().background(p.paper).pressable(haptic = false) { }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.border(1.5.dp, p.giltText, CircleShape).padding(1.5.dp)) { Portrait(w.portrait, w.fullName, 40.dp) }
                Text(w.fullName, style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
            }
            Spacer(Modifier.height(Tokens.Space.s4))
            Caps(stringResource(R.string.start_count, page + 1), p.giltText, small = true)
            when (page) {
                0 -> {
                    Text(stringResource(R.string.start_q_level, stringResource(langLabel(lang))), style = Type.title.ui(), color = p.ink)
                    OptionRows(Start.Level.entries.map { it to levelLabel(it) }, level) { level = it; page = 1 }
                    Text(stringResource(R.string.start_note), style = Type.small.ui(), color = p.inkSoft)
                }
                1 -> {
                    Text(stringResource(R.string.start_q_why), style = Type.title.ui(), color = p.ink)
                    OptionRows(Start.Why.entries.map { it to whyLabel(it) }, why) { why = it; page = 2 }
                }
                else -> {
                    Text(stringResource(R.string.start_q_amount), style = Type.title.ui(), color = p.ink)
                    OptionRows(Start.Amount.entries.map { it to amountLabel(it) }, amount) { amount = it }
                    why?.let { Text(cheerLine(it), style = Type.small.ui(), color = p.giltText) }
                    Spacer(Modifier.height(Tokens.Space.s2))
                    Primary(stringResource(R.string.start_go), shine = true, onClick = done)
                }
            }
        }
    }
}

/**
 * 오늘 맨 위 (천천히 듣는 2주 동안): “천천히 듣는 2주 · N일째” 와 ‘어제 편지 다시 듣기 · 1분’ (그 편지의 메시지 낭독을 이어서).
 * 어제 편지가 없으면 (첫날) 이름표만.
 */
@Composable
fun SlowCard(s: AppState, id: String) {
    val p = Ink.palette
    val day = s.slowDay(id) ?: return
    val y = s.yesterdayLetter(id)
    val clips = remember(y?.second?.id, s.room(id).learn) { y?.let { (c, l) -> s.letterClips(id, c, l) }.orEmpty() }
    val mins = remember(clips) { io.github.graviton94.todayletters.core.Slow.minutes(s.clipsMs(clips)) }
    Column(Modifier.fillMaxWidth().background(p.leaf).border(1.dp, p.gilt).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Caps(stringResource(R.string.slow_label, day), p.giltText, small = true)
        if (clips.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.slow_again, mins), style = Type.body.ui(), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Box(Modifier.heightIn(min = 44.dp).pressable { s.playYesterday(id) }.padding(start = Tokens.Space.s2), contentAlignment = Alignment.CenterEnd) {
                Text(stringResource(if (s.againPlaying) R.string.slow_stop else R.string.slow_listen), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold),
                    color = p.giltText, maxLines = 1)
            }
        }
    }
}

/** 편지를 마친 뒤 한 번 (첫 편지 · 3일째): “처음 2주는 천천히 들어봐요 · 오늘 N분 들었어요”. */
@Composable
fun SlowNoteLine(s: AppState, series: String, key: String) {
    if (s.slowNote != key) return
    val p = Ink.palette
    Text(stringResource(R.string.slow_note, s.listenedToday(s.room(series).learn).coerceAtLeast(1)), style = Type.small.ui(), color = p.giltText,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
