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
import io.github.graviton94.todayletters.core.Achievements
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
    val overallDone = Achievements.overall.count { it.id in s.store.achievements }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1, top = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            Caps("LINGUA MUSEUM", p.inkSoft, small = true, decorative = true, modifier = Modifier.weight(1f))
            HeaderIcons(s)
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Text(stringResource(R.string.lib_title), style = Type.title.ui(), color = p.ink)
            Row(Modifier.fillMaxWidth()) {
                listOf("${s.streak}" to R.string.lib_stat_streak, "$pieces" to R.string.lib_stat_pieces, "$overallDone / ${Achievements.overall.size}" to R.string.lib_stat_ach).forEach { (v, l) ->
                    Column(Modifier.weight(1f)) {
                        Text(v, style = Type.title.copy(fontFamily = Faces.display), color = p.ink)
                        Text(stringResource(l), style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            }
            Box(Modifier.heightIn(min = 40.dp).pressable { s.go(Route.Overall) }, contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.lib_overall) + " ›", style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.ink)
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
            // 원어 · 햇수 · 배우는 말 (눌러서 바꾸기: 진도는 언어마다 따로)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text("${stringResource(langLabel(w.series.original))} · ${w.years}", style = Type.small.ui(), color = t.soft, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (s.store.learnChosen(id) || s.startedCount(id) > 0) LearnChip(s, id, ink = t.ink, line = t.accent)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.lib_letters, done, total), style = Type.small.ui(), color = t.ink)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(t.ink.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).height(3.dp).background(t.accent))
                    }
                }
                Spacer(Modifier.weight(0.2f))
                val badge = when { unread > 0 -> stringResource(R.string.lib_arrived); due > 0 -> stringResource(R.string.lib_due, due); else -> null }
                if (badge != null) Text(badge, style = Type.small.ui(), color = t.paper, modifier = Modifier.background(t.accent).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
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

/** 서재 › 내 기록 · 전체: 이어 읽기 하나 · 시리즈별 화폐 · 합친 갤러리 (시리즈로 거르기) · 전체 이정표. */
@Composable
fun OverallScreen(s: AppState) {
    val p = Ink.palette
    s.version
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<io.github.graviton94.todayletters.core.Piece?>(null) }
    val st = s.stats()
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.overall_title), s, help = null, showBack = true, showSettings = false)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.streak}", style = Type.display.copy(fontFamily = Faces.display), color = p.ink)
                Text(" " + stringResource(R.string.overall_streak), style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
                Text(stringResource(R.string.overall_streak_note), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                s.works.forEach { w ->
                    val t = w.kit.light?.let(::tone)
                    Row(Modifier.weight(1f).background(t?.paper ?: p.hide).border(width = 0.dp, color = Color.Transparent).padding(Tokens.Space.s3),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Box(Modifier.width(3.dp).height(30.dp).background(t?.accent ?: p.ink))
                        Portrait(w.portrait, w.fullName, 28.dp)
                        Column {
                            Text("${s.wallet(w.series.id).balance}", style = Type.heading.copy(fontFamily = Faces.display), color = t?.accent ?: p.ink)
                            Text(w.kit.currency[uiLang()], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                        }
                    }
                }
                if (s.works.size == 1) Spacer(Modifier.weight(1f))
            }
            // 합친 갤러리
            val all = s.works.flatMap { w -> s.gallery(w.series.id).filter { s.owns(w.series.id, it) }.map { w to it } }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.overall_gallery), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
                Text("${all.size} / ${s.works.sumOf { s.gallery(it.series.id).size }}", style = Type.small, color = p.inkSoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Chip(stringResource(R.string.overall_all), filter == null, p.ink) { filter = null }
                s.works.forEach { w -> Chip(w.name[uiLang()], filter == w.series.id, w.kit.light?.let(::tone)?.accent ?: p.ink) { filter = w.series.id } }
            }
            all.filter { filter == null || it.first.series.id == filter }.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (w, pc) ->
                        Column(Modifier.weight(1f).pressable { open = pc }) {
                            AssetImage(pc.image, Modifier.fillMaxWidth().aspectRatio(1f), sample = 8)
                            Box(Modifier.fillMaxWidth().height(3.dp).background(w.kit.light?.let(::tone)?.accent ?: p.ink))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (all.isEmpty()) Text(stringResource(R.string.overall_gallery_empty), style = Type.small.ui(), color = p.inkSoft)
            // 도록 (E4): 다 모은 이달의 전시
            val cats = s.works.flatMap { w -> s.catalogues(w.series.id).map { w to it } }
            if (cats.isNotEmpty()) {
                Text(stringResource(R.string.overall_catalogues), style = Type.heading.ui(), color = p.ink, modifier = Modifier.padding(top = Tokens.Space.s2))
                cats.forEachIndexed { k, (w, c) -> CatalogueCover(s, w, c.first, c.second, c.third, k + 1) }
            }
            // 전체 이정표
            Text(stringResource(R.string.overall_milestones), style = Type.heading.ui(), color = p.ink, modifier = Modifier.padding(top = Tokens.Space.s2))
            Column {
                Achievements.overall.forEachIndexed { i, a ->
                    val day = s.achievedOn(a.id)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Text(roman(i + 1), style = Type.capsSm, color = if (day != null) p.ink else p.hideInk, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text("ach_${a.id}", a.id), style = Type.body.ui(), color = p.ink)
                            if (day == null) Bar(Achievements.progress(a, st))
                        }
                        Text(if (day != null && day >= 0) shortDate(day) else if (day != null) "✓" else "${a.metric(st).coerceAtMost(a.goal)} / ${a.goal}", style = Type.small, color = p.inkSoft)
                    }
                    Hair()
                }
            }
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
    open?.let { pc -> PieceView(pc, frame = s.frameKind(pc)) { open = null } }
}

@Composable
private fun Chip(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    val p = Ink.palette
    Box(Modifier.heightIn(min = 36.dp).then(if (on) Modifier.background(color) else Modifier.border(1.5.dp, color)).pressable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = if (on) p.paper else color, textAlign = TextAlign.Center)
    }
}
