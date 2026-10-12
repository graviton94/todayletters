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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.composed
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
import io.github.graviton94.todayletters.design.hangul

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

/** 대화창 밖(액티비티 화면)에서 잰 시스템 막대 두께. [FullDialog] 안에서만 값이 있다. */
private val LocalOuterBars = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.foundation.layout.PaddingValues?> { null }

/**
 * 화면을 가득 덮는 대화창 (편지 도착 · 낱말 상세 · 사진 크게 보기). 기기에 따라 대화창 창은 상태 바 · 내비게이션 바 두께를
 * 0 으로 받아서 아래 단추가 막대 밑에 깔린다. 그래서 바깥에서 잰 두께를 함께 넘기고, 안에서는 [statusBarsSafe] · [navigationBarsSafe] 를 쓴다.
 */
@Composable
fun FullDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalOuterBars provides bars, content = content)
    }
}

/** 상태 바 아래로: [statusBarsPadding] 과 같고, [FullDialog] 안에서는 바깥에서 잰 두께와 대화창이 받은 두께 중 큰 쪽. */
fun Modifier.statusBarsSafe(): Modifier = composed {
    val outer = LocalOuterBars.current
    if (outer == null) statusBarsPadding()
    else padding(top = maxOf(outer.calculateTopPadding(), WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
}

/** 내비게이션 바 위로: [navigationBarsPadding] 과 같고, [FullDialog] 안에서는 바깥에서 잰 두께와 대화창이 받은 두께 중 큰 쪽. */
fun Modifier.navigationBarsSafe(): Modifier = composed {
    val outer = LocalOuterBars.current
    if (outer == null) navigationBarsPadding()
    else padding(bottom = maxOf(outer.calculateBottomPadding(), WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
}

/** 이 시리즈의 빛깔로 칠한다. */
@Composable
fun InSeries(w: Work, content: @Composable () -> Unit) = SeriesTheme(w.kit.light?.let(::tone), w.kit.dark?.let(::tone), content)

/** 어두운 쪽 빛깔 (서재 카드 · 전환 화면은 테마와 상관없이 늘 어둡게). */
fun darkTone(w: Work): SeriesTone = w.kit.dark?.let(::tone) ?: SeriesTone(Color(0xFF121110), Color(0xFFECE7DE), Color(0xFFA29B90), Color(0xFF6A645B), Color(0xFFC9B48C))

/** 그림 하나 (assets 와 같은 경로, 받아 둔 것 먼저). 가볍게 줄여서 읽는다. 아직 받지 않았으면 빈 자리. */
@Composable
fun AssetImage(path: String, modifier: Modifier, gray: Boolean = false, sample: Int = 2, alignment: Alignment = Alignment.Center) {
    val ctx = LocalContext.current
    val bmp: ImageBitmap? = remember(path, sample, io.github.graviton94.todayletters.data.AssetFiles.generation) {
        if (path.isBlank()) null else runCatching {
            io.github.graviton94.todayletters.data.AssetFiles.open(ctx, path).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }
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

/** 요일 머리글자 (월~일), 앱 글자 언어로: M T W T F S S · 월 화 수 목 금 토 일. */
@Composable
fun weekLetters(): List<String> {
    val locale = if (uiHangul()) java.util.Locale.KOREAN else java.util.Locale.ENGLISH
    return java.time.DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.NARROW, locale) }
}

/** 이번 주: 요일 머리글자(앱 글자 언어)와 점 (배운 날 · 오늘 · 남은 날). */
@Composable
fun WeekDots(s: AppState, w: Work) {
    val p = Ink.palette
    val done = s.weekDone()
    val today = io.github.graviton94.todayletters.core.Recital.weekday(s.today)
    val letters = weekLetters()
    Row(Modifier.fillMaxWidth()) {
        letters.forEachIndexed { i, ch ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ch, style = Type.capsSm.hangul(uiHangul()), color = if (i <= today) p.ink else p.hideInk)
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
    Earn.SHADOW_PASS -> R.string.earn_shadow; Earn.READ_ALONE -> R.string.earn_alone; Earn.JOURNEY -> R.string.earn_journey
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
    return remember(art, kind, io.github.graviton94.todayletters.data.AssetFiles.generation) { io.github.graviton94.todayletters.data.Frames.framed(ctx, art, kind)?.asImageBitmap() }
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
            Caps(stringResource(R.string.reward_caps), if (master) gold else t.accent, decorative = true)
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
                    if (m.medals.isNotEmpty() && pc.tier >= Tier.PAINTING) Text(stringResource(R.string.reward_from_ach), style = Type.small.ui(), color = t.soft)
                }
            }
            if (m.milestone != null) Text(stringResource(R.string.reward_alone, m.milestone), style = Type.body.ui(), color = t.ink, textAlign = TextAlign.Center)
            // 새 메달: 금속 메달과 이름 (메달 진열장)
            m.medals.mapNotNull { s.medalBook.medal(it) }.forEach { md ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    MedalImage(md, true, 44.dp)
                    Text(stringResource(R.string.reward_ach, md.title[uiLang()]), style = Type.body.ui(), color = t.accent)
                }
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
        Row { Caps(stringResource(R.string.caps_catalogue), Color(0xFF7A5C2C), small = true, decorative = true, modifier = Modifier.weight(1f)); Caps("N° $number", Color(0xFF7A5C2C), small = true, decorative = true) }
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

/**
 * 시리즈 안 맨 위 줄 (v22): ‹ 서재 · 지명 · (?) · ⚙ — (?) 와 ⚙ 는 서재와 같이 늘 오른쪽 위.
 * 그 아래 한 줄: 배우는 말 바꾸기 단추 · 화폐.
 */
@Composable
fun SeriesHeader(s: AppState, title: String? = null, caps: String? = null, currency: Boolean = true) {
    val p = Ink.palette
    val w = s.currentWork
    Column {
        if (w.kit.hero.isNotEmpty()) AssetImage(w.kit.hero, Modifier.fillMaxWidth().height(6.dp), sample = 8)
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s2, end = Tokens.Space.s1, top = Tokens.Space.s1), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.heightIn(min = 44.dp).pressable { s.go(Route.Library) }.padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                Text("‹ " + stringResource(R.string.tab_library), style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
            }
            Text(w.kit.place, style = Type.capsSm, color = p.giltText, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            HeaderIcons(s)
        }
        Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s5), verticalAlignment = Alignment.CenterVertically) {
            LearnChip(s, w.series.id)
            Spacer(Modifier.weight(1f))
            if (currency) CurrencyLine(s, w)
        }
        // 칭호 · 레벨 (모든 작품 공통): 얇게, 누르면 서재의 나의 기록
        RankLine(s, Modifier.fillMaxWidth().pressable { s.go(Route.Record) }.padding(horizontal = Tokens.Space.s5).padding(top = 2.dp, bottom = 2.dp))
        if (title != null) Column(Modifier.padding(horizontal = Tokens.Space.s6).padding(top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (caps != null) Caps(caps, p.giltText, small = true, decorative = true)
            Text(title, style = Type.title.ui(), color = p.ink)
        }
    }
}

/** 맨 위 화면의 오른쪽 위: (?) 이 화면 도움말 (있을 때) · ⚙ 앱 설정. 서재와 시리즈 안 이름표가 같은 자리에. */
@Composable
fun HeaderIcons(s: AppState) {
    val p = Ink.palette
    val help = helpId(s.route)
    if (help != null) IconButton(stringResource(R.string.help), onClick = { s.coachAgain(help) }) { HelpGlyph(p.ink) }
    IconButton(stringResource(R.string.settings), onClick = { s.settingsOpen = true }) { Gear(p.ink) }
}

/**
 * 갤러리 (시리즈 안 이름표, APK 20 피드백): 세 묶음만 — 편지의 그림 (읽은 편지) · 모은 작품 (뽑기 · 상점) · 이달의 전시.
 * 묶음마다 가진 것만 세 칸, 나머지는 ‘더 보기’ (빈칸 줄은 두지 않는다).
 */
@Composable
fun GalleryTab(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val id = w.series.id
    s.version
    var open by remember { mutableStateOf<Piece?>(null) }
    var framing by remember { mutableStateOf<Piece?>(null) }
    val plates = w.chapters.flatMapIndexed { ci, c -> c.letters.mapIndexedNotNull { li, l -> l.plate?.let { Triple(ci, li, c to l) } } }
    val opened = plates.filter { (_, _, cl) -> s.progress(id, cl.first.id, cl.second.id).let { it.shown > 0 || it.done } }
    val collected = w.kit.collection.filter { s.owns(id, it) }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.tab_gallery))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
            // 편지의 그림: 연 편지의 그림, 최근 것부터 셋 → 그림 산책
            GalleryGroup(stringResource(R.string.plates_title), "${opened.size} / ${plates.size}", onMore = { s.go(Route.Catalogue(id)) }) {
                Thumbs(opened.takeLast(3).reversed().map { (ci, li, cl) ->
                    "plates/${cl.second.plate!!.image}" to { s.go(Route.Artwork(id, li + 1, Route.Letter(id, ci + 1, li + 1, Route.Gallery))) }
                })
            }
            // 모은 작품 (예전 ‘나의 도록’과 합침): 뽑기 · 상점으로 모은 소장품
            GalleryGroup(stringResource(R.string.gallery_collected), "${collected.size} / ${w.kit.collection.size}", onMore = { s.go(Route.Collection(id)) }) {
                Thumbs(collected.takeLast(3).reversed().map { pc -> pc.image to { open = pc } })
            }
            // 이달의 전시
            s.exhibition(id)?.let { e ->
                val ep = s.exhibitionPieces(id)
                val have = ep.filter { s.owns(id, it) }
                GalleryGroup(stringResource(R.string.gallery_expo), "${e.title[uiLang()]} · ${have.size} / ${ep.size}", onMore = { s.go(Route.Exhibition) }) {
                    Thumbs(have.take(3).map { pc -> pc.image to { open = pc } })
                }
            }
            Spacer(Modifier.height(Tokens.Space.s3))
        }
    }
    open?.let { pc -> PieceView(pc, walk = s.plateWalk(id, pc), frame = s.frameKind(pc), onFrame = { framing = pc }) { open = null } }
    framing?.let { pc -> FramePicker(s, w, pc) { framing = null } }
}

