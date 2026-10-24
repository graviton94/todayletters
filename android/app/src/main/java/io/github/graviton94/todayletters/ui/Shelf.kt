package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp

/** 아직 오지 않은 시리즈 (서재의 ‘곧 도착’ 칸): 초상 파일과 이름. */
private val upcoming = listOf("mozart" to R.string.up_mozart, "napoleon" to R.string.up_napoleon, "basho" to R.string.up_basho)

/**
 * 서재: 앱을 열면 여기. 위에 전체 기록 (이어 읽기 · 모은 자료 · 전체 이정표), 아래에 시리즈 카드.
 * 카드는 시리즈마다 그 시리즈의 어두운 빛깔과 대표 그림이라, 한눈에 서로 다르다.
 */
@Composable
fun LibraryTab(s: AppState) {
    val p = Ink.palette
    s.version
    LaunchedEffect(Unit) { s.settle() }
    val pieces = s.works.sumOf { s.ownedCount(it.series.id) }
    val cabinet = cabinetMedals(s)
    val medalsGot = cabinet.count { it.id in s.medalsOwned }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1, top = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Caps("LINGUA MUSEUM", p.inkSoft, small = true, decorative = true, modifier = Modifier.weight(1f))
            // 나의 기록 (칭호 · 메달 · 이어 읽기, 모든 작품 합산)
            Box(Modifier.heightIn(min = 44.dp).pressable { s.go(Route.Record) }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lib_overall), style = Type.small.ui(), color = p.ink, maxLines = 1,
                    modifier = Modifier.border(1.dp, p.line).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            HeaderIcons(s)
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Text(stringResource(R.string.lib_title), style = Type.title.ui(), color = p.ink)
            // 칭호 · 레벨 · 요약 (누르면 나의 기록)
            RankLine(s, Modifier.fillMaxWidth().pressable { s.go(Route.Record) })
            Row(Modifier.fillMaxWidth().pressable { s.go(Route.Record) }) {
                listOf("${s.streak}" to R.string.lib_stat_streak, "$pieces" to R.string.lib_stat_pieces, "$medalsGot / ${cabinet.size}" to R.string.lib_stat_ach).forEach { (v, l) ->
                    Column(Modifier.weight(1f)) {
                        Text(v, style = Type.title.copy(fontFamily = Faces.display), color = p.ink)
                        Text(stringResource(l), style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            }
            // 새 편지가 온 작품 (읽던 작품 · 한 번도 열지 않은 편지) 이 맨 위 큰 카드, 나머지는 작고 차분하게 (시안 rw2 6 · ④)
            val hero = if (s.tutorialDone) s.works.firstOrNull { w -> s.startedCount(w.series.id) > 0 && s.freshLetter(w.series.id) != null } else null
            if (hero != null) {
                val fresh = s.works.sumOf { w -> if (s.startedCount(w.series.id) > 0) s.unreadKeys(w.series.id).size else 0 }
                Text(stringResource(R.string.lib_new_today, fresh), style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
                HeroCard(s, hero)
                s.works.filter { it != hero }.forEach { w -> SmallSeriesCard(s, w) }
            }
            // 처음 설치 (튜토리얼): 첫 작품 카드를 강조하고, 배울 말은 그 카드에서 고른다 → 바로 편지 I
            else s.works.forEach { w -> if (!s.tutorialDone && w.series.id == s.tutorialSeries && s.needsWelcome(w.series.id)) TutorialCard(s, w) else SeriesCard(s, w) }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                upcoming.filter { (id, _) -> s.works.none { it.series.id == id } }.forEach { (file, name) ->
                    Column(Modifier.weight(1f).heightIn(min = 96.dp).dashed(p.hair).padding(vertical = Tokens.Space.s3),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.graphicsLayer { alpha = 0.5f }) { Portrait("$file.webp", stringResource(name), 32.dp) }
                        Text(stringResource(name), style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.lib_soon), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk)
                    }
                }
            }
            Spacer(Modifier.height(Tokens.Space.s5))
            // 서재 아래 업데이트 띠가 떠 있으면 그 높이만큼 아래 여백 (마지막 카드가 띠에 가리지 않게, 시안 하루 흐름 ⑤)
            if (s.updateBand != io.github.graviton94.todayletters.core.UpdateBand.Hidden) Spacer(Modifier.height(UPDATE_BAND_SPACE))
        }
    }
}

