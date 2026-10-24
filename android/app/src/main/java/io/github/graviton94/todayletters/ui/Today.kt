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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
import io.github.graviton94.todayletters.core.Journey
import io.github.graviton94.todayletters.core.Earn
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 오늘 (시리즈 안 첫 이름표) = 오늘의 여정 하나 (core 의 Journey): 차례가 있는 세 걸음.
 *   I 오늘의 편지 (걸음마다 점, 건너뛴 걸음은 빈 점) → II 오늘의 복습 (이 작품 카드만) → III 여정 완료 (보너스 화폐 + 편지 그림).
 * 이번 주 · 낭독회 · 이달의 전시 · 화폐 쓰기는 갤러리 · 이정표 이름표로 옮겼다. 세는 것은 실제로 한 일뿐.
 */
@Composable
fun Inbox(s: AppState) {
    val p = Ink.palette
    s.version
    // 들어올 때 한 번 정산 (혼자 읽기가 올랐거나 새 이정표가 있으면 보상 순간)
    LaunchedEffect(Unit) { s.settle() }
    val work = s.currentWork
    val id = work.series.id
    val letters = work.chapters.flatMap { it.letters }
    val j = s.journey(id)
    val tl = s.todayLetter(id)

    var backupSheet by remember { mutableStateOf(false) }
    // 새 편지 알림 허락 (Android 13+): 첫 편지를 마친 뒤 다음에 오늘에 왔을 때 한 번, 덮개 (보상 등) 가 걷힌 뒤 (편지 끝 흐름: 완료 화면 위에 겹치지 않게)
    var preNotice by remember { mutableStateOf(false) }
    val askNotice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        io.github.graviton94.todayletters.data.Notices.schedule(s.ctx)
    }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !s.store.notifyAsked && s.store.progressKeys().any { s.store.progress(it).done }) {
            s.uncovered(); s.store.notifyAsked = true; preNotice = true
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, learnChip = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            // 다른 작품 소식 한 줄 (읽기 시작한 작품에 편지가 와 있을 때)
            OthersLine(s, id)
            // 누구의 편지 · 무엇을 배우는지 (누르면 배우는 말 바꾸기)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.border(1.5.dp, p.giltText, CircleShape).padding(1.5.dp)) { Portrait(work.portrait, work.fullName, 44.dp) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_whose, work.name[uiLang()]), style = Type.title.ui(), color = p.ink, maxLines = 1)
                    LearnChip(s, id, learning = true)
                }
            }
            // 오늘 마친 편지의 그림 (편지 완료에서 옮겨 온 자리, 시안 rw2 ②): 그날 내내 맨 위에
            val tlDone = tl != null && s.progress(id, tl.first, tl.second.id).done
            tl?.second?.plate?.image?.takeIf { tlDone && it.isNotBlank() && io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, "plates/$it") }?.let { img ->
                Box(Modifier.fillMaxWidth().height(150.dp).flightSlot(s, "today-pic").clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))) {
                    AssetImage("plates/$img", Modifier.fillMaxSize(), sample = 2, alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f))
                }
            }
            // 어제 말 떠올리기 (지난 공부한 날 회화를 했으면, 오늘 마칠 때까지)
            RecallCard(s, id)
            // 천천히 듣는 2주: 어제 편지 다시 듣기 (기간 중에만)
            SlowCard(s, id)
            TodayLine(j)
            // 기록 보관 한 줄 (오래 보관하지 않았을 때만)
            BackupNudgeLine(s) { backupSheet = true }
            StreakBlock(s, work)
            Hair()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.journey_title), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
                Text("${j.done} / 3", style = Type.heading.copy(fontFamily = Faces.display), color = p.giltText)
            }
            // I 오늘의 편지
            // 여정을 마친 날, 할 것이 없던 걸음도 끝난 표시 (머리의 3 / 3 과 맞춘다)
            // 편지 완료에서 막 왔으면: 그림이 닿은 뒤 편지에 체크가 그어지고 다음 할 일 (복습) 이 밝아진다 (한 번)
            val landed = tl != null && s.landing == s.key(id, tl.first, tl.second.id)
            val land by rememberClock(s.landing ?: "", s.reducedMotion || !landed, 2400)
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { s.landing = null } }
            val tick = if (landed) win(land, 900, 700) else 1f
            val glow = if (landed) win(land, 1300, 900) else 0f
            JourneyStep("I", stringResource(R.string.journey_letter), if (j.complete && j.letter == Journey.Step.NONE) Journey.Step.DONE else j.letter, last = false, tick = tick) {
                // 여러 통이 와 있으면: 수 · 가장 앞의 편지를 큰 단추로 · 나머지는 한 줄씩
                // 오늘 이미 한 통을 마쳤으면 그 카드 아래에 수 · 나머지
                val waiting = s.waitingLetters(id)
                val rest = waiting.filter { it.second != tl?.second }
                val tlOpen = tl != null && !s.progress(id, tl.first, tl.second.id).done
                if (tlOpen && waiting.size > 1) Text(stringResource(R.string.today_waiting, waiting.size), style = Type.small.ui(), color = p.giltText, maxLines = 1)
                if (tl != null) TodayLetter(s, id, work, tl.first, tl.second, letters)
                if (tl != null && !tlOpen && rest.isNotEmpty()) Text(stringResource(R.string.today_waiting, rest.size), style = Type.small.ui(), color = p.giltText, maxLines = 1,
                    modifier = Modifier.padding(top = Tokens.Space.s2))
                if (tl != null) rest.forEach { (c, l) -> WaitingRow(s, id, work, c, l, letters) }
                else {
                    val upcoming = letters.firstOrNull { l -> s.openable(id).none { it.second == l } }
                    val days = upcoming?.let { s.daysUntil(id, it.id) }
                    Text(stringResource(if (s.doneCount(id) >= letters.size) R.string.chapter_end_next else R.string.today_all_done), style = Type.body.ui(), color = p.ink)
                    Text(stringResource(R.string.next_title) + " · " + when { days == null -> stringResource(R.string.next_none); days <= 1 -> stringResource(R.string.letter_eta); else -> stringResource(R.string.next_in, days) },
                        style = Type.small.ui(), color = p.inkSoft)
                }
            }
            // II 오늘의 복습 (이 작품 카드만, 한 판)
            JourneyStep("II", stringResource(R.string.review_today), if (j.complete && j.review == Journey.Step.NONE) Journey.Step.DONE else j.review, last = false,
                glow = if (j.review == Journey.Step.NOW || j.review == Journey.Step.TODO) glow else 0f) {
                val (n, total) = j.reviewCount
                when (j.review) {
                    Journey.Step.NONE -> Text(stringResource(if (j.letter == Journey.Step.DONE && s.seriesCards(id).isEmpty()) R.string.review_first_tomorrow else reviewNone(s.seriesCards(id))), style = Type.small.ui(), color = p.inkSoft)
                    Journey.Step.DONE -> Text(stringResource(R.string.journey_review_done, total), style = Type.small.ui(), color = p.inkSoft)
                    else -> {
                        Text(stringResource(R.string.review_words_n, total), style = Type.small.ui(), color = p.inkSoft)
                        if (n > 0) Bar(n.toFloat() / total.coerceAtLeast(1))
                        GoButton(stringResource(if (n > 0) R.string.journey_review_continue else R.string.review_start), stringResource(R.string.go_cards_n, (total - n).coerceAtLeast(0))) { s.go(Route.Session()) }
                    }
                }
            }
            // III 여정 완료: 보너스 화폐 + 오늘 편지의 그림
            JourneyStep("III", stringResource(R.string.journey_bonus), if (j.paid) Journey.Step.DONE else Journey.Step.TODO, last = true) {
                Text(stringResource(if (j.paid) R.string.journey_bonus_paid else R.string.journey_bonus_d, Earn.JOURNEY.amount, work.kit.currency[uiLang()]),
                    style = Type.small.ui(), color = if (j.paid) p.giltText else p.inkSoft)
                // 받기까지 남은 걸음 (할 것이 남은 걸음만, 화면의 이름 그대로)
                if (!j.paid) {
                    val left = listOfNotNull(
                        stringResource(R.string.journey_letter).takeIf { j.letter == Journey.Step.TODO || j.letter == Journey.Step.NOW },
                        stringResource(R.string.review_today).takeIf { j.review == Journey.Step.TODO || j.review == Journey.Step.NOW },
                    )
                    if (left.isNotEmpty()) Text(stringResource(R.string.journey_left, left.joinToString(" · ")), style = Type.small.ui(), color = p.ink, maxLines = 1)
                }
            }
            Hair()
            // 진도 한 줄 (F29) · 목차: 한 줄에 높이를 맞추고, 목록은 테두리 단추로 뚜렷하게
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lib_letters, s.doneCount(id), letters.size), style = Type.body.ui(), color = p.inkSoft, modifier = Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 44.dp).border(1.dp, p.ink).pressable { s.go(Route.Series(id)) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.today_all_letters), style = Type.body.ui(), color = p.ink, maxLines = 1) }
            }
            FreeLeftLine(s, id)
        }
    }
    // 보관 시트는 화면 위에 (굴리는 칸 밖)
    BackupExportFlow(s, backupSheet) { backupSheet = false }
    if (preNotice) Ask(stringResource(R.string.notice_pre_t), stringResource(R.string.notice_pre_yes), stringResource(R.string.notice_pre_no),
        onYes = { preNotice = false; askNotice.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, onNo = { preNotice = false })
    }
}

