package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Levels
import io.github.graviton94.todayletters.core.MedalDef
import io.github.graviton94.todayletters.core.MedalTier
import io.github.graviton94.todayletters.core.Medals
import io.github.graviton94.todayletters.core.Stats
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/*
 * 메달 진열장 · 끝없는 레벨 (core 의 Medals · Levels, data/medals.json).
 * 진열장: 가족마다 한 줄 (받은 메달은 금속 그대로 · 등급 테, 다음 메달은 석고 본 + "12/30일", 그 뒤는 더 흐리게).
 * 누르면 그 메달이 된 고대 유물 (이름 · 시기 · 소장처 · 라이선스) 과 받는 법.
 * 레벨: 서재 · 작품 머리의 짧은 칭호 줄, 오르면 보상 순간과 같은 모양의 레벨 업 한 장, 5 레벨마다 입구 장식.
 */

/** 석고 본 (아직 받지 않은 메달): 흑백 · 밝게 · 대비 낮게, 따뜻한 흰빛. */
private val plaster = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0.2f, 0.2f, 0.2f, 0f, 140f,
    0.195f, 0.195f, 0.195f, 0f, 136f,
    0.185f, 0.185f, 0.185f, 0f, 127f,
    0f, 0f, 0f, 1f, 0f,
)))

/** 등급 테의 빛깔: 실제 금속 (청동 · 은 · 금). */
fun tierColor(t: MedalTier): Color = when (t) {
    MedalTier.BRONZE -> Color(0xFFA9773F)
    MedalTier.SILVER -> Color(0xFFB9BEC6)
    MedalTier.GOLD -> Color(0xFFD9B44A)
}

@Composable
fun tierName(t: MedalTier): String = stringResource(when (t) {
    MedalTier.BRONZE -> R.string.medal_tier_bronze
    MedalTier.SILVER -> R.string.medal_tier_silver
    MedalTier.GOLD -> R.string.medal_tier_gold
})

/**
 * 메달 하나. [earned] 면 금속 그대로 + 등급 테 + 그림자. 아니면 석고 본, [progress] 가 있으면 진행 고리 (다음 메달),
 * [faint] 면 더 흐리게 (그 뒤의 메달).
 */
@Composable
fun MedalImage(m: MedalDef, earned: Boolean, size: Dp, progress: Float? = null, faint: Boolean = false) {
    val p = Ink.palette
    val bmp = rememberAsset(m.image)
    val ring = tierColor(m.tier)
    Box(Modifier.size(size).graphicsLayer { alpha = if (faint) 0.38f else 1f }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = (if (earned) 2.dp else 1.5.dp).toPx(); val r = this.size.minDimension / 2 - st
            drawCircle(if (earned) ring else p.hair, r, style = Stroke(st))
            if (earned) drawCircle(ring.copy(alpha = 0.35f), r - 3.dp.toPx(), style = Stroke(0.8.dp.toPx()))
            if (!earned && progress != null && progress > 0f) drawArc(p.giltText, -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(st + 0.5.dp.toPx(), cap = StrokeCap.Round))
        }
        if (bmp != null) Image(bmp, null, Modifier.size(size * 0.8f).then(if (earned) Modifier.shadow(4.dp, CircleShape) else Modifier),
            colorFilter = if (earned) null else plaster)
    }
}

/** 메달의 지금 숫자와 문턱: 시리즈 메달은 그 작품의 다 읽은 편지 / 모든 편지. */
private fun medalCount(s: AppState, m: MedalDef, st: Stats): Pair<Int, Int> {
    val sid = m.series
    if (sid != null) {
        val w = s.works.firstOrNull { it.series.id == sid } ?: return 0 to 1
        val total = w.chapters.sumOf { it.letters.size }
        return (if (sid in st.seriesCompleted) total else s.doneCount(sid)) to total
    }
    return Medals.value(m, st).coerceAtMost(m.threshold) to m.threshold
}

/** 단위 ("일" · "days"): 시리즈 메달은 편지 단위. */
private fun unitOf(s: AppState, m: MedalDef, ui: Lang): String =
    (s.medalBook.family(if (m.series != null) "letters" else m.family)?.unit?.get(ui)).orEmpty()

