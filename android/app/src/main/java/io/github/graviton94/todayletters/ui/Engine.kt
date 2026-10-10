package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import io.github.graviton94.todayletters.core.Lang
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Achievements
import io.github.graviton94.todayletters.core.Earn
import io.github.graviton94.todayletters.core.Piece
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Spend
import io.github.graviton94.todayletters.core.Streak
import io.github.graviton94.todayletters.core.Tier
import io.github.graviton94.todayletters.core.Tone
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.SeriesTheme
import io.github.graviton94.todayletters.design.SeriesTone
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import io.github.graviton94.todayletters.design.uiHangul

/**
 * 보상 · 갤러리 엔진의 화면들. 모두 시리즈 공통 틀이고, 이름 · 색 · 소장품만 키트([io.github.graviton94.todayletters.core.Kit])에서 온다.
 * 우리가 그린 우표 · 소포 · 이모지는 없다: 보상은 소장처가 공개한 진짜 작품과 숫자 · 활자로만.
 */

@Composable
private fun named(name: String): Int {
    val ctx = LocalContext.current
    return remember(name) { ctx.resources.getIdentifier(name, "string", ctx.packageName) }
}

@Composable
fun text(name: String, fallback: String = ""): String = named(name).let { if (it != 0) stringResource(it) else fallback }

fun tone(t: Tone) = SeriesTone(Color(t.paper), Color(t.ink), Color(t.soft), Color(t.faint), Color(t.accent))

/** 이 시리즈의 빛깔로 칠한다. */
@Composable
fun InSeries(w: Work, content: @Composable () -> Unit) = SeriesTheme(w.kit.light?.let(::tone), w.kit.dark?.let(::tone), content)

/** 어두운 쪽 빛깔 (서재 카드 · 전환 화면은 테마와 상관없이 늘 어둡게). */
fun darkTone(w: Work): SeriesTone = w.kit.dark?.let(::tone) ?: SeriesTone(Color(0xFF121110), Color(0xFFECE7DE), Color(0xFFA29B90), Color(0xFF6A645B), Color(0xFFC9B48C))

/** assets 안 그림 하나 (아무 경로). 가볍게 줄여서 읽는다. */
@Composable
fun AssetImage(path: String, modifier: Modifier, gray: Boolean = false, sample: Int = 2, alignment: Alignment = Alignment.Center) {
    val ctx = LocalContext.current
    val bmp: ImageBitmap? = remember(path, sample) {
        if (path.isBlank()) null else runCatching {
            ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }
        }.getOrNull()
    }
    if (bmp == null) Box(modifier.background(Ink.palette.hide))
    else Image(bmp, null, modifier, contentScale = ContentScale.Crop, alignment = alignment,
        colorFilter = if (gray) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null, alpha = if (gray) 0.45f else 1f)
}

/** 화폐: 숫자 + 이름 (146 우표). */
@Composable
fun CurrencyLine(s: AppState, w: Work = s.currentWork) {
    val p = Ink.palette
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${s.wallet(w.series.id).balance}", style = Type.heading.copy(fontFamily = Faces.display), color = p.giltText)
        Text(w.kit.currency[uiLang()], style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** 이번 주: 그 시리즈 언어의 요일 머리글자와 점 (배운 날 · 오늘 · 남은 날). */
@Composable
fun WeekDots(s: AppState, w: Work) {
    val p = Ink.palette
    val done = s.weekDone()
    val today = io.github.graviton94.todayletters.core.Recital.weekday(s.today)
    val letters = w.kit.weekdays.padEnd(7).take(7)
    Row(Modifier.fillMaxWidth()) {
        letters.forEachIndexed { i, ch ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$ch", style = Type.capsSm, color = if (i <= today) p.ink else p.hideInk)
                Box(Modifier.size(12.dp).then(when {
                    i in done -> Modifier.background(p.giltText, CircleShape)
                    i == today -> Modifier.border(1.5.dp, p.giltText, CircleShape)
                    else -> Modifier.border(1.dp, p.hair, CircleShape)
                }))
            }
        }
    }
}

/** 이어 읽기 줄: 날 수 · 남은 쉼표 · 이번 주 점. */
@Composable
fun StreakBlock(s: AppState, w: Work) {
    val p = Ink.palette
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.streak_label), style = Type.body.ui(), color = p.ink)
            Text(" ${s.streak}", style = Type.title.copy(fontFamily = Faces.display), color = p.ink)
            Text(stringResource(R.string.streak_days), style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.rests_n, s.rests), style = Type.small.ui(), color = p.inkSoft)
        }
        WeekDots(s, w)
    }
}

/** 가는 진행 막대. */
@Composable
fun Bar(frac: Float, modifier: Modifier = Modifier) {
    val p = Ink.palette
    Box(modifier.fillMaxWidth().height(3.dp).background(p.hair)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(3.dp).background(p.giltText))
    }
}

@Composable
private fun tierName(t: Tier) = stringResource(when (t) {
    Tier.LETTER -> R.string.tier_letter; Tier.SKETCH -> R.string.tier_sketch; Tier.DRAWING -> R.string.tier_drawing
    Tier.PAINTING -> R.string.tier_painting; Tier.MASTER -> R.string.tier_master
})

@Composable
private fun earnName(e: Earn) = stringResource(when (e) {
    Earn.LETTER -> R.string.earn_letter; Earn.PRACTICE -> R.string.earn_practice; Earn.REVIEW_RIGHT -> R.string.earn_review
    Earn.SHADOW_PASS -> R.string.earn_shadow; Earn.READ_ALONE -> R.string.earn_alone; Earn.QUESTS -> R.string.earn_quests
    Earn.RECITAL -> R.string.earn_recital; Earn.STREAK -> R.string.earn_streak; Earn.ACHIEVEMENT -> R.string.earn_achievement
    Earn.SEAL -> R.string.earn_seal; Earn.EXHIBITION -> R.string.earn_exhibition
})