/** 업데이트 띠 (알림 · 받는 중 · 다 받음) 의 가장 큰 높이 + 바깥 여백. */
private val UPDATE_BAND_SPACE = 168.dp

/** 서재의 시리즈 카드: 대표 그림 위에 그 시리즈의 빛깔이 왼쪽에서 번진다. */
@Composable
private fun SeriesCard(s: AppState, w: Work) {
    val t = darkTone(w)
    val id = w.series.id
    val total = w.chapters.sumOf { it.letters.size }
    val done = s.doneCount(id)
    val unread = s.unread(id)
    val due = s.dueCards(id).size
    Box(Modifier.fillMaxWidth().height(228.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(t.paper).pressable { s.go(Route.Enter(id)) }) {
        AssetImage(w.kit.hero, Modifier.fillMaxSize(), sample = 2, alignment = Alignment.CenterEnd)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to t.paper, 0.32f to t.paper.copy(alpha = 0.9f), 0.62f to t.paper.copy(alpha = 0.5f), 1f to t.paper.copy(alpha = 0.1f))))
        Column(Modifier.fillMaxSize().padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.kit.place, style = Type.capsSm, color = t.accent, modifier = Modifier.weight(1f), maxLines = 1)
                // 작품 주제 꼬리표 (series.json kit.tag: 미술관 · 오페라)
                val tag = w.kit.tag[uiLang()]
                if (tag.isNotBlank()) Text(tag, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = t.paper, maxLines = 1,
                    modifier = Modifier.background(t.accent).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.border(1.5.dp, t.accent, CircleShape).padding(1.5.dp)) { Portrait(w.portrait, w.fullName, 40.dp) }
                Text("${w.name[uiLang()]} → ${w.recipient[uiLang()]}", style = Type.heading.ui(), color = t.ink, modifier = Modifier.weight(1f))
            }
            // 햇수 · 배우는 말 (언어는 칩 하나에만) (눌러서 바꾸기: 진도는 언어마다 따로)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(w.years, style = Type.small.ui(), color = t.soft, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (s.store.learnChosen(id) || s.startedCount(id) > 0) LearnChip(s, id, ink = t.ink, line = t.accent)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.lib_letters, done, total), style = Type.small.ui(), color = t.ink)
                    // 진도: 읽는 장 · 마지막으로 읽은 날 (처음이면 서문부터)
                    Text(libWhere(s, w), style = Type.small.ui(), color = t.soft, maxLines = 1)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(t.ink.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).height(3.dp).background(t.accent))
                    }
                }
                Spacer(Modifier.weight(0.2f))
                // 읽던 작품: 새 편지 (열지 않은) 는 금빛으로 채운 표 (숨 쉬는 점 · 처음 한 번 내려앉기), 읽는 중인 것만 있으면 ‘읽는 중’
                val started = s.startedCount(id) > 0
                val fresh = if (started) s.unreadKeys(id) else emptyList()
                val reading = started && fresh.isEmpty() && s.waitingLetters(id).isNotEmpty()
                if (fresh.isNotEmpty()) Row(Modifier.arrival(s, "lib", fresh).background(Gold).padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GiltDot(GoldInk, 6.dp)
                    Text(stringResource(R.string.lib_new, fresh.size), style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = GoldInk)
                } else {
                    val badge = when { reading -> stringResource(R.string.lib_reading); !started && unread > 0 -> stringResource(R.string.lib_arrived); due > 0 -> stringResource(R.string.lib_due, due); else -> null }
                    if (badge != null) Text(badge, style = Type.small.ui(), color = t.paper, modifier = Modifier.background(t.accent).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

/**
 * 새 편지가 온 작품의 큰 카드 (시안 rw2 6): 그 작품의 표지 그림 위, 카드 위쪽에서 크림색 편지지 “Lettre II · 오늘 08:00 도착” 이 내려와 꽂히고
 * (1.1초, 0.5초 뒤 · 종이 소리), 빛이 한 번 카드를 지나간다. 아래 ‘편지 열기 · Lettre II’ → 카드의 그림이 편지 도착 맨 위로 펴진다.
 * 편지지가 내려오는 것은 편지마다 한 번 (저장소에 기억), 그 뒤로는 꽂혀 있는 채로. 카드를 누르면 예전처럼 그 작품으로.
 */
@Composable
private fun HeroCard(s: AppState, w: Work) {
    val id = w.series.id
    val f = s.freshLetter(id) ?: return
    val still = s.reducedMotion
    val ui = uiLang()
    val k = s.key(id, f.first, f.second.id)
    val first = remember(k) { !s.store.dropSeen("hero|$k") }
    val clock by rememberClock(k, still || !first, 3000)
    LaunchedEffect(k) { s.store.markDropSeen(listOf("hero|$k")); if (first && !still) { kotlinx.coroutines.delay(500); s.cuePage() } }
    val card = rememberBounds()
    val density0 = androidx.compose.ui.platform.LocalDensity.current
    val no = io.github.graviton94.todayletters.core.Wall.letterNo(w.series.original, f.third)
    val day = s.arrivedOn(id)
    val ago = if (day == null) 0L else java.time.LocalDate.now().toEpochDay() - day
    val time = noticeTime(s.store.noticeHour.coerceIn(0, 23), s.store.noticeMinute.coerceIn(0, 59))
    val arrived = when {
        ago <= 0L -> stringResource(R.string.slip_today, time)
        ago == 1L -> stringResource(R.string.slip_ago, stringResource(R.string.lib_when_yesterday))
        else -> stringResource(R.string.slip_ago, stringResource(R.string.lib_when_days, ago.toInt()))
    }
    fun open() {
        val from = card.rect
        s.openFresh(id)
        // 편지 도착 장면이 바로 뜰 때만 (받기 시트 · 예열 · 이번 장의 소리가 먼저면 날리지 않는다)
        if (s.route is Route.Letter && s.downloadAsk == null)
            s.flights.launch(w.kit.hero, from, "arrival-pic", still, cornerFrom = with(density0) { 16.dp.toPx() }, alignY = 0.25f, handOff = true)
    }
    Box(Modifier.fillMaxWidth().height(300.dp).measured(card).flightSource(s, "hero").clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        .border(1.dp, Color(0x47C9A866), androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Museum.wall).pressable { s.go(Route.Enter(id)) }) {
        AssetImage(w.kit.hero, Modifier.fillMaxSize(), sample = 2, alignment = androidx.compose.ui.BiasAlignment(0f, -0.5f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color(0x000A0908), 0.62f to Color(0xB30A0908), 1f to Color(0xF00A0908))))
        // 빛 한 번
        val sweep = win(clock, 1200, 1600)
        if (sweep > 0f && sweep < 1f) Box(Modifier.fillMaxSize().drawWithContent {
            val x = size.width * (1.3f - 1.8f * sweep)
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x29FFF0D2), Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(x - size.width * 0.25f, 0f), end = androidx.compose.ui.geometry.Offset(x + size.width * 0.25f, size.height)))
        })
        // 편지지: 카드 위쪽에서 내려와 꽂힌다
        val slip = win(clock, 500, 1100)
        Column(Modifier.padding(start = 16.dp).graphicsLayer { translationY = -(1f - slip) * size.height }
            .shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
            .background(Color(0xFFF1EBDF), androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(no, style = Type.title.copy(fontFamily = Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp, lineHeight = 18.sp), color = Color(0xFF2A2520))
            Text(arrived, style = Type.small.ui().copy(fontSize = 11.sp), color = Color(0xFF6B6255), maxLines = 1)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(w.kit.place, Museum.gilt, size = 11.5f)
            Text("${w.name[ui]} → ${w.recipient[ui]}", style = Type.heading.ui(), color = Museum.ink, maxLines = 1)
            Text(stringResource(R.string.lib_new_hero), style = Type.small.ui().copy(fontSize = 12.sp), color = Museum.soft, maxLines = 1)
            Box(Modifier.padding(top = 10.dp).heightIn(min = 44.dp).pressable { open() }, contentAlignment = Alignment.CenterStart) {
                Row(Modifier.background(Museum.ink, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.go_open_letter, no), style = Type.small.ui().copy(fontSize = 13.sp), color = Museum.wall, maxLines = 1)
                    ArrowGlyph(Museum.wall)
                }
            }
        }
    }
}

