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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val askNotice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        io.github.graviton94.todayletters.data.Notices.schedule(s.ctx)
    }
    var preNotice by remember { mutableStateOf(false) }
    if (preNotice) FullDialog(onDismissRequest = { preNotice = false }) {
        Ask(stringResource(R.string.notice_pre_t), stringResource(R.string.notice_pre_yes), stringResource(R.string.notice_pre_no),
            onYes = { preNotice = false; askNotice.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, onNo = { preNotice = false })
    }
    LaunchedEffect(Unit) {
        t.animateTo(0.35f, androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        if (s.app.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        t.animateTo(1f, androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing))
        // 첫 편지를 끝낸 뒤 한 번: 내일 편지가 오면 알려 줄지 묻는다 (Android 13+). 시스템 창 앞에 앱의 한 줄 먼저
        if (android.os.Build.VERSION.SDK_INT >= 33 && !s.store.notifyAsked) {
            s.store.notifyAsked = true
            preNotice = true
        }
    }
    fun win(a: Float, b: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(((t.value - a) / (b - a)).coerceIn(0f, 1f))
    val stamp = win(0f, 0.35f)
    val modes = s.letterModes(room.series, chapter, letter)
    val pr = s.progress(room.series, chapter, letter.id)
    val waiting = s.waiting(room.series)
    // 다음 편지: 방금 마친 편지 뒤의 첫 안 마친 편지 (앞서 읽다 둔 편지가 끝 부분부터 열리지 않게)
    val next = s.nextLetter(room)

    val plates = work.chapters.flatMap { it.letters }
    val got = work.chapters.sumOf { c -> c.letters.count { s.progress(room.series, c.id, it.id).done } }
    Column(Modifier.fillMaxSize().background(p.paper)) {
        Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            // 그림 공개: 화면 위를 가득 채우고 아래로 종이에 스며든다. 밀랍 도장이 그 경계에 찍힌다.
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                PlateImage(letter.plate?.image.orEmpty(), Modifier.fillMaxSize().graphicsLayer { alpha = win(0.1f, 0.45f); val k = 1.06f - 0.06f * win(0.1f, 0.6f); scaleX = k; scaleY = k })
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x59170F0A), 0.3f to Color.Transparent, 0.65f to Color.Transparent, 1f to p.paper)))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Tokens.Space.s4)) {
                    Caps(stringResource(R.string.story_received, roman(plates.indexOf(letter) + 1)), Color(0xFFF7F0E1), modifier = Modifier.weight(1f), decorative = true)
                    letter.plate?.let { Text(it.date.uppercase(), style = Type.caps, color = Color(0xFFF7F0E1)) }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).size(92.dp).graphicsLayer {
                        val k = 1.8f - 0.8f * stamp
                        scaleX = k; scaleY = k; alpha = stamp; rotationZ = -8f - 10f * (1f - stamp)
                    }.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFC25546), Color(0xFF9A3027), Color(0xFF6E1C15))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Portrait(work.portrait, work.fullName, 58.dp) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Text(stringResource(R.string.done_title), style = Type.title.ui(), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = win(0.3f, 0.5f) })
                letter.plate?.let { pl ->
                    // 원제와 번역은 두 줄 (v20). 같은 시절을 보여 주려고 곁에 둔 그림이면 ‘시대의 그림’
                    if (pl.era) EraTag()
                    Text(pl.title[work.series.original], style = Type.small.of(work.series.original), color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (pl.title[view.read] != pl.title[work.series.original]) Text(pl.title[view.read], style = Type.small.of(view.read), color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                // 오늘 한 일 (작은 칩)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 실제로 한 걸음만 체크, 건너뛴 걸음은 ‘나중에’ (복습 탭 · 편지 목록에서 다시 하기)
                    (listOf(null to R.string.step_read_short) + modes.map { it to when (it) { io.github.graviton94.todayletters.core.ReplyMode.MATCH -> R.string.step_match; io.github.graviton94.todayletters.core.ReplyMode.ALOUD -> R.string.step_aloud; io.github.graviton94.todayletters.core.ReplyMode.CHECK -> R.string.step_check; io.github.graviton94.todayletters.core.ReplyMode.TALK -> R.string.step_talk; else -> R.string.step_reply } }).forEachIndexed { k, (m, label) ->
                        val skipped = m != null && m in pr.skipped && m !in pr.replied
                        Row(Modifier.graphicsLayer { alpha = win(0.45f + k * 0.07f, 0.55f + k * 0.07f) }.background(p.leaf).border(Tokens.Stroke.hair, p.hair).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            CheckMark(!skipped, 14.dp)
                            Text(stringResource(label) + if (skipped) " · " + stringResource(R.string.step_later) else "", style = Type.small.ui(), color = if (skipped) p.inkSoft else p.ink)
                        }
                    }
                }
                Row(Modifier.graphicsLayer { alpha = win(0.7f, 0.9f) }, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s6)) {
                    if (s.streak > 0) Stat("${s.streak}", stringResource(R.string.done_stat_streak))
                    Stat("+${letter.words.size}", stringResource(R.string.done_stat_cards))
                    Stat("$got/${plates.size}", stringResource(R.string.tab_gallery))
                }
                Text(stringResource(if (waiting > 0) R.string.done_next_now else if (got >= plates.size && work.outro != null) R.string.done_series_end else if (got >= plates.size) R.string.done_chapter_end else R.string.done_next_tomorrow), style = Type.small.ui(), color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Column(Modifier.background(p.paper).navigationBarsPadding().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            // 그림이 이 기기에 없으면 (소리 없이 읽기 · 받지 않음) 걸을 그림도 없다
            if (letter.plate != null && io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, "plates/${letter.plate!!.image}")) Secondary(stringResource(R.string.done_walk)) { s.go(Route.Artwork(room.series, room.letter, room)) }
            if (next != null) Primary(stringResource(R.string.done_open_next), shine = true) { s.open(room.series, next.first, next.second, Route.Inbox) }
            else if (work.outro != null && s.seriesDone(room.series)) Primary(stringResource(R.string.done_outro), shine = true) { s.openOutro(room.series) }
            else Primary(stringResource(R.string.done_home), shine = true, onClick = home)
        }
    }
    // 튜토리얼 끝 (편지 I 을 마친 뒤 한 번): “이제 혼자서도 괜찮아요” → 그 뒤로는 도움말이 저절로 뜨지 않는다
    if (!s.tutorialDone) Graduate(s)
    // 오늘의 편지 마침 한 장: 처음 마쳤을 때 한 번 (건너뛴 걸음이 있어도), 도장이 다 찍힌 뒤
    else if (s.finishSheet == s.key(room.series, chapter, letter.id) && t.value >= 1f) FinishSheet(s, room)
}