/** 액자 이름 (E3 · D2). */
@Composable
fun frameName(k: io.github.graviton94.todayletters.data.Frames.Kind) = stringResource(when (k) {
    io.github.graviton94.todayletters.data.Frames.Kind.SKETCH -> R.string.frame_sketch
    io.github.graviton94.todayletters.data.Frames.Kind.DRAWING -> R.string.frame_drawing
    io.github.graviton94.todayletters.data.Frames.Kind.PAINTING -> R.string.frame_painting
    io.github.graviton94.todayletters.data.Frames.Kind.MASTER -> R.string.frame_master
})

/** 액자에 끼운 작품 그림 (실제 액자 사진 + 작품). */
@Composable
fun rememberFramed(art: String, kind: io.github.graviton94.todayletters.data.Frames.Kind): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(art, kind) { io.github.graviton94.todayletters.data.Frames.framed(ctx, art, kind)?.asImageBitmap() }
}

/**
 * 보상 순간 (v21 3단계 D2): 어두운 전시실 벽에 조명이 내려오고, 걸린 자료는 등급에 맞는 실제 액자에 걸린다.
 * 아래는 작품 라벨 · 등급 · 얼마나 드문지, 새 이정표, 받은 화폐 (내역은 접어 둠). 등급마다 다른 소리.
 * 이달의 전시를 방금 다 모았으면 액자 대신 도록 표지 (E4).
 */
@Composable
fun RewardOverlay(s: AppState) {
    val m = s.reward ?: return
    val w = s.work(m.series.ifEmpty { s.current })
    val t = darkTone(w)
    val a = remember(m) { Animatable(if (s.reducedMotion) 1f else 0f) }
    LaunchedEffect(m) {
        m.piece?.let { s.cueTier(it.tier) }
        a.animateTo(1f, tween(if (s.reducedMotion) 0 else 900))
    }
    var lines by remember(m) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { s.reward = null }
    val master = m.piece?.tier == Tier.MASTER && m.catalogue == null
    val gold = Color(0xFFE7C878)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(t.paper).pressable(haptic = false) { }) {
        val wpx = constraints.maxWidth.toFloat(); val hpx = constraints.maxHeight.toFloat()
        // 전시실 벽: 위에서 내려오는 조명 (명작이면 더 밝게)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = a.value }.background(Brush.radialGradient(
            listOf(Color(0xFFFFE2AA).copy(alpha = if (master) 0.30f else 0.18f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(wpx / 2, hpx * 0.2f), radius = hpx * 0.55f)))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(when { m.catalogue != null -> "EXPOSITION COMPLÈTE"; master -> "CHEF-D'ŒUVRE"; else -> stringResource(R.string.reward_caps) }, if (master) gold else t.accent, decorative = true)
            Text(stringResource(when { m.catalogue != null -> R.string.reward_catalogue; master -> R.string.reward_master; m.piece != null -> R.string.reward_piece; else -> R.string.reward_title }),
                style = Type.title.ui(), color = t.ink, textAlign = TextAlign.Center)
            val cat = m.catalogue
            if (cat != null) {
                val today = java.time.LocalDate.now()
                Box(Modifier.padding(top = Tokens.Space.s3).graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 40f }) {
                    CatalogueCover(s, w, cat, today.year, today.monthValue, s.catalogues(w.series.id).size)
                }
            } else m.piece?.let { pc ->
                val kind = s.frameKind(pc)
                val framed = rememberFramed(pc.image, kind)
                Box(Modifier.padding(top = Tokens.Space.s3).fillMaxWidth(if ((framed?.height ?: 1) > (framed?.width ?: 1)) 0.66f else 0.86f)
                    .graphicsLayer { alpha = a.value; val k = 0.96f + 0.04f * a.value; scaleX = k; scaleY = k }
                    .shadow(18.dp)) {
                    if (framed != null) Image(framed, pc.title[uiLang()], Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    else AssetImage(pc.image, Modifier.fillMaxWidth().aspectRatio(0.82f), sample = 1)
                }
                // 명작: 작품 이름 명판
                if (master) Box(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFE9CF8A), Color(0xFFB48F45)))).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("${pc.title[Lang.EN].uppercase()} · ${pc.date}", style = Type.capsSm, color = Color(0xFF2A1C06))
                }
                Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pc.title[uiLang()], style = Type.title.ui(), color = t.ink)
                    Text("${pc.date} · ${pc.collection}", style = Type.small.copy(fontSize = Tokens.Text.caps), color = t.soft)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.then(if (master) Modifier.background(gold) else Modifier.border(1.dp, t.accent)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                            Text(tierName(pc.tier), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (master) Color(0xFF2A1C06) else t.accent)
                        }
                        Text(frameName(kind) + if (pc.tier.weight > 0) " · " + stringResource(R.string.reward_odds, pc.tier.weight) else "",
                            style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = t.soft)
                    }
                    if (m.achievements.isNotEmpty() && pc.tier >= Tier.PAINTING) Text(stringResource(R.string.reward_from_ach), style = Type.small.ui(), color = t.soft)
                }
            }
            if (m.milestone != null) Text(stringResource(R.string.reward_alone, m.milestone), style = Type.body.ui(), color = t.ink, textAlign = TextAlign.Center)
            m.achievements.forEach { id ->
                Text(stringResource(R.string.reward_ach, text("ach_$id", id)), style = Type.body.ui(), color = t.accent, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Tokens.Space.s2))
            if (m.gain > 0) Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.ink.copy(alpha = 0.15f)))
                Row(Modifier.fillMaxWidth().pressable(haptic = false) { lines = !lines }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (m.catalogue != null) R.string.reward_catalogue_gain else R.string.reward_total, w.kit.currency[uiLang()]), style = Type.body.ui(), color = t.ink)
                    if (m.lines.size > 1) Text("  " + stringResource(R.string.reward_lines), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = t.faint)
                    Spacer(Modifier.weight(1f))
                    Text("+${m.gain}", style = Type.display.copy(fontFamily = Faces.display), color = if (master) gold else t.accent)
                }
                if (lines) m.lines.forEach { (e, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(earnName(e), style = Type.small.ui(), color = t.soft, modifier = Modifier.weight(1f))
                        Text("+$n", style = Type.small, color = t.ink)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(if (master) gold else t.ink).pressable {
                s.reward = null; if (m.piece != null && m.catalogue == null) s.go(Route.Gallery) else if (m.catalogue != null) s.go(Route.Exhibition)
            }, contentAlignment = Alignment.Center) {
                Text(stringResource(when { m.catalogue != null -> R.string.reward_catalogue_open; m.piece != null -> R.string.reward_hang; else -> R.string.reward_ok }),
                    style = Type.body.ui(), color = if (master) Color(0xFF15110C) else t.paper)
            }
            if (m.piece != null || m.catalogue != null) Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).pressable { s.reward = null }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.close), style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = t.ink)
            }
        }
    }
}

