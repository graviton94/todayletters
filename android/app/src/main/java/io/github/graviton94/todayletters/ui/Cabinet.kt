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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    val (now, goal) = medalCount(s, m, st)
    return stringResource(R.string.medal_count, now, goal, unitOf(s, m, uiLang())).trim()
}

/** 받는 법 한 줄. */
@Composable
fun medalHow(s: AppState, m: MedalDef): String = when (m.family) {
    "streak" -> stringResource(R.string.medal_how_streak, m.threshold)
    "letters" -> stringResource(R.string.medal_how_letters, m.threshold)
    "words" -> stringResource(R.string.medal_how_words, m.threshold)
    "stars" -> stringResource(R.string.medal_how_stars, m.threshold)
    "walks" -> stringResource(R.string.medal_how_walks, m.threshold)
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
            }
            Hair()
        }
    }
}

/** 메달 칸 하나: 메달 · 아래에 이름 (받음) / 진행 (다음) / 문턱 (그 뒤). 진열장과 나의 기록이 같이 쓴다. */
@Composable
fun MedalCell(s: AppState, m: MedalDef, st: Stats, earned: Boolean, isNext: Boolean, size: Dp, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val p = Ink.palette
    val ui = uiLang()
    Column(modifier.pressable(onClick = onOpen).padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MedalImage(m, earned, size, progress = if (isNext) medalCount(s, m, st).let { (a, b) -> a.toFloat() / b.coerceAtLeast(1) } else null,
            faint = !earned && !isNext)
        Text(
            when {
                earned -> m.title[ui]
                isNext -> countText(s, m, st)
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
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            MedalImage(m, earned, 150.dp, progress = if (earned) null else medalCount(s, m, st).let { (a, b) -> a.toFloat() / b.coerceAtLeast(1) })
            Caps(tierName(m.tier) + " · " + (s.medalBook.family(m.family)?.title?.get(ui) ?: ""), if (earned) p.giltText else p.inkSoft,
                small = true, decorative = true)
            Text(m.title[ui], style = Type.title.ui(), color = p.ink, textAlign = TextAlign.Center)
            if (day != null) Text(stringResource(R.string.medal_got, if (day >= 0) shortDate(day) else "✓"), style = Type.small.ui(), color = p.inkSoft)
            else {
                val (now, goal) = medalCount(s, m, st)
                Text(countText(s, m, st), style = Type.small.ui(), color = p.giltText)
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
 * 레벨 업 순간: 보상 순간과 같은 전시실 벽 (어두운 바탕 · 위에서 내려오는 조명) 위에 새 레벨 · 칭호 · 새 입구 장식.
 * 보상 순간이 떠 있으면 그것이 닫힌 뒤에 (겹쳐 쌓지 않는다). 한 번에 여러 레벨을 올라도 한 장.
 */
@Composable
fun LevelUpOverlay(s: AppState) {
    val up = s.levelUp ?: return
    if (s.reward != null) return
    val w = s.currentWork
    val t = darkTone(w)
    val ui = uiLang()
    val gold = Color(0xFFE7C878)
    val a = remember(up) { Animatable(if (s.reducedMotion) 1f else 0f) }
    LaunchedEffect(up) { a.animateTo(1f, tween(if (s.reducedMotion) 0 else 900)) }
    androidx.activity.compose.BackHandler { s.levelUp = null }
    val book = s.medalBook.levels
    val lv = Levels.of(s.store.xp().total)
    BoxWithConstraints(Modifier.fillMaxSize().background(t.paper).pressable(haptic = false) { }) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = a.value }.background(Brush.radialGradient(
            listOf(Color(0xFFFFE2AA).copy(alpha = 0.22f), Color.Transparent), center = Offset(wpx / 2, hpx * 0.28f), radius = hpx * 0.5f)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3, Alignment.CenterVertically),
        ) {
            Spacer(Modifier.height(Tokens.Space.s6))
            Caps(stringResource(R.string.levelup_caps), gold, decorative = true)
            Text(stringResource(R.string.levelup_title), style = Type.title.ui(), color = t.ink, textAlign = TextAlign.Center)
            Box(Modifier.graphicsLayer { alpha = a.value; val k = 0.94f + 0.06f * a.value; scaleX = k; scaleY = k }.padding(vertical = Tokens.Space.s3),
                contentAlignment = Alignment.Center) {
                Text("Lv ${up.to}", style = Type.display.copy(fontFamily = Faces.display), color = gold)
            }
            Ornament(gold.copy(alpha = 0.8f))
            Text(Levels.title(book, up.to, ui), style = Type.title.ui(), color = t.ink, textAlign = TextAlign.Center)
            Levels.decorationsBetween(up.from, up.to).forEach { d ->
                Text(stringResource(R.string.levelup_deco, Levels.decorationName(book, d, ui)), style = Type.body.ui(), color = t.accent, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(t.ink.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(lv.frac).height(2.dp).background(gold))
                }
                Text(stringResource(R.string.level_left, lv.left, lv.level + 1), style = Type.small.ui(), color = t.soft, modifier = Modifier.align(Alignment.End))
            }
            Spacer(Modifier.height(Tokens.Space.s3))
            Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(gold).pressable { s.levelUp = null }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reward_ok), style = Type.body.ui(), color = Color(0xFF15110C))
            }
        }
    }
}
