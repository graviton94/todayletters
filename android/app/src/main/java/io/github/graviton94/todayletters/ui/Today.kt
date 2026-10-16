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

    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            TodayLine(j)
            StreakBlock(s, work)
            Hair()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.journey_title), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
                Text("${j.done} / 3", style = Type.heading.copy(fontFamily = Faces.display), color = p.giltText)
            }
            // I 오늘의 편지
            JourneyStep("I", stringResource(R.string.journey_letter), j.letter, last = false) {
                if (tl != null) TodayLetter(s, id, work, tl.first, tl.second, letters)
                else {
                    val upcoming = letters.firstOrNull { l -> s.openable(id).none { it.second == l } }
                    val days = upcoming?.let { s.daysUntil(id, it.id) }
                    Text(stringResource(if (s.doneCount(id) >= letters.size) R.string.chapter_end_next else R.string.today_all_done), style = Type.body.ui(), color = p.ink)
                    Text(stringResource(R.string.next_title) + " · " + when { days == null -> stringResource(R.string.next_none); days <= 1 -> stringResource(R.string.letter_eta); else -> stringResource(R.string.next_in, days) },
                        style = Type.small.ui(), color = p.inkSoft)
                }
            }
            // II 오늘의 복습 (이 작품 카드만, 한 판)
            JourneyStep("II", stringResource(R.string.review_today), j.review, last = false) {
                val (n, total) = j.reviewCount
                when (j.review) {
                    Journey.Step.NONE -> Text(stringResource(reviewNone(s.seriesCards(id))), style = Type.small.ui(), color = p.inkSoft)
                    Journey.Step.DONE -> Text(stringResource(R.string.journey_review_done, total), style = Type.small.ui(), color = p.inkSoft)
                    else -> {
                        Text(stringResource(R.string.review_words_n, total), style = Type.small.ui(), color = p.inkSoft)
                        if (n > 0) Bar(n.toFloat() / total.coerceAtLeast(1))
                        Primary(stringResource(if (n > 0) R.string.journey_review_continue else R.string.review_start), small = true) { s.go(Route.Session()) }
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
}

/** 여정의 한 걸음: 왼쪽 로마 숫자 동그라미 (다 하면 금빛, 할 것이 없으면 점선) · 이름 · 내용. 걸음 사이를 세로 선이 잇는다. */
@Composable
private fun JourneyStep(num: String, title: String, state: Journey.Step, last: Boolean, content: @Composable () -> Unit) {
    val p = Ink.palette
    val done = state == Journey.Step.DONE
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp).then(when (state) {
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
                Text(labels.getOrElse(k) { "" }, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (st == Journey.Step.TODO) p.hideInk else p.inkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    when {
        !pr.done -> {
            val next = modes.firstOrNull { it !in pr.replied && it !in pr.skipped }
            Primary(if (pr.shown < l.messages.size) stringResource(R.string.today_open)
                else next?.let { stringResource(R.string.room_next, stringResource(modeLabel(it))) } ?: stringResource(R.string.room_done_today), small = true) { s.open(id, c, l, Route.Inbox) }
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
    Box(Modifier.heightIn(min = 44.dp).pressable { onClick() }, contentAlignment = Alignment.TopEnd) {
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
            ListenButtons(play = { sp -> s.narrator.play(audio, sp) }, ring = true)
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