@Composable
private fun countText(s: AppState, m: MedalDef, st: Stats): String {
    if (m.family == "grade") return stringResource(R.string.grade_to, gradeLabel(m.threshold), (m.threshold - st.bestGrade).coerceAtLeast(0))
    val (now, goal) = medalCount(s, m, st)
    return stringResource(R.string.medal_count, now, goal, unitOf(s, m, uiLang())).trim()
}

/** 등급 번호 (1~12) 를 “편지를 읽는 사람” · “답장을 쓰는 사람 3단” 처럼: 등급의 첫 단이면 이름만. A1~B2 글자는 화면에 내지 않는다. */
@Composable
private fun gradeLabel(index: Int): String {
    val g = io.github.graviton94.todayletters.core.Reading.Grade.of(index.coerceAtLeast(1))
    return if (g.step == 1) gradeName(g.band) else gradeStep(g)
}

/** 등급의 화면 이름 (낱말을 줍는 사람 · 문장을 읽는 사람 · 편지를 읽는 사람 · 답장을 쓰는 사람). */
@Composable
fun gradeName(b: io.github.graviton94.todayletters.core.Reading.Band): String = stringResource(when (b) {
    io.github.graviton94.todayletters.core.Reading.Band.A1 -> R.string.grade_a1
    io.github.graviton94.todayletters.core.Reading.Band.A2 -> R.string.grade_a2
    io.github.graviton94.todayletters.core.Reading.Band.B1 -> R.string.grade_b1
    io.github.graviton94.todayletters.core.Reading.Band.B2 -> R.string.grade_b2
})

/** “문장을 읽는 사람 2단”. */
@Composable
fun gradeStep(g: io.github.graviton94.todayletters.core.Reading.Grade): String = stringResource(R.string.grade_step, gradeName(g.band), g.step)

/** “프랑스어 · 문장을 읽는 사람 2단”. */
@Composable
fun gradeLine(lang: Lang, g: io.github.graviton94.todayletters.core.Reading.Grade): String =
    stringResource(R.string.grade_line, stringResource(langLabel(lang)), gradeName(g.band), g.step)

/** 언어 등급 메달이 가리키는 말: 등급이 가장 높은 말 (같으면 지금 작품에서 배우는 말). */
private fun gradeLang(s: AppState): io.github.graviton94.todayletters.core.Lang {
    val cur = s.room(s.current).learn
    return io.github.graviton94.todayletters.core.Lang.entries.maxByOrNull { s.store.grade(it.code) * 2 + if (it == cur) 1 else 0 } ?: cur
}

/** 받는 법 한 줄. */
@Composable
fun medalHow(s: AppState, m: MedalDef): String = when (m.family) {
    "streak" -> stringResource(R.string.medal_how_streak, m.threshold)
    "letters" -> stringResource(R.string.medal_how_letters, m.threshold)
    "words" -> stringResource(R.string.medal_how_words, m.threshold)
    "stars" -> stringResource(R.string.medal_how_stars, m.threshold)
    "walks" -> stringResource(R.string.medal_how_walks, m.threshold)
    "grade" -> stringResource(R.string.medal_how_grade, stringResource(langLabel(gradeLang(s))), gradeLabel(m.threshold))
    else -> stringResource(R.string.medal_how_series, s.works.firstOrNull { it.series.id == m.series }?.name?.get(uiLang()) ?: m.series.orEmpty())
}

/** 진열장에 보이는 메달: 시리즈 메달은 앱에 있는 작품 것만. */
fun cabinetMedals(s: AppState): List<MedalDef> =
    s.medalBook.medals.filter { m -> m.series == null || s.baseWorks.any { it.series.id == m.series } }

/**
 * 메달 진열장 (나의 기록 › 전체 보기): 가족마다 이름 · 받은 수, 그 아래 메달 칸 (한 줄에 넷).
 * 받은 메달은 금속과 이름, 다음 메달은 석고 본과 진행 ("12/30일"), 그 뒤는 흐린 석고 본과 문턱.
 */
