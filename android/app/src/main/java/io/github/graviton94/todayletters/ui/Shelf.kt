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
            // 처음 설치 (튜토리얼): 첫 작품 카드를 강조하고, 배울 말은 그 카드에서 고른다 → 바로 편지 I
            s.works.forEach { w -> if (!s.tutorialDone && w.series.id == s.tutorialSeries && s.needsWelcome(w.series.id)) TutorialCard(s, w) else SeriesCard(s, w) }
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
        }
    }
}

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
                val waiting = if (s.startedCount(id) > 0) s.waitingLetters(id).size else 0
                val badge = when { waiting > 0 -> stringResource(R.string.lib_waiting, waiting); unread > 0 -> stringResource(R.string.lib_arrived); due > 0 -> stringResource(R.string.lib_due, due); else -> null }
                if (badge != null) Text(badge, style = Type.small.ui(), color = t.paper, modifier = Modifier.background(t.accent).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

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