/** 여정의 한 걸음: 왼쪽 로마 숫자 동그라미 (다 하면 금빛, 할 것이 없으면 점선) · 이름 · 내용. 걸음 사이를 세로 선이 잇는다. */
@Composable
private fun JourneyStep(num: String, title: String, state: Journey.Step, last: Boolean,
                        /** 체크가 그어지는 정도 (편지 완료에서 막 왔을 때 0 → 1). */ tick: Float = 1f,
                        /** 다음 할 일로 밝히기 (옅은 금빛 바탕, 0..1). */ glow: Float = 0f, content: @Composable () -> Unit) {
    val p = Ink.palette
    val done = state == Journey.Step.DONE && tick >= 1f
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
        .then(if (glow > 0f) Modifier.background(p.gilt.copy(alpha = 0.10f * glow)) else Modifier), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).then(when {
                state == Journey.Step.DONE && tick < 1f -> Modifier.border(1.5.dp, p.giltText, CircleShape).background(p.giltText.copy(alpha = tick), CircleShape)
                else -> Modifier
            }).then(when (if (state == Journey.Step.DONE && tick < 1f) null else state) {
                null -> Modifier
                Journey.Step.DONE -> Modifier.background(p.giltText, CircleShape)
                Journey.Step.NONE -> Modifier.dashed(p.line)
                Journey.Step.NOW -> Modifier.border(2.dp, p.ink, CircleShape)
                else -> Modifier.border(1.5.dp, p.hideInk, CircleShape)
            }), contentAlignment = Alignment.Center) {
                Text(if (done) "✓" else num, style = Type.small.copy(fontFamily = Faces.display, fontSize = Tokens.Text.caps), color = if (done) p.paper else p.inkSoft)
            }
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(if (done) p.giltText else p.line))
        }
        Column(Modifier.weight(1f).padding(bottom = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(title, style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = if (state == Journey.Step.NONE) p.inkSoft else p.ink)
            content()
        }
    }
}