@Composable
fun MedalCabinet(s: AppState, onOpen: (MedalDef) -> Unit) {
    val p = Ink.palette
    val ui = uiLang()
    val st = s.medalStats()
    val have = s.medalsOwned
    val all = cabinetMedals(s)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        s.medalBook.families.forEach { f ->
            val ms = all.filter { it.family == f.id }.sortedBy { it.threshold }
            if (ms.isEmpty()) return@forEach
            val next = ms.firstOrNull { it.id !in have && it.series == null }
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(f.title[ui], style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
                    Text("${ms.count { it.id in have }} / ${ms.size}", style = Type.small.copy(fontFamily = Faces.display), color = p.giltText)
                }
                ms.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { m ->
                            MedalCell(s, m, st, earned = m.id in have, isNext = m == next || (m.series != null && m.id !in have), size = 66.dp,
                                modifier = Modifier.weight(1f)) { onOpen(m) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                // 메달을 막 진열장에 넣었으면 (메달 한 장의 ‘진열장에 넣기’): 그 가족 아래 다음 메달까지 (시안 rw2 ③)
                if (s.medalLanding != null && ms.any { it.id == s.medalLanding } && next != null) {
                    val left = (next.threshold - Medals.value(next, st)).coerceAtLeast(0)
                    val unit = unitOf(s, next, ui)
                    Text(stringResource(R.string.medal_next_line, next.title[ui], if (ui == Lang.KO) "$left$unit" else "$left $unit".trim()),
                        style = Type.small.ui(), color = p.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            Hair()
        }
    }
}

/** 메달 칸 하나: 메달 · 아래에 이름 (받음) / 진행 (다음) / 문턱 (그 뒤). 진열장과 나의 기록이 같이 쓴다. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MedalCell(s: AppState, m: MedalDef, st: Stats, earned: Boolean, isNext: Boolean, size: Dp, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val p = Ink.palette
    val ui = uiLang()
    Column(modifier.pressable(onClick = onOpen).padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 막 받은 메달이 날아올 칸 (ui/Flight.kt): 화면 밖이면 그 칸까지 굴린다
        val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        val landing = s.medalLanding == m.id
        LaunchedEffect(landing) { if (landing) bring.bringIntoView() }
        Box(Modifier.bringIntoViewRequester(bring).then(if (earned) Modifier.flightSlot(s, "medal:${m.id}") else Modifier)) {
            MedalImage(m, earned, size, progress = if (isNext) medalCount(s, m, st).let { (a, b) -> a.toFloat() / b.coerceAtLeast(1) } else null,
                faint = !earned && !isNext)
        }
        Text(
            when {
                earned -> m.title[ui]
                isNext -> countText(s, m, st)
                m.family == "grade" -> gradeLabel(m.threshold)   // 문턱만 (“편지를 읽는 사람”)
                else -> stringResource(R.string.medal_count, m.threshold, m.threshold, unitOf(s, m, ui)).trim().substringAfter("/")   // 문턱만 ("60일")
            },
            style = Type.small.ui().copy(fontSize = Tokens.Text.caps, fontWeight = if (earned) FontWeight.SemiBold else FontWeight.Normal),
            color = when { earned -> p.ink; isNext -> p.giltText; else -> p.hideInk }, textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}

/**
 * 나의 기록에 먼저 보여 줄 메달 [n] 개: 받은 메달 (최근 것부터) → 가족마다 다음 메달 → 나머지.
 */
fun featuredMedals(s: AppState, n: Int = 6): List<MedalDef> {
    val all = cabinetMedals(s)
    val have = s.medalsOwned
    val got = all.filter { it.id in have }.sortedByDescending { s.medalDay(it.id) ?: -1L }
    val next = all.filter { it.id !in have && it.series == null }.groupBy { it.family }.values.mapNotNull { ms -> ms.minByOrNull { it.threshold } }
    return (got + next + all).distinct().take(n)
}

/** 메달 자세히: 메달 · 등급 · 이름 · 받은 날 (또는 진행), 받는 법, 메달이 된 유물 (이름 · 시기 · 소장처 · 라이선스). */
@Composable
fun MedalDetail(s: AppState, m: MedalDef, onClose: () -> Unit) {
    val p = Ink.palette
    val ui = uiLang()
    val w = s.currentWork
    val st = s.medalStats()
    val day = s.medalDay(m.id)
    val earned = day != null
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose).statusBarsSafe(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsSafe()
            .verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            MedalImage(m, earned, 150.dp, progress = if (earned) null else medalCount(s, m, st).let { (a, b) -> a.toFloat() / b.coerceAtLeast(1) })
            Caps(tierName(m.tier) + " · " + (s.medalBook.family(m.family)?.title?.get(ui) ?: ""), if (earned) p.giltText else p.inkSoft,
                small = true, decorative = true)
            Text(m.title[ui], style = Type.title.ui(), color = p.ink, textAlign = TextAlign.Center)
            if (day != null) Text(stringResource(R.string.medal_got, if (day >= 0) shortDate(day) else "✓"), style = Type.small.ui(), color = p.inkSoft)
            else {
                val (now, goal) = medalCount(s, m, st)
                Text(if (m.family == "grade") stringResource(R.string.medal_now_grade, gradeLabel(st.bestGrade)) else countText(s, m, st),
                    style = Type.small.ui(), color = p.giltText)
                Bar(now.toFloat() / goal.coerceAtLeast(1), Modifier.padding(vertical = 2.dp))
            }
            // 받는 법 / 받은 까닭
            Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caps(stringResource(if (earned) R.string.medal_how_got_h else R.string.medal_how_h), p.giltText, small = true)
                Text(medalHow(s, m), style = Type.body.ui(), color = p.ink)
                if (!earned) Text(stringResource(R.string.medal_gift, w.kit.currency[ui]), style = Type.small.ui(), color = p.inkSoft)
            }
            // 메달이 된 유물
            Column(Modifier.fillMaxWidth().border(1.dp, p.line).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Caps(stringResource(R.string.medal_object_h), p.giltText, small = true)
                Text(m.source.title, style = Type.body.copy(fontFamily = Faces.display, fontStyle = FontStyle.Italic), color = p.ink)
                Text(listOf(m.source.date, m.source.artist).filter { it.isNotBlank() }.joinToString(" · "), style = Type.small, color = p.inkSoft)
                Text(m.source.museum + " · " + m.source.license, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            }
            Secondary(stringResource(R.string.close), Modifier.padding(top = Tokens.Space.s2), onClick = onClose)
        }
    }
}

/**
 * 칭호 줄 (서재 · 작품 머리): "학예 보조 II · Lv 14" 와 다음 레벨까지 얇은 막대. 누르면 나의 기록 (부르는 곳에서 [modifier] 로).
 */
@Composable
fun RankLine(s: AppState, modifier: Modifier = Modifier) {
    val p = Ink.palette
    val lv = s.level
    val title = Levels.title(s.medalBook.levels, lv.level, uiLang())
    Column(modifier.semantics { contentDescription = title + " · Lv " + lv.level }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.level_line, title, lv.level), style = Type.small.ui().copy(fontSize = Tokens.Text.caps, fontWeight = FontWeight.SemiBold),
                color = p.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.level_left, lv.left, lv.level + 1), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.hair)) {
            Box(Modifier.fillMaxWidth(lv.frac).height(2.dp).background(p.giltText))
        }
    }
}