/** 새 편지가 온 작품이 맨 위에 있을 때의 나머지 작품: 낮은 카드 (표지 그림 위 어둠 한 겹 · 이름 · 진도 한 줄). */
@Composable
private fun SmallSeriesCard(s: AppState, w: Work) {
    val t = darkTone(w)
    val id = w.series.id
    val ui = uiLang()
    val started = s.startedCount(id) > 0
    val fresh = if (started) s.unreadKeys(id).size else 0
    val line = when {
        fresh > 0 -> stringResource(R.string.lib_new, fresh)
        started && s.waitingLetters(id).isNotEmpty() -> stringResource(R.string.lib_reading)
        else -> libWhere(s, w)
    }
    Box(Modifier.fillMaxWidth().height(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(t.paper).pressable { s.go(Route.Enter(id)) }) {
        AssetImage(w.kit.hero, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }, sample = 4, alignment = Alignment.CenterEnd)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to t.paper, 0.45f to t.paper.copy(alpha = 0.85f), 1f to t.paper.copy(alpha = 0.35f))))
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${w.name[ui]} → ${w.recipient[ui]}", style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = t.ink, maxLines = 1)
            Text(line, style = Type.small.ui().copy(fontSize = 12.sp), color = t.soft, maxLines = 1)
        }
    }
}