/**
 * 도록 표지 (E4): 이달의 전시를 다 모으면. 전시 자료 전부 · 전시 이름 (원어 · 우리말) · 완성 소인.
 */
@Composable
fun CatalogueCover(s: AppState, w: Work, e: io.github.graviton94.todayletters.core.Exhibition, year: Int, month: Int, number: Int, modifier: Modifier = Modifier) {
    val ink = Color(0xFF2A1F10); val soft = Color(0xFF76674F); val seal = Color(0xFF8E2A22)
    val ep = remember(e) { io.github.graviton94.todayletters.core.Draws.exhibitionPieces(w.kit.collection, e) }
    Column(modifier.fillMaxWidth().shadow(16.dp).background(Color(0xFFF3EAD6)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row { Caps("CATALOGUE", Color(0xFF7A5C2C), small = true, decorative = true, modifier = Modifier.weight(1f)); Caps("N° $number", Color(0xFF7A5C2C), small = true, decorative = true) }
        Text(e.title[w.series.original], style = Type.title.copy(fontFamily = Faces.display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic).of(w.series.original), color = ink)
        if (uiLang() != w.series.original) Text(e.title[uiLang()], style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = ink)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ep.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { pc -> AssetImage(pc.image, Modifier.weight(1f).aspectRatio(1f), sample = 8) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.catalogue_foot, w.name[uiLang()]), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft, modifier = Modifier.weight(1f))
            Box(Modifier.size(50.dp).graphicsLayer { rotationZ = -10f }.border(1.5.dp, seal, CircleShape), contentAlignment = Alignment.Center) {
                Text("COMPLET\n${roman(month)} · $year", style = Type.capsSm.copy(fontSize = 7.sp, lineHeight = 9.sp), color = seal, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 시리즈 안 맨 위 줄: ‹ 서재 · 지명 · 화폐. */
@Composable
fun SeriesHeader(s: AppState, title: String? = null, caps: String? = null) {
    val p = Ink.palette
    val w = s.currentWork
    Column {
        if (w.kit.hero.isNotEmpty()) AssetImage(w.kit.hero, Modifier.fillMaxWidth().height(6.dp), sample = 8)
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s2, end = Tokens.Space.s5, top = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.heightIn(min = 44.dp).pressable { s.go(Route.Library) }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                Text("‹ " + stringResource(R.string.tab_library), style = Type.small.ui(), color = p.inkSoft)
            }
            Text(w.kit.place, style = Type.capsSm, color = p.giltText, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            CurrencyLine(s, w)
        }
        if (title != null) Column(Modifier.padding(horizontal = Tokens.Space.s6).padding(top = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (caps != null) Caps(caps, p.giltText, small = true, decorative = true)
            Text(title, style = Type.title.ui(), color = p.ink)
        }
    }
}

/** 갤러리 (시리즈 안 이름표): 희귀도별 칸 · 이달의 전시 · 다음 이정표까지. */
@Composable
fun GalleryTab(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val id = w.series.id
    s.version
    val all = s.gallery(id)
    var tier by rememberSaveable { mutableStateOf(Tier.LETTER) }
    var open by remember { mutableStateOf<Piece?>(null) }
    var buying by remember { mutableStateOf<Piece?>(null) }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.tab_gallery), "COLLECTION")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.ownedCount(id)}", style = Type.title.copy(fontFamily = Faces.display), color = p.ink)
                Text(" / ${all.size}", style = Type.small, color = p.inkSoft, modifier = Modifier.padding(bottom = 3.dp))
            }
            // 편지의 그림 (v21): 읽은 편지의 그림 → 그림 산책 (자리별 설명)
            LetterPlates(s, w)
            // 이달의 전시
            s.exhibition(id)?.let { e ->
                val ep = s.exhibitionPieces(id)
                Row(Modifier.fillMaxWidth().background(p.hide).pressable { s.go(Route.Exhibition) }.padding(Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    AssetImage(ep.firstOrNull()?.image.orEmpty(), Modifier.size(44.dp), sample = 8)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.expo_line, e.title[uiLang()]), style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
                        Text(stringResource(R.string.expo_meta, s.daysLeftInMonth, ep.count { s.owns(id, it) }, ep.size), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                    Text("›", style = Type.heading, color = p.inkSoft)
                }
            }
            // 아직 한 점도 없으면: 왜 비었는지 + 오늘의 편지로 (U2)
            if (s.ownedCount(id) == 0) EmptyState(stringResource(R.string.empty_gallery_t), stringResource(R.string.empty_gallery_b), stringResource(R.string.today_open)) { s.go(Route.Inbox) }
            // 희귀도 칸
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                Tier.entries.forEach { t ->
                    val inTier = all.filter { it.tier == t }
                    if (inTier.isEmpty()) return@forEach
                    Column(Modifier.pressable { tier = t }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tierName(t), style = Type.small.ui(), color = if (tier == t) p.ink else p.inkSoft)
                        Text("${inTier.count { s.owns(id, it) }} / ${inTier.size}", style = Type.small.copy(fontFamily = Faces.display), color = p.inkSoft)
                        Box(Modifier.width(24.dp).height(2.dp).background(if (tier == t) p.giltText else Color.Transparent))
                    }
                }
            }
            val shown = all.filter { it.tier == tier }
            shown.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pc ->
                        val have = s.owns(id, pc)
                        Box(Modifier.weight(1f).aspectRatio(1f).then(if (have) Modifier else Modifier.border(1.dp, p.hair))
                            .pressable { if (have) open = pc else if (pc.tier != Tier.LETTER) buying = pc }) {
                            if (have) AssetImage(pc.image, Modifier.fillMaxSize(), sample = 4)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(stringResource(if (tier == Tier.LETTER) R.string.gallery_letter_note else R.string.gallery_draw_note), style = Type.small.ui(), color = p.inkSoft)
            val next = Achievements.series.first { it.id == "pieces_12" }
            if (s.ownedCount(id) < next.goal) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text(stringResource(R.string.gallery_next, next.goal), style = Type.small.ui(), color = p.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.gallery_next_gift), style = Type.small.ui(), color = p.inkSoft) }
                Bar(s.ownedCount(id).toFloat() / next.goal)
            }
        }
    }
    var framing by remember { mutableStateOf<Piece?>(null) }
    open?.let { pc -> PieceView(pc, walk = s.plateWalk(id, pc), frame = s.frameKind(pc), onFrame = { framing = pc }) { open = null } }
    framing?.let { pc -> FramePicker(s, w, pc) { framing = null } }
    buying?.let { pc ->
        val cost = Spend.PICK_PIECE.cost
        Ask(stringResource(R.string.pick_title, cost, w.kit.currency[uiLang()]), stringResource(R.string.pick_yes), stringResource(R.string.not_now),
            onYes = { s.buy(Spend.PICK_PIECE, id, pc); buying = null }, onNo = { buying = null })
    }
}