/** 입구 장식의 작은 문양: 가는 줄 · 마름모 · 가는 줄. */
@Composable
fun Ornament(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(76.dp).height(8.dp)) {
        val y = size.height / 2; val c = size.width / 2; val d = 3.5.dp.toPx(); val st = 0.8.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(c - d * 2, y), st)
        drawLine(color, Offset(c + d * 2, y), Offset(size.width, y), st)
        val path = Path().apply { moveTo(c, y - d); lineTo(c + d, y); lineTo(c, y + d); lineTo(c - d, y); close() }
        drawPath(path, color)
    }
}

/**
 * 레벨 업 (시안 rw2 4): 숫자보다 이름. 옛 칭호 “해설사 II” 가 흐려지며 사라지고 새 칭호 “해설사 III” 이 또렷해진다
 * (한글은 세운 글자, 로마 숫자는 Cormorant). 금선이 가운데서 펴지고, 새로 열린 것 (입구 장식, 그림이 데이터에 없으면 이름만) · XP 줄.
 * 진동 한 번. 큰 단추는 열린 곳으로 (입구 장식이 열렸으면 ‘입구에 가 보기’, 걸음 중이면 그 걸음으로, 아니면 나의 기록).
 * 보상 순간 · 등급 · 걸음 마침 한 장이 떠 있으면 그것이 닫힌 뒤에 (겹쳐 쌓지 않는다). 한 번에 여러 레벨을 올라도 한 장.
 */