/** 갤러리의 한 묶음: 이름 · 가진 수 · 더 보기 ›, 그 아래 그림 칸. */
@Composable
private fun GalleryGroup(title: String, count: String, onMore: () -> Unit, content: @Composable () -> Unit) {
    val p = Ink.palette
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
                Text(count, style = Type.small.ui(), color = p.giltText, maxLines = 1)
            }
            Box(Modifier.heightIn(min = 44.dp).pressable(onClick = onMore).padding(start = Tokens.Space.s3), contentAlignment = Alignment.CenterEnd) {
                Text(stringResource(R.string.gallery_more) + " ›", style = Type.small.ui(), color = p.ink)
            }
        }
        content()
    }
}

/** 가진 그림만 세 칸 (모자라면 빈자리 없이 비워 둔다). 하나도 없으면 한 줄. */
@Composable
private fun Thumbs(items: List<Pair<String, () -> Unit>>) {
    val p = Ink.palette
    if (items.isEmpty()) { Text(stringResource(R.string.gallery_none), style = Type.small.ui(), color = p.hideInk); return }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(3).forEach { (image, go) ->
            AssetImage(image, Modifier.weight(1f).aspectRatio(1f).border(Tokens.Stroke.hair, p.hair).pressable(onClick = go), sample = 4)
        }
        repeat(3 - items.size.coerceAtMost(3)) { Spacer(Modifier.weight(1f)) }
    }
}

