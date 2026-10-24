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
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.core.Wall
import io.github.graviton94.todayletters.core.Levels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first

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
 * 편지 완료 (시안 rw2 3 · ②): 그림이 주인공. 그날 편지의 그림이 화면 가득 천천히 물러나고 (9초), 아래에 미술관 벽 라벨처럼
 * 머리말 (작품의 말 “Lettre I · 마침”) · 제목 (원어) · 번역 제목 · 작가 · 해 · 곳 (데이터에 있는 것만) 이 차례로 뜬다.
 * 가는 금선 봉인 (장 번호) 이 흐린 데서 또렷해지며 진동 한 번. 셈 한 줄 (+우표 · +낱말 카드 · 갤러리 n / N), 등급 · 레벨은 그 아래 한 줄.
 * 큰 단추 하나 “오늘로 · 복습 3장 · 기초 낱말 2개” (맺음이 있으면 맺음) → 그림이 오늘 맨 위 그림 칸으로 옮겨 가고 오늘의 할 일에 체크.
 * 작게 “그림 속 산책”. 뒤로 가면 오늘로. 보상 층 (자료 · 메달) 이 떠 있으면 그것이 걷힌 뒤에 라벨이 뜬다.
 */
@Composable
fun Done(s: AppState, room: Route.Letter) {
    val (chapter, letter) = s.letterOf(room)
    val view = s.room(room.series)
    val work = s.work(room.series)
    val ui = uiLang()
    val still = s.reducedMotion
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val plates = work.chapters.flatMap { it.letters }
    val got = work.chapters.sumOf { c -> c.letters.count { s.progress(room.series, c.id, it.id).done } }
    val pl = letter.plate
    val image = pl?.image?.takeIf { it.isNotBlank() }?.let { "plates/$it" }
    val art = rememberBounds()
    // 편지 끝 흐름 (시안 하루 흐름): 이 화면 하나. 알림 허락은 다음에 오늘에 올 때 (Inbox), 튜토리얼 끝은 이 화면 안의 한 줄
    val graduating = remember { !s.tutorialDone }
    LaunchedEffect(graduating) { if (graduating) s.graduate() }
    // 보상 층 (자료 · 메달) 이 걷힌 뒤에 라벨 시계가 돈다
    var open by remember { mutableStateOf(!s.covered || still) }
    LaunchedEffect(Unit) { s.uncovered(); open = true }
    val clock by rememberClock(room, still, 3200, start = open)
    val zoom by rememberClock(room to "zoom", still, 9000)
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        s.cueTier(io.github.graviton94.todayletters.core.Tier.PAINTING)
        if (!still) androidx.compose.runtime.snapshotFlow { clock }.first { it >= 2300 }
        if (s.app.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    val outro = work.outro != null && s.seriesDone(room.series)
    val plan = s.todayPlan(room.series)
    fun home() {
        if (image != null && !outro) {
            s.landing = s.key(room.series, chapter, letter.id)
            s.flights.launch(image, art.rect, "today-pic", still, alignY = 0.3f, source = "wall")
        }
        if (outro) s.openOutro(room.series) else s.go(Route.Inbox)
    }
    androidx.activity.compose.BackHandler { s.go(Route.Inbox) }
    val tally = s.letterTally?.takeIf { it.room == room }
    BoxWithConstraints(Modifier.fillMaxSize().background(Museum.wall)) {
        val h = maxHeight
        // 그림: 화면 가득, 9초 동안 1.12 → 1 로 물러난다. 아래 3할부터 벽빛에 스민다
        Box(Modifier.fillMaxSize().measured(art).flightSource(s, "wall").clipToBounds()) {
            if (image != null) AssetImage(image, Modifier.fillMaxSize().graphicsLayer {
                val k = 1.12f - 0.12f * (zoom / 9000f).coerceIn(0f, 1f); scaleX = k; scaleY = k
            }, sample = 1, alignment = androidx.compose.ui.BiasAlignment(0f, -0.4f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Museum.wall.copy(alpha = 0.15f), 0.3f to Color.Transparent, 0.58f to Museum.wall.copy(alpha = 0.78f), 0.74f to Museum.wall, 1f to Museum.wall)))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().heightIn(min = h).navigationBarsSafe(), verticalArrangement = Arrangement.Bottom) {
            Spacer(Modifier.height(h * 0.36f))
            // 벽 라벨
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Column(Modifier.fillMaxWidth().padding(end = 56.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(Wall.letterNo(work.series.original, plates.indexOf(letter) + 1) + " · " + stringResource(R.string.wall_done), Museum.gilt,
                        Modifier.rise(win(clock, 600, 900)))
                    if (pl != null) {
                        val orig = pl.title[work.series.original]
                        Text(orig, style = Type.title.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic, fontSize = 30.sp, lineHeight = 33.sp).of(work.series.original),
                            color = Museum.ink, modifier = Modifier.rise(win(clock, 800, 900)))
                        val tr = pl.title[view.read].takeIf { it.isNotBlank() && it != orig }
                        Text(if (tr != null) stringResource(R.string.wall_hung, tr) else stringResource(R.string.wall_hung_only),
                            style = Type.small.ui().copy(fontSize = 13.sp), color = Museum.soft, modifier = Modifier.rise(win(clock, 1000, 900)))
                        val artist = pl.credit.ifBlank { if (pl.kind.isEmpty()) work.kit.fullName[ui].ifBlank { work.fullName } else "" }
                        val meta = Wall.meta(artist, pl.date)
                        if (meta.isNotEmpty()) Text(meta, style = Type.small.ui().copy(fontSize = 12.sp), color = Museum.soft, modifier = Modifier.rise(win(clock, 1100, 900)))
                    } else Text(stringResource(R.string.done_title), style = Type.title.ui(), color = Museum.ink, modifier = Modifier.rise(win(clock, 800, 900)))
                }
                // 가는 금선 봉인 (장 번호): 흐린 데서 또렷해진다
                val seal = win(clock, 1300, 1100)
                Box(Modifier.align(Alignment.TopEnd).padding(top = 0.dp).size(44.dp).graphicsLayer {
                    alpha = seal; val k = 1.25f - 0.25f * seal; scaleX = k; scaleY = k
                    val r = (1f - seal) * 4f * density
                    renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(r, r) else null
                }.drawBehind {
                    drawCircle(Museum.gilt, size.minDimension / 2 - 0.5.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawCircle(Museum.gilt.copy(alpha = 0.5f), size.minDimension / 2 - 3.5.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                    Text(roman(room.chapter), style = Type.title.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic, fontSize = 21.sp, lineHeight = 21.sp), color = Museum.gilt)
                }
            }
            // 셈: +우표 · +낱말 카드 · 갤러리 n / N (화폐는 이 편지로 받은 것, 다시 보기면 칸 없음)
            val k = win(clock, 1500, 900)
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).rise(k).drawBehind {
                drawLine(Museum.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(Museum.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }) {
                val cells = listOfNotNull(
                    tally?.takeIf { it.gain > 0 }?.let { "+${(it.gain * win(clock, 1700, 900)).toInt()}" to work.kit.currency[ui] },
                    "+${letter.words.size}" to stringResource(R.string.done_stat_cards),
                    "$got / ${plates.size}" to stringResource(R.string.tab_gallery),
                )
                cells.forEachIndexed { i, (v, l) ->
                    Column(Modifier.weight(1f).then(if (i > 0) Modifier.drawBehind { drawLine(Museum.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }.padding(start = 14.dp) else Modifier)
                        .padding(vertical = 12.dp).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(v, style = Type.title.copy(fontFamily = Faces.display, fontSize = 24.sp, lineHeight = 26.sp), color = Museum.ink, maxLines = 1)
                        Text(l, style = Type.small.ui().copy(fontSize = 11.sp), color = Museum.soft, maxLines = 1)
                    }
                }
            }
            // 한 줄씩 (있는 것만): 오른 등급 · 레벨, 건너뛴 걸음, 다음 편지, 천천히 듣기, 튜토리얼 끝, 다음 작품
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).rise(win(clock, 1800, 900)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tally?.grade?.let { g ->
                    Text(if (g.to.band != g.from.band) stringResource(R.string.gradeup_became, gradeName(g.to.band)) else stringResource(R.string.gradeup_title, stringResource(langLabel(g.lang))),
                        style = Type.small.ui(), color = Museum.gilt, maxLines = 1)
                }
                tally?.level?.let { lv ->
                    val t = Levels.title(s.medalBook.levels, lv.to, ui)
                    Text(stringResource(R.string.reward_level_line, lv.to) + " · " + t, style = Type.small.ui(), color = Museum.gilt, maxLines = 1)
                }
                val modes = s.letterModes(room.series, chapter, letter)
                val pr = s.progress(room.series, chapter, letter.id)
                val skipped = pr.toRedo.count { it in modes }
                if (skipped > 0) Text(stringResource(R.string.finish_skipped, skipped), style = Type.small.ui(), color = Museum.soft, maxLines = 1)
                val waiting = s.waiting(room.series)
                Text(when {
                    waiting > 0 -> stringResource(R.string.done_next_now)
                    got >= plates.size && work.outro != null -> stringResource(R.string.done_series_end)
                    got >= plates.size -> stringResource(R.string.done_chapter_end)
                    s.seriesSettings(room.series).arrivalNotice -> stringResource(R.string.finish_next_at, noticeTime(s.store.noticeHour.coerceIn(0, 23), s.store.noticeMinute.coerceIn(0, 59)))
                    else -> stringResource(R.string.done_next_tomorrow)
                }, style = Type.small.ui(), color = Museum.soft)
                val key = s.key(room.series, chapter, letter.id)
                if (s.finishSheet == key && s.slowNote == key) Text(stringResource(R.string.slow_note, s.listenedToday(view.learn).coerceAtLeast(1)), style = Type.small.ui(), color = Museum.gilt)
                if (graduating) Text(stringResource(R.string.graduate_title), style = Type.small.ui(), color = Museum.gilt, maxLines = 1)
                if (s.seriesDone(room.series)) s.suggestedNext(room.series)?.let { nx ->
                    WallLink(stringResource(R.string.finish_suggest, s.work(nx).name[ui])) { s.enter(nx) }
                }
            }
            // 큰 단추 하나: 어디로 가는지 + 거기서 기다리는 것
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 10.dp)) {
                val detail = listOfNotNull(
                    plan.review.takeIf { it > 0 }?.let { stringResource(R.string.go_review_n, it) },
                    plan.basics.takeIf { it > 0 }?.let { stringResource(R.string.go_basics_n, it) },
                ).joinToString(" · ").ifEmpty { null }
                if (outro) GoButton(stringResource(R.string.done_outro), fill = Museum.ink, on = Museum.wall, soft = Color(0xFF6B6255)) { home() }
                else GoButton(stringResource(R.string.done_today), detail, fill = Museum.ink, on = Museum.wall, soft = Color(0xFF6B6255)) { home() }
                // 그림이 이 기기에 없으면 (소리 없이 읽기 · 받지 않음) 걸을 그림도 없다
                if (pl != null && io.github.graviton94.todayletters.data.AssetFiles.has(s.ctx, "plates/${pl.image}"))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WallLink(stringResource(R.string.done_walk)) { s.go(Route.Artwork(room.series, room.letter, room)) } }
            }
        }
        }
    }
}

/** 전시실 벽 위의 조용한 글자 링크 (밑줄, 흐린 색, 누르는 자리 44dp). */
@Composable
private fun WallLink(text: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).pressable { onClick() }, contentAlignment = Alignment.Center) {
        Text(text, style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = Museum.soft, maxLines = 1)
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