@Composable
fun LevelUpOverlay(s: AppState) {
    val up = s.levelUp ?: return
    if (s.reward != null || s.gradeUp != null || s.stepNote != null) return
    val w = s.currentWork
    val ui = uiLang()
    val still = s.reducedMotion
    val clock by rememberClock(up, still, 3200)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(up) { s.cueTier(io.github.graviton94.todayletters.core.Tier.DRAWING) }
    LaunchedEffect(up) {
        if (!still) androidx.compose.runtime.snapshotFlow { clock }.first { it >= 1700 }
        if (s.app.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    androidx.activity.compose.BackHandler { s.levelUp = null }
    val book = s.medalBook.levels
    val lv = Levels.of(s.store.xp().total)
    val old = io.github.graviton94.todayletters.core.Wall.rank(Levels.title(book, up.from.coerceAtLeast(1), ui))
    val now = io.github.graviton94.todayletters.core.Wall.rank(Levels.title(book, up.to, ui))
    val decos = Levels.decorationsBetween(up.from, up.to)
    val step = s.route as? io.github.graviton94.todayletters.core.Route.Play
    @Composable
    fun Rank(t: Pair<String, String>, k: Float, dir: Float) {
        Row(Modifier.fillMaxWidth().focus(k, 8f, dir * (1f - k) * 8f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(t.first, style = Type.title.ui().copy(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium), color = Museum.ink, maxLines = 1, textAlign = TextAlign.Center)
            if (t.second.isNotEmpty()) Text(" " + t.second, style = Type.title.copy(fontFamily = Faces.display, fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium), color = Museum.ink, maxLines = 1)
        }
    }
    Box(Modifier.fillMaxSize().background(Museum.wall).pressable(haptic = false) { }) {
        Column(Modifier.fillMaxSize().statusBarsSafe().navigationBarsSafe()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Spacer(Modifier.height(24.dp))
                Eyebrow("${io.github.graviton94.todayletters.core.Wall.level(w.series.original)} ${up.to}", Museum.gilt, Modifier.rise(win(clock, 0, 900)))
                Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                    // 옛 칭호: 흐려지며 위로 (0.3초 뒤 1.2초), 새 칭호: 아래에서 또렷해짐 (0.9초 뒤 1.2초)
                    val out = 1f - win(clock, 300, 1200)
                    if (out > 0f && old != now) Rank(old, out, -1f)
                    Rank(now, if (old == now) win(clock, 300, 1200) else win(clock, 900, 1200), 1f)
                }
                val rule = win(clock, 1400, 1000)
                Box(Modifier.width(120.dp).height(1.dp).graphicsLayer { scaleX = rule }.background(Museum.gilt))
                Text(stringResource(R.string.levelup_title), style = Type.small.ui().copy(fontSize = 13.sp), color = Museum.soft, textAlign = TextAlign.Center,
                    modifier = Modifier.rise(win(clock, 1600, 900)))
                if (decos.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(top = 18.dp).rise(win(clock, 1900, 900)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow(stringResource(R.string.level_unlocked), Museum.soft, size = 11f)
                    decos.forEach { d ->
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Museum.line))
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Ornament(Museum.gilt.copy(alpha = 0.8f))
                                Column {
                                    Text(Levels.decorationName(book, d, ui), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = Museum.ink)
                                    Text(stringResource(R.string.level_deco_line, d.level), style = Type.small.ui().copy(fontSize = 12.sp), color = Museum.soft)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Museum.line)) {
                        val k = win(clock, 1800, 1200)
                        Box(Modifier.fillMaxWidth(lv.frac.coerceIn(0.02f, 1f) * k).height(2.dp).background(Museum.gilt))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.level_left, lv.left, lv.level + 1), style = Type.small.ui().copy(fontSize = 11.sp), color = Museum.soft)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                when {
                    decos.isNotEmpty() -> GoButton(stringResource(R.string.go_entrance), Levels.decorationName(book, decos.last(), ui),
                        fill = Museum.ink, on = Museum.wall, soft = Color(0xFF6B6255)) { s.levelUp = null; s.visitEntrance() }
                    step != null -> GoButton(stringResource(R.string.go_next_step, stringResource(modeLabel(step.mode))),
                        fill = Museum.ink, on = Museum.wall, soft = Color(0xFF6B6255)) { s.levelUp = null }
                    else -> GoButton(stringResource(R.string.go_record), "Lv ${up.to}", fill = Museum.ink, on = Museum.wall, soft = Color(0xFF6B6255)) {
                        s.levelUp = null; s.go(io.github.graviton94.todayletters.core.Route.Record)
                    }
                }
            }
        }
    }
}