/** 서재 카드의 ‘새 편지’ 표: 작품 빛깔과 상관없이 금빛 (어두운 카드 위). */
private val Gold = Color(0xFFE2C374)
private val GoldInk = Color(0xFF2A2116)

/** 서재 카드의 진도 한 줄: “VI장 · 3일 전” (다음에 읽을 편지의 장 · 마지막으로 편지를 연 날), 아직 열지 않았으면 “처음 열기 · 서문부터”. */
@Composable
private fun libWhere(s: AppState, w: Work): String {
    val id = w.series.id
    if (s.startedCount(id) == 0) return stringResource(R.string.lib_fresh)
    val next = w.chapters.indexOfFirst { c -> c.letters.any { !s.progress(id, c.id, it.id).done } }.let { if (it < 0) w.chapters.size - 1 else it }
    val day = s.store.lastOpen(s.space(id)).first
    val ago = if (day < 0) -1L else java.time.LocalDate.now().toEpochDay() - day
    val whenText = when {
        ago < 0 -> null
        ago == 0L -> stringResource(R.string.lib_when_today)
        ago == 1L -> stringResource(R.string.lib_when_yesterday)
        else -> stringResource(R.string.lib_when_days, ago.toInt())
    }
    val ch = roman(next + 1)
    return stringResource(R.string.lib_where, ch, whenText ?: "").trimEnd(' ', '·')
}

/**
 * 튜토리얼의 서재 카드 (처음 설치 한 번): 금빛 테로 강조한 첫 작품 카드 안에서 배울 말을 고르고 ‘첫 편지 받기’ → 편지 I.
 */