/** 걸린 작품 크게 보기: 액자에 끼운 채로 전체를, 확대 · 끌어 보기 (ZoomViewer). 편지의 그림이기도 하면 ‘그림 산책’, [onFrame] 이 있으면 ‘액자 바꾸기’. */
@Composable
fun PieceView(pc: Piece, walk: Pair<Int, () -> Unit>? = null, frame: io.github.graviton94.todayletters.data.Frames.Kind? = null, onFrame: (() -> Unit)? = null, onClose: () -> Unit) {
    val framed = frame?.let { rememberFramed(pc.image, it) }
    ZoomViewer(framed ?: rememberAsset(pc.image), pc.title[uiLang()], "${pc.date} · ${pc.collection}" + (frame?.let { " · " + frameName(it) } ?: ""), onClose,
        action = walk?.let { (n, go) -> stringResource(R.string.walk_spots, n) to { onClose(); go() } },
        action2 = onFrame?.let { stringResource(R.string.frame_change) to it })
}

/** 액자 바꾸기 (E3): 네 액자를 그 작품에 끼운 모습으로 보여 주고, 고르면 우표로. */
@Composable
fun FramePicker(s: AppState, w: Work, pc: Piece, onClose: () -> Unit) {
    val p = Ink.palette
    val cur = s.frameKind(pc)
    var pick by remember { mutableStateOf<io.github.graviton94.todayletters.data.Frames.Kind?>(null) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.frame_change), style = Type.heading.ui(), color = p.ink)
            Text(stringResource(R.string.frame_note), style = Type.small.ui(), color = p.inkSoft)
            io.github.graviton94.todayletters.data.Frames.Kind.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    row.forEach { k ->
                        Column(Modifier.weight(1f).pressable(enabled = k != cur) { pick = k }, horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().height(130.dp).background(Color(0xFF1E1811)).padding(10.dp), contentAlignment = Alignment.Center) {
                                rememberFramed(pc.image, k)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            }
                            Text(frameName(k), style = Type.small.ui(), color = p.ink)
                            Text(if (k == cur) stringResource(R.string.border_on) else "${Spend.FRAME.cost} ${w.kit.currency[uiLang()]}",
                                style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (k == cur) p.giltText else p.inkSoft)
                        }
                    }
                }
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
    pick?.let { k ->
        Ask(stringResource(R.string.spend_confirm, Spend.FRAME.cost, w.kit.currency[uiLang()]), stringResource(R.string.spend_yes), stringResource(R.string.not_now),
            onYes = { s.buy(Spend.FRAME, w.series.id, pc, k.name); pick = null; onClose() }, onNo = { pick = null })
    }
}

/**
 * 갤러리 맨 위 ‘편지의 그림’ (v21): 연 편지의 그림을 가로 줄로 (편지 번호 · 그림 이름 · 자리 수), 누르면 그림 산책.
 * 아직 안 연 편지의 그림은 미리 보이지 않게 빈 테두리 (다음 두 통까지만).
 */