/**
 * 오늘의 편지를 마쳤어요: 밀랍 도장 · 편지 번호와 날짜 · 건너뛴 걸음은 복습에서 · 다음 편지가 언제 오는지 (알림 시각 그대로).
 * 서재로 (채운 단추) · 이 편지 처음부터 다시 보기. 닫으면 다시 뜨지 않는다 ([AppState.finishSheet]).
 */
@Composable
fun FinishSheet(s: AppState, room: Route.Letter) {
    val p = Ink.palette
    val (chapter, letter) = s.letterOf(room)
    val work = s.work(room.series)
    val pr = s.progress(room.series, chapter, letter.id)
    val skipped = pr.toRedo.count { it in s.letterModes(room.series, chapter, letter) }
    val n = work.chapters.flatMap { it.letters }.indexOf(letter) + 1
    val close = { s.finishSheet = null }
    androidx.activity.compose.BackHandler(onBack = close)
    // 가운데 한 장 (아래 완료 화면의 제목 · 도장이 겹쳐 보이지 않게 짙은 막 위에)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).pressable(haptic = false, onClick = close), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.86f).background(p.paper).border(1.dp, p.giltText).pressable(haptic = false) { }
                .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Box(
                Modifier.size(64.dp).graphicsLayer { rotationZ = -8f }.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFC25546), Color(0xFF9A3027), Color(0xFF6E1C15))), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Portrait(work.portrait, work.fullName, 40.dp) }
            Text(stringResource(R.string.finish_title), style = Type.title.ui(), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.finish_meta, roman(n), dateLine(letter.date, letter.place)),
                style = Type.small.ui(), color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            if (skipped > 0) Text(stringResource(R.string.finish_skipped, skipped),
                style = Type.small.ui(), color = p.giltText, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            val next = when {
                s.seriesDone(room.series) -> stringResource(R.string.finish_series_done)
                s.waiting(room.series) > 0 -> stringResource(R.string.done_next_now)
                s.seriesSettings(room.series).arrivalNotice -> stringResource(R.string.finish_next_at, noticeTime(s.store.noticeHour.coerceIn(0, 23), s.store.noticeMinute.coerceIn(0, 59)))
                else -> stringResource(R.string.finish_next_day)
            }
            Text(next, style = Type.body.ui(), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = Tokens.Space.s2))
            // 천천히 듣는 2주의 안내 (첫 편지 · 3일째에 한 번)
            SlowNoteLine(s, room.series, s.key(room.series, chapter, letter.id))
            // 작품을 다 마쳤으면: 왜에 맞춘 다음 작품 한 줄
            if (s.seriesDone(room.series)) s.suggestedNext(room.series)?.let { nx ->
                TextLink(stringResource(R.string.finish_suggest, s.work(nx).name[uiLang()])) { close(); s.enter(nx) }
            }
            Primary(stringResource(R.string.finish_today), shine = true) { close(); s.go(Route.Inbox) }
            Secondary(stringResource(R.string.finish_again)) { s.replayLetter(room.series, chapter, letter, Route.Inbox) }
        }
    }
}

/** 튜토리얼을 맺는 한 장. */
@Composable
private fun Graduate(s: AppState) {
    val p = Ink.palette
    androidx.activity.compose.BackHandler { s.graduate() }
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false) { }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.86f).background(p.paper).border(1.dp, p.giltText).padding(Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.graduate_title), style = Type.title.ui(), color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.graduate_body), style = Type.body.ui(), color = p.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // 첫 편지를 마치면 천천히 듣기 안내 한 줄 (튜토리얼 끝 한 장에)
            s.slowNote?.let { k -> SlowNoteLine(s, s.current, k) }
            Primary(stringResource(R.string.graduate_go), shine = true) { s.graduate() }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val p = Ink.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.numeral, color = p.giltText)
        Text(label, style = Type.small.ui(), color = p.inkSoft)
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
                    // 한 줄 소개: 작품 키트에서 (series.json kit.about, F21)
                    w.kit.about[uiLang()].takeIf { it.isNotBlank() }?.let { Text(it, style = Type.small.ui(), color = p.inkSoft) }
                }
            }
            Rule()
            Caps("Chronologie", p.giltText, decorative = true)
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
