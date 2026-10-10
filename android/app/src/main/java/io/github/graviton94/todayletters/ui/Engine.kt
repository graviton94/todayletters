package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
    Earn.SEAL -> R.string.earn_seal
})

/**
 * 보상 순간: 어느 화면에서든 맨 위에. 오늘의 자료가 걸렸으면 그 그림이 주인공,
 * 아래에 받은 화폐 (무엇으로 얼마) 와 새 이정표.
 */
@Composable
fun RewardOverlay(s: AppState) {
    val m = s.reward ?: return
    val w = s.work(m.series.ifEmpty { s.current })
    val t = darkTone(w)
    val a = remember(m) { Animatable(0f) }
    LaunchedEffect(m) { a.animateTo(1f, tween(if (s.reducedMotion) 0 else 520)) }
    androidx.activity.compose.BackHandler { s.reward = null }
    Box(Modifier.fillMaxSize().background(t.paper).pressable(haptic = false) { }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6).graphicsLayer { alpha = a.value },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(stringResource(R.string.reward_caps), t.accent, decorative = true)
            Text(stringResource(if (m.piece != null) R.string.reward_piece else R.string.reward_title), style = Type.title.ui(), color = t.ink, textAlign = TextAlign.Center)
            m.piece?.let { pc ->
                Text(tierName(pc.tier), style = Type.small.ui(), color = t.accent)
                Box(Modifier.padding(top = Tokens.Space.s2).border(1.dp, t.ink.copy(alpha = 0.15f)).padding(10.dp).graphicsLayer { scaleX = 0.94f + 0.06f * a.value; scaleY = scaleX }) {
                    AssetImage(pc.image, Modifier.fillMaxWidth().aspectRatio(0.82f), sample = 1)
                }
                Text(pc.title[uiLang()], style = Type.body.ui(), color = t.ink, textAlign = TextAlign.Center)
                Text("${pc.date} · ${pc.collection}", style = Type.small.copy(fontSize = Tokens.Text.caps), color = t.faint, textAlign = TextAlign.Center)
            }
            if (m.milestone != null) Text(stringResource(R.string.reward_alone, m.milestone), style = Type.body.ui(), color = t.ink, textAlign = TextAlign.Center)
            m.achievements.forEach { id ->
                Text(stringResource(R.string.reward_ach, text("ach_$id", id)), style = Type.body.ui(), color = t.accent, textAlign = TextAlign.Center)
            }
            if (m.gain > 0) Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2)) {
                m.lines.forEach { (e, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(earnName(e), style = Type.small.ui(), color = t.soft, modifier = Modifier.weight(1f))
                        Text("+$n", style = Type.small, color = t.ink)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(t.ink.copy(alpha = 0.12f)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.reward_total, w.kit.currency[uiLang()]), style = Type.body.ui(), color = t.ink, modifier = Modifier.weight(1f))
                    Text("+${m.gain}", style = Type.display.copy(fontFamily = Faces.display), color = t.accent)
                }
            }
            Spacer(Modifier.height(Tokens.Space.s3))
            Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(t.ink).pressable {
                s.reward = null; if (m.piece != null) s.go(Route.Gallery)
            }, contentAlignment = Alignment.Center) {
                Text(stringResource(if (m.piece != null) R.string.reward_hang else R.string.reward_ok), style = Type.body.ui(), color = t.paper)
            }
            if (m.piece != null) Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).pressable { s.reward = null }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.close), style = Type.body.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = t.ink)
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
    open?.let { pc -> PieceView(pc) { open = null } }
    buying?.let { pc ->
        val cost = Spend.PICK_PIECE.cost
        Ask(stringResource(R.string.pick_title, cost, w.kit.currency[uiLang()]), stringResource(R.string.pick_yes), stringResource(R.string.not_now),
            onYes = { s.buy(Spend.PICK_PIECE, id, pc); buying = null }, onNo = { buying = null })
    }
}

/** 걸린 작품 크게 보기: 잘라 내지 않고 전체를, 확대 · 끌어 보기 (ZoomViewer). */
@Composable
fun PieceView(pc: Piece, onClose: () -> Unit) {
    ZoomViewer(rememberAsset(pc.image), pc.title[uiLang()], "${pc.date} · ${pc.collection}", onClose)
}

/** 이정표 (시리즈 안 이름표): 이 시리즈의 이정표와 화폐 쓰기. */
@Composable
fun MilestonesTab(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val id = w.series.id
    s.version
    val st = s.stats(id)
    var asked by remember { mutableStateOf<Spend?>(null) }
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.tab_milestones), "JALONS")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s3)) {
            Achievements.series.forEachIndexed { i, a ->
                val k = Achievements.key(a, id)
                val day = s.achievedOn(k)
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Text(roman(i + 1), style = Type.capsSm, color = if (day != null) p.giltText else p.hideInk, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text("ach_${a.id}", a.id), style = Type.body.ui(), color = p.ink)
                        if (day == null) Bar(Achievements.progress(a, st))
                        if (Achievements.grantsMasterpiece(i) && day == null) Text(stringResource(R.string.ach_gift), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                    Text(if (day != null && day >= 0) shortDate(day) else if (day != null) "✓" else "${a.metric(st).coerceAtMost(a.goal)} / ${a.goal}", style = Type.small, color = p.inkSoft)
                }
                Hair()
            }
            Spacer(Modifier.height(Tokens.Space.s6))
            Caps("SPEND", p.inkSoft, small = true, decorative = true)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.spend_title, w.kit.currency[uiLang()]), style = Type.heading.ui(), color = p.ink, modifier = Modifier.weight(1f))
                CurrencyLine(s, w)
            }
            listOf(
                Triple(Spend.REST, R.string.spend_rest, stringResource(R.string.spend_rest_d, s.rests, Streak.MAX_RESTS)),
                Triple(Spend.EARLY_LETTER, R.string.spend_early, stringResource(R.string.spend_early_d)),
                Triple(Spend.PICK_PIECE, R.string.spend_pick, stringResource(R.string.spend_pick_d)),
            ).forEach { (sp, title, desc) ->
                val can = s.wallet(id).balance >= sp.cost
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(title), style = Type.body.ui(), color = p.ink)
                        Text(desc, style = Type.small.ui(), color = p.inkSoft)
                    }
                    Box(Modifier.heightIn(min = 40.dp).border(1.dp, if (can) p.ink else p.hair)
                        .pressable(enabled = can) { if (sp == Spend.PICK_PIECE) s.go(Route.Gallery) else asked = sp }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("${sp.cost} ${w.kit.currency[uiLang()]}", style = Type.small.ui(), color = if (can) p.ink else p.hideInk)
                    }
                }
                Hair()
            }
        }
    }
    asked?.let { sp ->
        Ask(stringResource(R.string.spend_confirm, sp.cost, w.kit.currency[uiLang()]), stringResource(R.string.spend_yes), stringResource(R.string.not_now),
            onYes = { s.buy(sp, id); asked = null }, onNo = { asked = null })
    }
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
            if (have == ep.size && ep.isNotEmpty()) Text(stringResource(R.string.expo_complete), style = Type.body.ui(), color = p.giltText, modifier = Modifier.padding(top = Tokens.Space.s2))
            Spacer(Modifier.height(Tokens.Space.s6))
        }
    }
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