@Composable
private fun LetterPlates(s: AppState, w: io.github.graviton94.todayletters.data.Work) {
    val p = Ink.palette
    val id = w.series.id
    val all = w.chapters.flatMapIndexed { ci, c -> c.letters.mapIndexed { li, l -> Triple(ci, li, c to l) } }.filter { it.third.second.plate != null }
    if (all.isEmpty()) return
    val opened = all.filter { (_, _, cl) -> s.progress(id, cl.first.id, cl.second.id).let { it.shown > 0 || it.done } }
    val ahead = all.filter { it !in opened }.take(2)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.plates_title), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
            Text("${opened.size} / ${all.size}", style = Type.small.copy(fontFamily = Faces.display), color = p.giltText)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val letters = w.chapters.flatMap { it.letters }
            opened.forEach { (ci, li, cl) ->
                val pl = cl.second.plate!!
                Column(Modifier.width(112.dp).pressable { s.go(Route.Artwork(id, li + 1, Route.Letter(id, ci + 1, li + 1, Route.Gallery))) },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssetImage("plates/${pl.image}", Modifier.fillMaxWidth().height(84.dp).border(Tokens.Stroke.hair, p.hair), sample = 4)
                    Text(stringResource(R.string.letter_n, roman(letters.indexOf(cl.second) + 1)) + " · " + pl.title[uiLang()],
                        style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 2)
                    if (pl.spots.isNotEmpty()) Capsule(stringResource(R.string.plates_spots, pl.spots.size))
                }
            }
            ahead.forEach { (_, _, cl) ->
                Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(84.dp).border(1.dp, p.hair), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.plates_locked), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk)
                    }
                    Text(stringResource(R.string.letter_n, roman(letters.indexOf(cl.second) + 1)), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk)
                }
            }
        }
    }
}

/** 이정표 메달 (D5): 레이크스 미술관의 실제 19세기 전람회 메달 여덟 (CC0, assets/medals/m1~8). 이름은 medal_1~8. */
private val medalNames = listOf(R.string.medal_1, R.string.medal_2, R.string.medal_3, R.string.medal_4, R.string.medal_5, R.string.medal_6, R.string.medal_7, R.string.medal_8)

/** 석고 본 (아직 받지 않은 메달): 흑백 · 밝게 · 대비 낮게, 따뜻한 흰빛. */
private val plaster = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0.2f, 0.2f, 0.2f, 0f, 140f,
    0.195f, 0.195f, 0.195f, 0f, 136f,
    0.185f, 0.185f, 0.185f, 0f, 127f,
    0f, 0f, 0f, 1f, 0f,
)))

/** 메달 하나: 받았으면 금속 그대로 (그림자), 아직이면 석고 본 + 진행 고리. */
@Composable
fun Medal(index: Int, earned: Boolean, progress: Float, size: androidx.compose.ui.unit.Dp) {
    val p = Ink.palette
    val bmp = rememberAsset("medals/m${(index % 8) + 1}.webp")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!earned) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val st = 2.5.dp.toPx(); val r = this.size.minDimension / 2 - st
            drawCircle(p.hair, r, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
            if (progress > 0f) drawArc(p.giltText, -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (bmp != null) Image(bmp, null, Modifier.size(size * 0.82f).then(if (earned) Modifier.shadow(5.dp, CircleShape) else Modifier),
            colorFilter = if (earned) null else plaster)
    }
}

/** 이정표 (시리즈 안 이름표): 메달 여덟 (누르면 자세히) 과 우표 쓰기. */
@Composable
fun MilestonesTab(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val id = w.series.id
    s.version
    val st = s.stats(id)
    var detail by remember { mutableStateOf<Int?>(null) }
    val got = Achievements.series.count { s.achievedOn(Achievements.key(it, id)) != null }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.tab_milestones), "JALONS")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Text(stringResource(R.string.medals_note, Achievements.series.size, got), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(Tokens.Space.s4))
            Achievements.series.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = Tokens.Space.s4)) {
                    row.forEachIndexed { k, a ->
                        val i = r * 3 + k
                        val day = s.achievedOn(Achievements.key(a, id))
                        Column(Modifier.weight(1f).pressable { detail = i }.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Medal(i, day != null, Achievements.progress(a, st), 92.dp)
                            Text(text("ach_${a.id}", a.id), style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, textAlign = TextAlign.Center, maxLines = 2)
                            Text(if (day != null) (if (day >= 0) stringResource(R.string.medal_got, shortDate(day)) else "✓") else "${a.metric(st).coerceAtMost(a.goal)} / ${a.goal}",
                                style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                            if (Achievements.grantsMasterpiece(i) && day == null) Text(stringResource(R.string.medal_master), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.giltText)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(Tokens.Space.s4))
            Shop(s, w)
        }
    }
    detail?.let { i -> MedalSheet(s, w, i) { detail = null } }
}

/** 메달 자세히: 이름 · 지금 몇 · 남은 만큼 (또는 받은 날) · 그 메달이 무엇인지 · 받으면 생기는 것. */
@Composable
private fun MedalSheet(s: AppState, w: Work, i: Int, onClose: () -> Unit) {
    val p = Ink.palette
    val a = Achievements.series[i]
    val st = s.stats(w.series.id)
    val day = s.achievedOn(Achievements.key(a, w.series.id))
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding().padding(Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Medal(i, day != null, Achievements.progress(a, st), 170.dp)
            Caps("JALON ${roman(i + 1)}", p.giltText, small = true, decorative = true)
            Text(text("ach_${a.id}", a.id), style = Type.title.ui(), color = p.ink, textAlign = TextAlign.Center)
            val now = a.metric(st).coerceAtMost(a.goal)
            Text(if (day != null) stringResource(R.string.medal_got, if (day >= 0) shortDate(day) else "✓") else stringResource(R.string.medal_left, now, a.goal - now),
                style = Type.small.ui(), color = p.inkSoft)
            if (day == null) Bar(Achievements.progress(a, st), Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.medal_credit, stringResource(medalNames[i % 8])) + if (day == null) "\n" + stringResource(R.string.medal_cast) else "",
                style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().border(1.dp, p.line).padding(Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (Achievements.grantsMasterpiece(i)) R.string.medal_gift_master else R.string.medal_gift, w.kit.currency[uiLang()]),
                    style = Type.small.ui(), color = p.ink)
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
}