@Composable
private fun TutorialCard(s: AppState, w: Work) {
    val t = darkTone(w)
    var learn by remember(w.series.id) { mutableStateOf(io.github.graviton94.todayletters.core.Langs.defaultLearn(w.series, s.app.read)) }
    Column(Modifier.fillMaxWidth().border(2.dp, t.accent).padding(3.dp).background(t.paper)) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            AssetImage(w.kit.hero, Modifier.fillMaxSize(), sample = 2, alignment = Alignment.CenterEnd)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to t.paper.copy(alpha = 0.2f), 1f to t.paper)))
            Column(Modifier.align(Alignment.BottomStart).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.tutorial_card_caps), style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = t.paper,
                    modifier = Modifier.background(t.accent).padding(horizontal = 8.dp, vertical = 2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.border(1.5.dp, t.accent, CircleShape).padding(1.5.dp)) { Portrait(w.portrait, w.fullName, 40.dp) }
                    Text(w.kit.fullName[uiLang()].ifBlank { w.fullName }, style = Type.heading.ui(), color = t.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.learn_sheet_q), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = t.ink)
            LearnPicker(s, w, learn, ink = t.ink, soft = t.soft, line = t.ink.copy(alpha = 0.35f), fill = t.ink.copy(alpha = 0.08f)) { learn = it }
            Text(stringResource(R.string.learn_sheet_note), style = Type.small.ui(), color = t.soft)
            Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(t.ink).pressable { s.startTutorial(learn) }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ob_one_go), style = Type.body.ui(), color = t.paper)
            }
        }
    }
}

/**
 * 시리즈로 들어가는 짧은 전환: 책장 넘기는 소리와 함께 카드가 화면 가득 펼쳐지고, 대표 그림 위에서
 * “OO의 세상으로 떠납니다”와 함께 편지 꾸러미를 편 뒤 (불러오기) 저절로 들어간다 (누르는 횟수 하나 줄임, F07). 누르면 바로.
 */
@Composable
fun EnterScreen(s: AppState, id: String) {
    val w = s.work(id)
    val t = darkTone(w)
    val grow = remember { Animatable(if (s.reducedMotion) 1f else 0.88f) }
    val load = remember { Animatable(0f) }
    var ready by rememberSaveable { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    fun go() { if (!gone) { gone = true; s.enter(id) } }
    LaunchedEffect(id) {
        if (s.app.sound) s.narrator.play("sounds/page.m4a")
        grow.animateTo(1f, tween(if (s.reducedMotion) 0 else 450))
        // 편지 꾸러미 펴기: 이 시리즈의 낱말 카드 · 도착한 편지를 미리 셈해 둔다
        s.openable(id); s.dueCards(id)
        load.animateTo(1f, tween(if (s.reducedMotion) 0 else 700, easing = LinearEasing))
        ready = true
        kotlinx.coroutines.delay(if (s.reducedMotion) 0 else 450)
        go()
    }
    Box(Modifier.fillMaxSize().background(t.paper).pressable(haptic = false) { go() }.graphicsLayer { scaleX = grow.value; scaleY = grow.value; alpha = (grow.value - 0.88f) / 0.12f * 0.7f + 0.3f }) {
        AssetImage(w.kit.hero, Modifier.fillMaxSize(), sample = 1)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x26000000), 0.42f to Color(0x0D000000), 0.9f to t.paper)))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 28.dp, vertical = 64.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(w.kit.place, style = Type.capsSm, color = t.accent)
            Text(w.kit.world[uiLang()], style = Type.title.ui(), color = t.ink)
            if (!ready) {
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(2.dp).background(t.ink.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(load.value).height(2.dp).background(t.accent))
                }
                Text(stringResource(R.string.enter_loading), style = Type.small.ui(), color = t.soft)
            } else {
                Text(stringResource(R.string.enter_ready, s.unread(id), s.dueCards(id).size), style = Type.small.ui(), color = t.soft)
            }
        }
    }
}