/** 오늘의 편지 한 장: 머리 · 첫 줄 (자르지 않음) · 걸음 점 (읽기 · 낱말 · 따라 읽기 · 답장 · 그림, 건너뛴 걸음은 빈 점) · 단추. */
@Composable
private fun TodayLetter(s: AppState, id: String, work: io.github.graviton94.todayletters.data.Work, c: String, l: io.github.graviton94.todayletters.core.Letter,
                        letters: List<io.github.graviton94.todayletters.core.Letter>) {
    val p = Ink.palette
    val view = s.room(id)
    val pr = s.progress(id, c, l.id)
    val modes = s.letterModes(id, c, l)
    val idx = letters.indexOf(l) + 1
    Caps("${work.kit.place.substringBefore(" ")} · ${stringResource(R.string.letter_n, roman(idx))}".uppercase(), p.giltText, small = true, decorative = true)
    Text(l.status?.get(uiLang()) ?: dateLine(l.date, l.place), style = Type.title.ui(), color = p.ink)
    Text(dateLine(l.date, l.place), style = Type.small.ui(), color = p.inkSoft)
    Text(l.messages.first().text[view.learn], style = Type.target.of(view.learn), color = p.ink)
    if (view.showRead && view.read != view.learn) Text(l.messages.first().text[view.read], style = Type.small.of(view.read), color = p.inkSoft)
    // 천천히 듣는 2주: 처음 한 번은 0.7× 로 저절로 읽는다
    if (s.slowPassDue(id, c, l)) Text(stringResource(R.string.slow_first), style = Type.small.ui(), color = p.giltText, maxLines = 1)
    // 걸음 점
    val dots = Journey.letterDots(pr.shown, l.messages.size, modes, pr)
    val labels = listOf(stringResource(R.string.step_read_short)) + modes.map { stringResource(when (it) {
        io.github.graviton94.todayletters.core.ReplyMode.MATCH -> R.string.step_match
        io.github.graviton94.todayletters.core.ReplyMode.ALOUD -> R.string.step_aloud
        io.github.graviton94.todayletters.core.ReplyMode.CONSTELLATION -> R.string.step_reply
        io.github.graviton94.todayletters.core.ReplyMode.CHECK -> R.string.step_check
        io.github.graviton94.todayletters.core.ReplyMode.TALK -> R.string.step_talk
    }) } + stringResource(R.string.step_plate)
    Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s1)) {
        dots.forEachIndexed { k, st ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(if (st == Journey.Step.NOW) 12.dp else 10.dp).then(when (st) {
                    Journey.Step.DONE -> Modifier.background(p.giltText, CircleShape)
                    Journey.Step.NOW -> Modifier.background(p.ink, CircleShape)
                    Journey.Step.SKIPPED -> Modifier.border(1.5.dp, p.giltText, CircleShape)
                    else -> Modifier.border(1.dp, p.hideInk, CircleShape)
                }))
                // 이름표는 한 줄 (‘따라 읽기’ 가 두 줄로 꺾이지 않게: 칸보다 조금 넓으면 가운데에서 양쪽으로)
                Text(labels.getOrElse(k) { "" }, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (st == Journey.Step.TODO) p.hideInk else p.inkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true))
            }
        }
    }
    when {
        !pr.done -> {
            val next = modes.firstOrNull { it !in pr.replied && it !in pr.skipped }
            Primary(if (pr.shown < l.messages.size) stringResource(R.string.today_open)
                else next?.let { stringResource(R.string.room_next, stringResource(modeLabel(it))) } ?: stringResource(R.string.room_done_today), small = true, shine = true) { s.open(id, c, l, Route.Inbox) }
        }
        else -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.journey_letter_done), style = Type.small.ui(), color = p.giltText, modifier = Modifier.weight(1f))
                val redo = pr.toRedo.filter { it in modes }
                TextLink(if (redo.isNotEmpty()) stringResource(R.string.journey_redo, redo.size) else stringResource(R.string.today_reread)) { s.open(id, c, l, Route.Inbox) }
            }
            NextLetterLine(s, id)
        }
    }
}