/** 우표 쓰기 (E3): 이어 읽기 · 편지 · 갤러리 · 카드로 묶어서. 배움은 늘 무료. */
@Composable
private fun Shop(s: AppState, w: Work) {
    val p = Ink.palette
    val id = w.series.id
    val ctx = LocalContext.current
    var asked by remember { mutableStateOf<Spend?>(null) }
    var borders by remember { mutableStateOf(false) }
    Caps("SPEND", p.inkSoft, small = true, decorative = true)
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(stringResource(R.string.spend_title, w.kit.currency[uiLang()]), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
        CurrencyLine(s, w)
    }
    val frameHint = stringResource(R.string.spend_frame_hint)
    listOf(
        R.string.spend_g_streak to listOf(Triple(Spend.REST, R.string.spend_rest, stringResource(R.string.spend_rest_d, s.rests, Streak.MAX_RESTS))),
        R.string.spend_g_letter to listOf(Triple(Spend.EARLY_LETTER, R.string.spend_early, stringResource(R.string.spend_early_d))),
        R.string.spend_g_gallery to listOf(Triple(Spend.PICK_PIECE, R.string.spend_pick, stringResource(R.string.spend_pick_d)),
            Triple(Spend.FRAME, R.string.spend_frame, stringResource(R.string.spend_frame_d))),
        R.string.spend_g_card to listOf(Triple(Spend.BORDER, R.string.spend_border, stringResource(R.string.spend_border_d))),
    ).forEach { (group, items) ->
        Caps(stringResource(group), p.giltText, small = true, modifier = Modifier.padding(top = Tokens.Space.s4, bottom = 2.dp))
        items.forEach { (sp, title, desc) ->
            val can = s.wallet(id).balance >= sp.cost
            Hair()
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = Type.body.ui(), color = p.ink)
                    Text(desc, style = Type.small.ui(), color = p.inkSoft)
                }
                Box(Modifier.heightIn(min = 40.dp).border(1.dp, if (can || sp == Spend.BORDER) p.ink else p.hair)
                    .pressable(enabled = can || sp == Spend.BORDER) {
                        when (sp) {
                            Spend.PICK_PIECE -> s.go(Route.Gallery)
                            Spend.FRAME -> { android.widget.Toast.makeText(ctx, frameHint, android.widget.Toast.LENGTH_LONG).show(); s.go(Route.Gallery) }
                            Spend.BORDER -> borders = true
                            else -> asked = sp
                        }
                    }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center) {
                    Text("${sp.cost} ${w.kit.currency[uiLang()]}", style = Type.small.ui(), color = if (can || sp == Spend.BORDER) p.ink else p.hideInk)
                }
            }
        }
    }
    Hair()
    Text(stringResource(R.string.spend_free_note), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(top = Tokens.Space.s3, bottom = Tokens.Space.s6))
    asked?.let { sp ->
        Ask(stringResource(R.string.spend_confirm, sp.cost, w.kit.currency[uiLang()]), stringResource(R.string.spend_yes), stringResource(R.string.not_now),
            onYes = { s.buy(sp, id); asked = null }, onNo = { asked = null })
    }
    if (borders) BorderSheet(s, w) { borders = false }
}

/** 카드 테두리 고르기 · 사기 (E3 · E5). */
@Composable
fun BorderSheet(s: AppState, w: Work, onClose: () -> Unit) {
    val p = Ink.palette
    var buying by remember { mutableStateOf<io.github.graviton94.todayletters.core.Border?>(null) }
    s.version
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.spend_border), style = Type.heading.ui(), color = p.ink)
            Text(stringResource(R.string.border_note), style = Type.small.ui(), color = p.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                io.github.graviton94.todayletters.core.Border.entries.forEach { b ->
                    val owned = b.name in s.store.borders()
                    val on = s.store.border == b.name
                    Column(Modifier.weight(1f).pressable { if (owned) { s.store.border = b.name; s.bump() } else buying = b },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BorderSwatch(b, Modifier.fillMaxWidth().aspectRatio(1.3f), selected = on)
                        Text(stringResource(borderName(b)), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.ink, maxLines = 1)
                        Text(if (on) stringResource(R.string.border_on) else if (owned) stringResource(R.string.border_owned) else "${Spend.BORDER.cost} ${w.kit.currency[uiLang()]}",
                            style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = if (on) p.giltText else p.inkSoft)
                    }
                }
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
    buying?.let { b ->
        Ask(stringResource(R.string.spend_confirm, Spend.BORDER.cost, w.kit.currency[uiLang()]), stringResource(R.string.spend_yes), stringResource(R.string.not_now),
            onYes = { s.buy(Spend.BORDER, w.series.id, option = b.name); buying = null }, onNo = { buying = null })
    }
}

fun borderName(b: io.github.graviton94.todayletters.core.Border) = when (b) {
    io.github.graviton94.todayletters.core.Border.PLAIN -> R.string.border_plain
    io.github.graviton94.todayletters.core.Border.POSTMARK -> R.string.border_postmark
    io.github.graviton94.todayletters.core.Border.GILT -> R.string.border_gilt
    io.github.graviton94.todayletters.core.Border.PAINT -> R.string.border_paint
}