/** 갤러리 › 모은 작품 전부: 가진 소장품 (등급 순) 과 다 모은 이달의 전시의 도록 표지. */
@Composable
fun CollectionScreen(s: AppState, series: String) {
    val p = Ink.palette
    val w = s.work(series)
    s.version
    var open by remember { mutableStateOf<Piece?>(null) }
    var framing by remember { mutableStateOf<Piece?>(null) }
    val owned = w.kit.collection.filter { s.owns(series, it) }.sortedByDescending { it.tier.ordinal }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.gallery_collected), s, help = null, showBack = true, showSettings = false)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${owned.size} / ${w.kit.collection.size}", style = Type.small.ui(), color = p.giltText)
            if (owned.isEmpty()) Text(stringResource(R.string.gallery_none), style = Type.small.ui(), color = p.hideInk)
            owned.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pc -> AssetImage(pc.image, Modifier.weight(1f).aspectRatio(1f).pressable { open = pc }, sample = 4) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // 도록 (E4): 다 모은 이달의 전시
            val cats = s.catalogues(series)
            if (cats.isNotEmpty()) {
                Text(stringResource(R.string.overall_catalogues), style = Type.heading.ui(), color = p.ink, modifier = Modifier.padding(top = Tokens.Space.s3))
                cats.forEachIndexed { k, c -> CatalogueCover(s, w, c.first, c.second, c.third, k + 1) }
            }
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
    open?.let { pc -> PieceView(pc, walk = s.plateWalk(series, pc), frame = s.frameKind(pc), onFrame = { framing = pc }) { open = null } }
    framing?.let { pc -> FramePicker(s, w, pc) { framing = null } }
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
                    // 테두리는 작품마다 (그 작품의 화폐로 사니까, F19)
                    val owned = b.name in s.store.borders(w.series.id)
                    val on = s.store.border(w.series.id) == b.name
                    Column(Modifier.weight(1f).pressable { if (owned) { s.store.setBorder(w.series.id, b.name); s.bump() } else buying = b },
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
            Caps(stringResource(R.string.caps_exhibition), p.giltText, small = true, decorative = true)
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
    val letters = weekLetters()
    var open by remember { mutableStateOf<Piece?>(null) }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.week_title))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Text(stringResource(R.string.week_range, shortDate(wk.start), shortDate(wk.start + 6)), style = Type.small.ui(), color = p.inkSoft)
            Row(Modifier.fillMaxWidth()) {
                letters.forEachIndexed { i, ch ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(ch, style = Type.capsSm.hangul(uiHangul()), color = if (i == todayIdx) p.ink else p.inkSoft)
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
        SeriesHeader(s, stringResource(R.string.recital_title))
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
            // 기록이 없으면 "– → –" 대신 한 줄 (F24)
            if (first == null && now == null) Text(stringResource(R.string.recital_compare_empty), style = Type.small.ui(), color = p.inkSoft)
            else Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s6)) {
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