/** 와 있는 다른 편지 한 줄: 편지 번호 · 날짜 · 못 읽음 (열어 본 편지는 읽는 중) ›. 누르면 그 편지로. */
@Composable
private fun WaitingRow(s: AppState, id: String, work: io.github.graviton94.todayletters.data.Work, c: String, l: io.github.graviton94.todayletters.core.Letter,
                       letters: List<io.github.graviton94.todayletters.core.Letter>) {
    val p = Ink.palette
    val head = stringResource(R.string.letter_n, roman(letters.indexOf(l) + 1)) + " · " + (l.status?.get(uiLang()) ?: dateLine(l.date, l.place))
    val started = s.progress(id, c, l.id).shown > 0
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable { s.open(id, c, l, Route.Inbox) }, contentAlignment = Alignment.CenterStart) {
        Text(stringResource(if (started) R.string.today_row_reading else R.string.today_row_unread, head), style = Type.body.ui(), color = p.ink, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * 다른 작품 소식 (시안 newletter A+B): 한 작품이면 “볼프강에게서도 새 편지 1통이 왔어요 ›” (그 작품의 오늘로), 여럿이면 “다른 작품에 새 편지 N통 ›” (서재로).
 * 새 편지 (열지 않은) 가 없고 읽는 중인 것만 있으면 ‘읽는 중’. 바탕과 구분되는 금빛 칸 + 금테 · 금빛 글씨, 새 편지면 숨 쉬는 점과 한 번 내려앉기.
 */
@Composable
private fun OthersLine(s: AppState, id: String) {
    val p = Ink.palette
    val others = s.othersWaiting(id)
    if (others.isEmpty()) return
    val one = others.singleOrNull()
    val fresh = others.flatMap { it.fresh }
    val name = one?.let { s.work(it.id).name[uiLang()] }.orEmpty()
    val text = when {
        one != null && one.fresh.size == 1 -> stringResource(R.string.today_other_one, name)
        one != null && one.fresh.isNotEmpty() -> stringResource(R.string.today_other_one_n, name, one.fresh.size)
        one != null -> stringResource(R.string.today_other_reading, name)
        fresh.isNotEmpty() -> stringResource(R.string.today_other_many, fresh.size)
        else -> stringResource(R.string.today_other_many_reading)
    }
    Row(Modifier.fillMaxWidth().arrival(s, "today", fresh).heightIn(min = 44.dp).background(p.gilt.copy(alpha = if (p.paper.luminance() < 0.5f) 0.2f else 0.16f).compositeOver(p.paper))
        .border(1.dp, p.giltText).pressable { if (one != null) s.enter(one.id) else s.go(Route.Library) }.padding(horizontal = Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (fresh.isNotEmpty()) GiltDot(p.giltText)
        Text(text, style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.giltText, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** 오늘 화면 맨 위 한 줄: 남은 편지 · 복습 수, 여정을 마쳤으면 체크. 할 것도 한 것도 없으면 비운다. */
@Composable
private fun TodayLine(j: AppState.JourneyView) {
    val p = Ink.palette
    val letter = if (j.letter == Journey.Step.TODO || j.letter == Journey.Step.NOW) 1 else 0
    val review = if (j.review == Journey.Step.TODO || j.review == Journey.Step.NOW) j.reviewCount.let { (n, total) -> (total - n).coerceAtLeast(0) } else 0
    when {
        j.complete -> Text(stringResource(R.string.today_line_done), style = Type.small.ui(), color = p.giltText, maxLines = 1)
        letter + review > 0 -> Text(stringResource(R.string.today_line, letter, review), style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
    }
}

/**
 * 오늘 편지를 마친 카드 아래: 다음 편지가 언제 오는지 (도착 알림 시각, 알림이 꺼져 있으면 ‘알림 켜기’) ·
 * 상점에서 내일 편지를 미리 열 수 있으면 그 링크. 다음 편지가 없거나 잠긴 장이면 말하지 않는다.
 */
@Composable
private fun NextLetterLine(s: AppState, id: String) {
    val p = Ink.palette
    if (s.waiting(id) > 0 || !s.moreComing(id)) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val posts = io.github.graviton94.todayletters.data.Notices.canPost(ctx)
    val on = s.seriesSettings(id).arrivalNotice && posts
    // 권한을 받으면 켜고, 거절돼 있으면 (다시 묻지 못하면) 앱 알림 설정 화면으로
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) s.noticeOn()
        else runCatching {
            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) stringResource(R.string.today_next_at, noticeTime(s.store.noticeHour.coerceIn(0, 23), s.store.noticeMinute.coerceIn(0, 59)))
            else stringResource(R.string.today_next_day), style = Type.small.ui(), color = p.inkSoft, maxLines = 1, modifier = Modifier.weight(1f))
        if (!on) TextLink(stringResource(R.string.notice_turn_on)) {
            if (!posts && android.os.Build.VERSION.SDK_INT >= 33) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) else s.noticeOn()
        }
    }
    // 상점의 ‘내일 편지 미리 열기’를 살 수 있을 때만
    if (s.canBuy(io.github.graviton94.todayletters.core.Spend.EARLY_LETTER, id)) TextLink(stringResource(R.string.spend_early) + " ›") { s.go(Route.Shop) }
}

/** 무료 장 끝이 가까우면 (남은 무료 편지 1~3통) 미리 한 줄: 잠긴 장에서 갑자기 구매 화면이 뜨지 않게. */
@Composable
internal fun FreeLeftLine(s: AppState, id: String, modifier: Modifier = Modifier) {
    val (n, lastFree) = s.freeLeft(id) ?: return
    if (n !in 1..3) return
    Text(stringResource(R.string.free_left, roman(lastFree), n), style = Type.small.ui(), color = Ink.palette.giltText, maxLines = 1, modifier = modifier)
}

/** 복습할 것이 없을 때의 한 줄: 카드가 없으면 편지부터, 카드는 있는데 아직 한 번도 복습하지 않았으면 첫 복습은 내일부터. */
internal fun reviewNone(cards: List<io.github.graviton94.todayletters.core.Card>): Int = when {
    cards.isEmpty() -> R.string.review_empty
    cards.all { it.seen == 0 } -> R.string.review_first_tomorrow
    else -> R.string.journey_review_none
}

/** 그림 썸네일: 받은 그림은 원래 색, 아직이면 흐린 흑백. */
@Composable
fun PlateThumb(file: String, unlocked: Boolean, modifier: Modifier) {
    val bmp = rememberPicture("plates/$file", 4).bmp
    if (bmp == null) Box(modifier.background(Ink.palette.hide)) else Image(
        bmp, null, modifier, contentScale = ContentScale.Crop,
        colorFilter = if (unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        alpha = if (unlocked) 1f else 0.35f,
    )
}


/** 조용한 글자 링크 (밑줄, 흐린 색). 누르는 자리는 44dp. */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit) {
    val p = Ink.palette
    Box(Modifier.heightIn(min = 44.dp).pressable { onClick() }, contentAlignment = Alignment.CenterEnd) {
        Text(text, style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.inkSoft, maxLines = 1)
    }
}

/**
 * 기억 확인 (F7): ‘내 것’이 된 지 오래된 낱말 하나를 툭. 낱말을 보고 뜻을 떠올린 뒤 펼쳐서 스스로 판정.
 * 기억났어요 → 일정 그대로, 가물가물 → 두 단계 내려가 내일 다시. 복습 탭에.
 */
@Composable
internal fun SurpriseCard(s: AppState, c: io.github.graviton94.todayletters.core.Card) {
    val p = Ink.palette
    val found = remember(c.key) { s.cardWord(c.key) } ?: return
    val (w, _, word) = found
    val view = s.room(w.series.id)
    var open by remember(c.key) { mutableStateOf(false) }
    val audio = s.wordAudio(found, view.learn)
    Column(Modifier.fillMaxWidth().border(1.dp, p.giltText).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(stringResource(R.string.surprise_title), style = Type.small.ui(), color = p.inkSoft)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(word.text[view.learn], style = Type.title.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display).of(view.learn), color = p.ink, modifier = Modifier.weight(1f))
            if (audio.isNotEmpty()) ListenButtons(play = { sp -> s.narrator.play(audio, sp) }, ring = true)
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