/** 테두리 견본 (작은 카드 모양). */
@Composable
fun BorderSwatch(b: io.github.graviton94.todayletters.core.Border, modifier: Modifier, selected: Boolean = false) {
    val p = Ink.palette
    val paper = Color(0xFFFBF7EE)
    Box(modifier.then(if (selected) Modifier.border(2.dp, p.ink).padding(3.dp) else Modifier).background(paper).then(when (b) {
        io.github.graviton94.todayletters.core.Border.PLAIN -> Modifier.border(1.dp, Color(0xFFCDBE9F))
        io.github.graviton94.todayletters.core.Border.POSTMARK -> Modifier.border(1.5.dp, Color(0xFF3D4C6E)).padding(3.dp).border(1.dp, Color(0xFF3D4C6E))
        io.github.graviton94.todayletters.core.Border.GILT -> Modifier.border(4.dp, Color(0xFFC9A456)).padding(4.dp).border(1.dp, Color(0xFF8A6526))
        io.github.graviton94.todayletters.core.Border.PAINT -> Modifier.border(6.dp, Brush.linearGradient(listOf(Color(0xFF9FB3C8), Color(0xFFE8D9A8), Color(0xFFC79A8A))), androidx.compose.ui.graphics.RectangleShape)
    }))
}

/** 10월 7일 · Oct 7. */
@Composable
fun shortDate(day: Long): String {
    val d = java.time.LocalDate.ofEpochDay(day)
    return if (uiHangul()) "${d.monthValue}월 ${d.dayOfMonth}일" else d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))
}

/** 이달의 전시: 주제 · 남은 날 · 전시 자료 (모으지 못한 것은 흐리게) · 다 모으면 도록 표지. */
@Composable
fun ExhibitionScreen(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val id = w.series.id
    val e = s.exhibition(id) ?: run { Text("") ; return }
    val ep = s.exhibitionPieces(id)
    val have = ep.count { s.owns(id, it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            AssetImage((ep.firstOrNull { s.owns(id, it) } ?: ep.firstOrNull())?.image.orEmpty(), Modifier.fillMaxSize(), sample = 2, alignment = Alignment.TopCenter)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x55000000), 0.4f to Color.Transparent, 1f to p.paper)))
            IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(Color(0xFFF7F0E1)) }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s6), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Caps("EXPOSITION", p.giltText, small = true, decorative = true)
            Text(e.title[uiLang()], style = Type.title.ui(), color = p.ink)
            Text(stringResource(R.string.expo_days, s.daysLeftInMonth), style = Type.small.ui(), color = p.inkSoft)
            Text(stringResource(R.string.expo_note), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(top = Tokens.Space.s2))
            Row(Modifier.padding(top = Tokens.Space.s3)) {
                Text(stringResource(R.string.expo_pieces), style = Type.small.ui(), color = p.ink, modifier = Modifier.weight(1f))
                Text("$have / ${ep.size}", style = Type.small, color = p.giltText)
            }
            ep.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pc -> AssetImage(pc.image, Modifier.weight(1f).aspectRatio(0.75f), gray = !s.owns(id, pc), sample = 4) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (have == ep.size && ep.isNotEmpty()) {
                Text(stringResource(R.string.expo_complete), style = Type.body.ui(), color = p.giltText, modifier = Modifier.padding(top = Tokens.Space.s2))
                val now = java.time.LocalDate.now()
                CatalogueCover(s, w, e, now.year, now.monthValue, s.catalogues(id).size.coerceAtLeast(1), Modifier.padding(top = Tokens.Space.s2))
            }
            Spacer(Modifier.height(Tokens.Space.s6))
        }
    }
}

/**
 * 이번 주 (E1): 요일마다 배운 날 (쉼표로 이은 날은 점선) · 읽은 편지 · 새로 ‘내 것’ · 따라 읽은 문장,
 * 날마다 억양 · 리듬 평균 (지난주와 견줌) · 이번 주 걸린 작품. 아래에 일요일 낭독회로.
 */
@Composable
fun WeekScreen(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val wk = s.week(w.series.id)
    val todayIdx = io.github.graviton94.todayletters.core.Recital.weekday(s.today)
    val letters = w.kit.weekdays.padEnd(7).take(7)
    var open by remember { mutableStateOf<Piece?>(null) }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.week_title), "CETTE SEMAINE")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Text(stringResource(R.string.week_range, shortDate(wk.start), shortDate(wk.start + 6)), style = Type.small.ui(), color = p.inkSoft)
            Row(Modifier.fillMaxWidth()) {
                letters.forEachIndexed { i, ch ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("$ch", style = Type.capsSm, color = if (i == todayIdx) p.ink else p.inkSoft)
                        Box(Modifier.size(30.dp).then(when {
                            i in wk.done -> Modifier.background(p.ink, CircleShape)
                            i in wk.rests -> { val g = p.giltText; Modifier.drawBehind { drawCircle(g, size.minDimension / 2 - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))) } }
                            i == todayIdx -> Modifier.border(1.6.dp, p.ink, CircleShape)
                            else -> Modifier.border(1.dp, p.hair, CircleShape)
                        }), contentAlignment = Alignment.Center) {
                            if (i in wk.done) Text("✓", style = Type.small, color = p.paper)
                            else if (i in wk.rests) Text(stringResource(R.string.week_rest), style = Type.small.ui().copy(fontSize = 10.sp), color = p.giltText)
                        }
                    }
                }
            }
            Text(stringResource(R.string.week_streak, s.streak) + if (wk.rests.isNotEmpty()) " · " + stringResource(R.string.week_rest_note) else "", style = Type.small.ui(), color = p.inkSoft)
            Row(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.hair)) {
                listOf(wk.letters to R.string.week_letters, wk.owned to R.string.week_owned, wk.sentences to R.string.week_sentences).forEachIndexed { k, (n, label) ->
                    if (k > 0) Box(Modifier.width(1.dp).height(64.dp).background(p.hair))
                    Column(Modifier.weight(1f).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = Type.title.copy(fontFamily = Faces.display), color = p.ink)
                        Text(stringResource(label), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                }
            }
            // 따라 읽기 점수: 날마다 억양(실선) · 리듬(점선)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.week_scores), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.week_scores_d), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            }
            if (wk.scores.isEmpty()) Text(stringResource(R.string.week_scores_empty), style = Type.small.ui(), color = p.inkSoft)
            else {
                val ink = p.ink; val gilt = p.giltText; val hair = p.hair
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val lo = 40f; val hi = 100f
                    fun y(v: Int) = size.height * 0.9f - (v.coerceIn(lo.toInt(), hi.toInt()) - lo) / (hi - lo) * size.height * 0.8f
                    fun x(i: Int) = size.width * (i + 0.5f) / 7f
                    listOf(60, 75, 90).forEach { v -> drawLine(hair, androidx.compose.ui.geometry.Offset(0f, y(v)), androidx.compose.ui.geometry.Offset(size.width, y(v)), 1f) }
                    val days = wk.scores.keys.sorted()
                    for ((sel, color, dash) in listOf(Triple(0, ink, null), Triple(1, gilt, androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))) {
                        val pts = days.map { d -> androidx.compose.ui.geometry.Offset(x(d), y(if (sel == 0) wk.scores[d]!!.first else wk.scores[d]!!.second)) }
                        for (k in 1 until pts.size) drawLine(color, pts[k - 1], pts[k], 2.2.dp.toPx(), pathEffect = dash)
                        pts.forEach { drawCircle(color, 3.dp.toPx(), it) }
                    }
                }
                Row {
                    Text("— " + stringResource(R.string.score_intonation), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.ink)
                    Text("   - - " + stringResource(R.string.score_rhythm), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.giltText, modifier = Modifier.weight(1f))
                    val now = wk.scores.values.let { v -> v.sumOf { it.first } / v.size }
                    wk.lastWeek?.let { (li, _) -> Text(stringResource(R.string.week_vs_last, (if (now >= li) "+" else "") + (now - li)), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.week_pieces), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
                Text("${wk.pieces.size}", style = Type.small, color = p.inkSoft)
            }
            if (wk.pieces.isEmpty()) Text(stringResource(R.string.week_pieces_empty), style = Type.small.ui(), color = p.inkSoft)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wk.pieces.forEach { pc -> AssetImage(pc.image, Modifier.size(72.dp).pressable { open = pc }, sample = 8) }
            }
            Spacer(Modifier.height(Tokens.Space.s2))
        }
        Box(Modifier.padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4)) { Primary(stringResource(R.string.week_recital)) { s.go(Route.Recital) } }
    }
    open?.let { pc -> PieceView(pc, frame = s.frameKind(pc)) { open = null } }
}