/**
 * 언어 등급이 오른 순간 (2026-10-25, 레벨 업과 같은 벽으로 다시 씀 — 예전 조명 · 금빛 큰 글자 · ‘좋아요’ 는 지움): 보상 순간이 닫힌 뒤, 레벨 업보다 먼저.
 * 머리말 “언어 등급 · 프랑스어”, 새 등급에 들어섰으면 옛 이름이 흐려지고 새 이름이 또렷해진다 (한글 세운 글자), 그 아래 “A2 1단” (같은 등급 안이면
 * 이것만 바뀐다). 금선, “프랑스어 실력이 한 단 올랐어요” (새 등급 이름은 제목이라 ‘됐어요’ 줄은 겹쳐 빼고), 새로 달라지는 것 한 줄씩 (번역이 조금 더 옅게 · 도움 한 칸). 단추는 덮인 화면으로.
 */
@Composable
fun GradeUpOverlay(s: AppState) {
    val up = s.gradeUp ?: return
    if (s.reward != null || s.stepNote != null) return
    val still = s.reducedMotion
    val clock by rememberClock(up, still, 3200)
    LaunchedEffect(up) { s.cueTier(io.github.graviton94.todayletters.core.Tier.DRAWING) }
    androidx.activity.compose.BackHandler { s.gradeUp = null }
    val langName = stringResource(langLabel(up.lang))
    val newBand = up.to.band != up.from.band
    @Composable
    fun Name(text: String, k: Float) {
        Text(text, style = Type.title.ui().copy(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium), color = Museum.ink, maxLines = 1,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().focus(k, 8f, 8f))
    }
    @Composable
    fun Step(g: io.github.graviton94.todayletters.core.Reading.Grade, k: Float) {
        Text(stringResource(R.string.grade_step, g.band.name, g.step), style = Type.body.ui().copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = Museum.gilt, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().focus(k, 6f, 6f))
    }
    Box(Modifier.fillMaxSize().background(Museum.wall).pressable(haptic = false) { }) {
        Column(Modifier.fillMaxSize().statusBarsSafe().navigationBarsSafe()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Spacer(Modifier.height(24.dp))
                Eyebrow(stringResource(R.string.gradeup_caps) + " · " + langName, Museum.gilt, Modifier.rise(win(clock, 0, 900)))
                // 등급 이름: 새 등급이면 옛 이름이 흐려지며 물러나고 (0.3초 뒤 1.2초) 새 이름이 또렷해진다 (0.9초 뒤 1.2초)
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    if (newBand) { val out = 1f - win(clock, 300, 1200); if (out > 0f) Name(gradeName(up.from.band), out) }
                    Name(gradeName(up.to.band), if (newBand) win(clock, 900, 1200) else win(clock, 300, 1200))
                }
                // 단: 같은 등급 안이면 이것만 넘어간다
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                    if (!newBand) { val out = 1f - win(clock, 500, 1100); if (out > 0f) Step(up.from, out) }
                    Step(up.to, if (newBand) win(clock, 1200, 1000) else win(clock, 1100, 1100))
                }
                Box(Modifier.width(120.dp).height(1.dp).graphicsLayer { scaleX = win(clock, 1400, 1000) }.background(Museum.gilt))
                Text(stringResource(R.string.gradeup_title, langName),
                    style = Type.small.ui().copy(fontSize = 13.sp), color = Museum.soft, textAlign = TextAlign.Center, modifier = Modifier.rise(win(clock, 1600, 900)))
                // 새로 달라지는 것: 새 등급의 이름 · 번역이 조금 더 옅게 (작품 설정에서 끈 사람에게는 말하지 않는다) · 도움 한 칸
                val fade = newBand && s.works.any { s.room(it.series.id).learn == up.lang && s.seriesSettings(it.series.id).fadeRead && s.seriesSettings(it.series.id).showRead }
                val less = io.github.graviton94.todayletters.core.Support.lessened(up.from, up.to)
                if (fade || less) Column(Modifier.fillMaxWidth().padding(top = 18.dp).rise(win(clock, 1900, 900)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Museum.line))
                    if (fade) Text(stringResource(R.string.gradeup_dim), style = Type.body.ui(), color = Museum.ink)
                    if (less) Text(stringResource(R.string.support_less), style = Type.body.ui(), color = Museum.ink)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Museum.line))
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp).rise(win(clock, 1000, 700), blur = 0f)) {
                UnderButton(s) { s.gradeUp = null }
            }
        }
    }
}