/**
 * 일요일 낭독회: 이번 주 내가 따라 읽은 마디를 이어 듣고, 첫 주와 억양 · 리듬을 견준다.
 * 같은 주에는 한 번 보상.
 */
@Composable
fun RecitalScreen(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val takes = remember(s.version) { s.weekTakes() }
    val secs = remember(takes) { takes.sumOf { (it.length() - 44).coerceAtLeast(0) } / (io.github.graviton94.todayletters.data.Wav.RATE * 2) }
    val (first, now) = remember(s.version) { s.scoreTrend() }
    var playing by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(s.away) { playing = false }
    val joined = remember(takes) {
        if (takes.isEmpty()) null else java.io.File(s.ctx.filesDir, "readings/week.wav").also { runCatching { io.github.graviton94.todayletters.data.Wav.join(takes, it) } }
    }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.recital_title), "DIMANCHE · LECTURE")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Text(stringResource(R.string.recital_meta, takes.size, secs / 60, secs % 60), style = Type.small.ui(), color = p.inkSoft)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                Box(Modifier.size(64.dp).background(if (joined != null) p.fill else p.hide, CircleShape).pressable(enabled = joined != null) {
                    if (playing) { s.recorder.stopPlaying(); playing = false } else joined?.let { f -> playing = true; s.recorder.play(f) { playing = false } }
                }, contentAlignment = Alignment.Center) { Text(if (playing) "■" else "▶", style = Type.heading, color = p.onFill) }
                Text(stringResource(if (takes.isEmpty()) R.string.recital_empty else R.string.recital_play), style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
            }
            Hair()
            Text(stringResource(R.string.recital_compare), style = Type.heading.ui(), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s6)) {
                listOf(R.string.score_intonation to (first?.first to now?.first), R.string.score_rhythm to (first?.second to now?.second)).forEach { (label, v) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(label), style = Type.small.ui(), color = p.inkSoft)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${v.first ?: "–"}", style = Type.heading.copy(fontFamily = Faces.display), color = p.inkSoft)
                            Text("  →  ", style = Type.small, color = p.hideInk)
                            Text("${v.second ?: "–"}", style = Type.display.copy(fontFamily = Faces.display), color = p.giltText)
                        }
                    }
                }
            }
            Text(stringResource(R.string.recital_note), style = Type.small.ui(), color = p.inkSoft)
        }
        Box(Modifier.padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4)) {
            if (s.recitalDone) Text(stringResource(R.string.recital_done), style = Type.small.ui(), color = p.inkSoft)
            else Primary(stringResource(R.string.recital_finish, w.kit.currency[uiLang()]), enabled = takes.isNotEmpty()) { s.recitalFinish() }
        }
    }
}

/** 낱말 성장 단계의 이름 (만남 → … → 내 것). */
val io.github.graviton94.todayletters.core.Growth.Stage.label: Int get() = when (this) {
    io.github.graviton94.todayletters.core.Growth.Stage.MEET -> R.string.stage_meet
    io.github.graviton94.todayletters.core.Growth.Stage.RECOGNIZE -> R.string.stage_recognize
    io.github.graviton94.todayletters.core.Growth.Stage.RECALL -> R.string.stage_recall
    io.github.graviton94.todayletters.core.Growth.Stage.WRITE -> R.string.stage_write
    io.github.graviton94.todayletters.core.Growth.Stage.SPEAK -> R.string.stage_speak
    io.github.graviton94.todayletters.core.Growth.Stage.OWN -> R.string.stage_own
}
