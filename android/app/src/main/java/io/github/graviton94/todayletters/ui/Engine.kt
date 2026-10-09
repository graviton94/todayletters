package io.github.graviton94.todayletters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Achievements
import io.github.graviton94.todayletters.core.Breaks
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.Growth
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Memory
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.StampArt
import io.github.graviton94.todayletters.core.VisitorGame
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import java.time.LocalDate

/*
 * 보상 · 수집 엔진의 화면들. 모두 시리즈 공통이고, 이름 · 그림 · 목록만 키트([io.github.graviton94.todayletters.core.Kit])에서 온다.
 * 우표 · 보상 순간 · 수집 앨범(진열장 · 물건 · 여행기 · 기념 우표 · 진도) · 정기 소포 · 손님(미니게임 틀 셋).
 */

/** 문자열 id 를 이름으로 (업적 ach_<id>). 없으면 0. */
@Composable
private fun named(name: String): Int {
    val ctx = LocalContext.current
    return remember(name) { ctx.resources.getIdentifier(name, "string", ctx.packageName) }
}

@Composable
private fun text(name: String, fallback: String = ""): String = named(name).let { if (it != 0) stringResource(it) else fallback }

/** 우표 한 장: 톱니 테두리 · 액면 · (그림이 있으면) 그림. */
@Composable
fun StampView(label: String, value: String, color: Color, image: String = "", w: Dp = 22.dp, h: Dp = 26.dp, faded: Boolean = false) {
    Box(Modifier.size(w, h).graphicsLayer { alpha = if (faded) 0.35f else 1f }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFF4EBD5))
            val r = size.minDimension * 0.06f
            var x = r; while (x < size.width) { drawCircle(Color(0x33000000), r, Offset(x, 0f)); drawCircle(Color(0x33000000), r, Offset(x, size.height)); x += r * 3 }
            var y = r; while (y < size.height) { drawCircle(Color(0x33000000), r, Offset(0f, y)); drawCircle(Color(0x33000000), r, Offset(size.width, y)); y += r * 3 }
            val m = size.minDimension * 0.1f
            drawRect(color, Offset(m, m), Size(size.width - m * 2, size.height - m * 2))
        }
        if (image.isNotEmpty()) PlateThumb(image, true, Modifier.padding(w * 0.16f).fillMaxSize())
        Column(Modifier.fillMaxSize().padding(w * 0.12f), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            if (w > 40.dp) Text(label, style = Type.caps.copy(fontSize = (w.value / 11).sp), color = Color(0xFFF4EBD5), maxLines = 1) else Spacer(Modifier)
            Text(value, style = Type.caps.copy(fontSize = (w.value / (if (w > 40.dp) 9 else 3.2f)).sp), color = Color(0xFFF4EBD5), maxLines = 1)
        }
    }
}

/** 위쪽 우표 잔액 (모든 화면 공통 모양, 색 · 액면은 키트). */
@Composable
fun StampChip(s: AppState, dark: Boolean = false) {
    val w = s.works.firstOrNull() ?: return
    val p = Ink.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clearAndSetSemantics { }) {
        StampView("", w.kit.currency.label, Color(w.kit.currency.color))
        Text("${s.wallet.balance}", style = Type.body.copy(fontFamily = Faces.display, fontWeight = FontWeight.SemiBold), color = if (dark) Color(0xFFEADFC8) else p.ink)
    }
}

/** 혼자 읽기 % 고리. */
@Composable
fun AloneRing(pct: Int, size: Dp = 64.dp, track: Color, fill: Color, ink: Color) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = androidx.compose.ui.graphics.drawscope.Stroke(size.toPx() * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = st.width / 2
            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - st.width, this.size.height - st.width), style = st)
            drawArc(fill, -90f, 360f * pct / 100f, false, Offset(inset, inset), Size(this.size.width - st.width, this.size.height - st.width), style = st)
        }
        Text("$pct%", style = Type.small.copy(fontFamily = Faces.display, fontWeight = FontWeight.SemiBold, fontSize = (size.value / 4.2f).sp), color = ink)
    }
}

// ── 보상 순간 ─────────────────────────────────────────

/** 하루의 일 · 편지 · 손님 · 소포 뒤에 한 번: 우표가 떨어지고, 금빛 물건 · 이정표 · 업적 · 기념 우표가 차례로. */
@Composable
fun RewardOverlay(s: AppState) {
    val r = s.reward ?: return
    val p = Ink.palette
    val w = s.works.firstOrNull() ?: return
    val ui = uiLang()
    val t = remember(r) { Animatable(if (s.reducedMotion) 1f else 0f) }
    LaunchedEffect(r) { t.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    androidx.activity.compose.BackHandler { s.reward = null }
    Box(Modifier.fillMaxSize().background(Color(0xEB0A0705)).clickable(enabled = false) {}, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(stringResource(R.string.reward_caps), style = Type.caps, color = Color(0xFFD2A955), modifier = Modifier.padding(top = Tokens.Space.s6).clearAndSetSemantics { })
            if (r.stamps > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    repeat(minOf(4, 1 + r.stamps / 6)) { k ->
                        Box(Modifier.graphicsLayer {
                            val d = ((t.value - k * 0.12f) / 0.5f).coerceIn(0f, 1f)
                            translationY = (1f - d) * -160f; rotationZ = (k - 1.5f) * 6f; alpha = d
                        }) { StampView("", w.kit.currency.label, Color(w.kit.currency.color), w = 44.dp, h = 54.dp) }
                    }
                }
                Text(stringResource(R.string.reward_stamps, r.stamps), style = Type.display.copy(fontFamily = Faces.display), color = Color(0xFFEADFC8))
            }
            r.milestone?.let { m ->
                RewardCard(dark = true) {
                    AloneRing(m, 56.dp, Color(0xFF33281D), Color(0xFFD2A955), Color(0xFFEADFC8))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.reward_alone, m), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFEADFC8))
                        Text(stringResource(R.string.reward_alone_d), style = Type.small.ui(), color = Color(0xFFA8977C))
                    }
                }
            }
            r.items.forEach { word ->
                RewardCard(dark = false) {
                    Text(word.icon, fontSize = 34.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.reward_item), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFF8F6A27))
                        val learn = s.room(w.series.id).learn
                        Text(word.text[ui], style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF2A2118))
                        Text(listOfNotNull(word.text[learn], word.ipa.takeIf { it.isNotEmpty() && learn == w.series.original }?.let { "[$it]" }).joinToString(" "),
                            style = Type.small.of(learn), color = Color(0xFF5A4E3E))
                    }
                }
            }
            r.achievements.forEachIndexed { i, id ->
                RewardCard(dark = true, gold = true) {
                    Seal(i)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text("ach_$id", id), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFEADFC8))
                        val gift = r.gifts.getOrNull(i)
                        Text(if (gift != null) stringResource(R.string.reward_gift, gift.label) else text("ach_${id}_d"), style = Type.small.ui(), color = Color(0xFFA8977C))
                    }
                }
            }
            if (r.gifts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.gifts.forEach { g -> StampView(g.label, g.value, Color(g.color), g.image, 74.dp, 92.dp) }
            }
            Spacer(Modifier.height(Tokens.Space.s4))
            Primary(stringResource(R.string.reward_album), Modifier.fillMaxWidth()) { s.reward = null; s.go(Route.Gallery) }
            Secondary(stringResource(R.string.close), Modifier.fillMaxWidth(), small = true) { s.reward = null }
        }
    }
}

@Composable
private fun RewardCard(dark: Boolean, gold: Boolean = false, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (dark) Color(0xFF1E1712) else Color(0xFFF4EBD5))
            .border(1.dp, if (gold) Color(0xFFD2A955) else if (dark) Color(0xFF4A3B2B) else Color(0xFFD9CCAE)).padding(Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) { content() }
}

/** 업적 밀랍 봉인. */
@Composable
private fun Seal(i: Int, on: Boolean = true, size: Dp = 48.dp) {
    val colors = listOf(0xFF8E2A22, 0xFFB8913E, 0xFF3F6B4A, 0xFF3D5A8F)
    val c = Color(colors[i % colors.size])
    Canvas(Modifier.size(size)) {
        if (on) {
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = 0.85f), c), center = Offset(this.size.width * 0.35f, this.size.height * 0.3f)))
            drawCircle(Color(0x40FFFFFF), this.size.minDimension * 0.32f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        } else drawCircle(Color(0xFF4A3B2B), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
    }
}

// ── 수집 앨범 (갤러리 탭) ─────────────────────────────

private enum class AlbumTab(val label: Int) { CABINET(R.string.album_cabinet), ITEMS(R.string.album_items), JOURNAL(R.string.album_journal), STAMPS(R.string.album_stamps), PROGRESS(R.string.album_progress) }

@Composable
fun CollectionTab(s: AppState) {
    val p = Ink.palette
    s.version
    var tab by remember { mutableStateOf(AlbumTab.CABINET) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_gallery), s, help = null, showBack = false, showSettings = true) { StampChip(s); Spacer(Modifier.width(Tokens.Space.s2)) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlbumTab.entries.forEach { t ->
                val on = t == tab
                Box(Modifier.heightIn(min = 40.dp).background(if (on) p.fill else Color.Transparent).border(1.dp, if (on) p.fill else p.hair).pressable { tab = t }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(t.label), style = Type.small.ui(), color = if (on) p.onFill else p.ink)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            s.works.forEach { w ->
                when (tab) {
                    AlbumTab.CABINET -> CabinetPane(s, w)
                    AlbumTab.ITEMS -> ItemsPane(s, w)
                    AlbumTab.JOURNAL -> JournalPane(s, w)
                    AlbumTab.STAMPS -> StampsPane(s, w)
                    AlbumTab.PROGRESS -> Unit
                }
            }
            if (tab == AlbumTab.PROGRESS) ProgressPane(s)
            Spacer(Modifier.height(Tokens.Space.s4))
        }
    }
}

/** 진열장: 키트의 벽 · 바닥 색 위에 받은 작품 액자, 아래에 정기 소포 · 오늘의 손님. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CabinetPane(s: AppState, w: Work) {
    val id = w.series.id
    val ui = uiLang()
    val cab = w.kit.cabinet
    val plates = w.chapters.flatMapIndexed { ci, c -> c.letters.mapIndexedNotNull { li, l -> l.plate?.let { Triple(ci, li, c to l) } } }
    Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(cab.wall), 0.78f to Color(cab.wall), 0.78f to Color(cab.floor), 1f to Color(cab.floor))).padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(cab.place[ui].uppercase(), style = Type.caps, color = Color(0xFFD2A955), modifier = Modifier.clearAndSetSemantics { })
        Text(cab.title[ui], style = Type.title.ui(), color = Color(0xFFEADFC8))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            plates.forEach { (ci, li, cl) ->
                val (c, l) = cl
                val pr = s.progress(id, c.id, l.id)
                val alone = pr.done && s.letterAlone(id, c.id, l)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.pressable(enabled = pr.done) { s.go(Route.Artwork(id, li + 1, Route.Letter(id, ci + 1, li + 1, Route.Gallery))) }) {
                    // 혼자 읽은 편지의 그림은 금빛 이중 액자
                    Box(Modifier.size(96.dp, 76.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFC9A24E), Color(0xFF7A5A20), Color(0xFFC9A24E))))
                        .then(if (alone) Modifier.border(2.dp, Color(0xFFFFE7A0)) else Modifier).padding(if (alone) 7.dp else 5.dp)) {
                        PlateThumb(l.plate!!.image, pr.done, Modifier.fillMaxSize())
                    }
                    Text(if (pr.done) l.plate!!.title[ui] else "· · ·", style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFFC9BBA0), maxLines = 1)
                }
            }
        }
        Text(stringResource(R.string.cabinet_gold), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = Color(0xFFA8977C))
    }
    ParcelCard(s, w)
    VisitorCard(s)
}

@Composable
private fun ParcelCard(s: AppState, w: Work) {
    val kit = w.kit.parcel ?: return
    val p = Ink.palette
    val sent = s.parcelSent(w.series.id)
    Column(Modifier.fillMaxWidth().background(p.leaf).border(1.dp, p.hair).pressable { s.go(Route.Parcel(w.series.id)) }.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.parcel_title), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
            Text(if (sent) stringResource(R.string.parcel_sent_short) else "${s.wallet.balance.coerceAtMost(kit.cost)} / ${kit.cost}", style = Type.small.ui(), color = p.giltText)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(p.hair)) {
            Box(Modifier.fillMaxWidth(if (sent) 1f else (s.wallet.balance.toFloat() / kit.cost).coerceIn(0f, 1f)).height(6.dp).background(p.giltText))
        }
        Text(stringResource(R.string.parcel_line, kit.label[uiLang()], w.name[uiLang()]), style = Type.small.ui(), color = p.inkSoft)
    }
}

@Composable
fun VisitorCard(s: AppState) {
    val (w, v) = s.visitorToday() ?: return
    val p = Ink.palette
    val done = s.store.visitorDay == s.today
    Row(Modifier.fillMaxWidth().background(p.leaf).border(1.dp, p.hair).pressable(enabled = !done) { s.go(Route.Visitor) }.padding(Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        VisitorFigure(p.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.visitor_today), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.giltText)
            Text(v.name[uiLang()], style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
            Text(stringResource(when (v.game) { VisitorGame.LISTEN -> R.string.game_listen; VisitorGame.TORN -> R.string.game_torn; VisitorGame.TELEGRAM -> R.string.game_telegram }), style = Type.small.ui(), color = p.inkSoft)
        }
        Text(if (done) "✓" else "+4", style = Type.body.copy(fontFamily = Faces.display), color = if (done) p.correct else p.giltText)
    }
    @Suppress("UNUSED_EXPRESSION") w
}

@Composable
private fun VisitorFigure(c: Color) = Canvas(Modifier.size(40.dp, 48.dp)) {
    val w = size.width; val h = size.height
    drawCircle(c, w * 0.2f, Offset(w / 2, h * 0.2f))
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.18f, h); lineTo(w * 0.24f, h * 0.45f); quadraticTo(w / 2, h * 0.32f, w * 0.76f, h * 0.45f); lineTo(w * 0.82f, h); close() }, c)
    drawLine(c, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.12f), 3.dp.toPx())
}

/** 물건: 아이콘이 있는 낱말. 낱말을 떠올리기 단계까지 올리면 금빛. */
@Composable
private fun ItemsPane(s: AppState, w: Work) {
    val p = Ink.palette
    val ui = uiLang()
    val learn = s.room(w.series.id).learn
    val cm = s.cardMap()
    val items = w.chapters.flatMap { c -> c.letters.flatMapIndexed { li, l -> l.words.mapIndexedNotNull { i, word ->
        if (word.icon.isEmpty()) null else Triple(word, s.cardKey(w.series.id, c.id, l.id, i), "${c.id} · ${roman(c.letters.indexOf(l) + 1)}")
    } } }
    val found = items.count { cm.containsKey(it.second) }
    Text(stringResource(R.string.items_count, found, items.size), style = Type.small.ui(), color = p.inkSoft)
    items.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (word, key, src) ->
                val card = cm[key]
                val gold = Growth.known(card)
                Column(
                    Modifier.weight(1f).heightIn(min = 120.dp).background(if (card != null) Color(0xFFF4EBD5) else p.hide)
                        .border(if (gold) 2.dp else 1.dp, if (gold) Color(0xFFC9A24E) else p.hair).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(if (card != null) word.icon else "?", fontSize = 30.sp, color = p.hideInk)
                    if (card != null) {
                        Text(word.text[ui], style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF2A2118), textAlign = TextAlign.Center, maxLines = 1)
                        Text(word.text[learn], style = Type.small.of(learn), color = Color(0xFF8F6A27), maxLines = 1)
                        Text(stringResource(Growth.stage(card).label), style = Type.small.ui().copy(fontSize = Tokens.Text.capsSm * 1.1f), color = Color(0xFF6E6150))
                    } else Text(src, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.hideInk)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text(stringResource(R.string.items_note), style = Type.small.ui(), color = p.inkSoft)
}

/** 성장 단계 이름 (만남 … 내 것). */
val Growth.Stage.label: Int get() = when (this) {
    Growth.Stage.MEET -> R.string.stage_meet
    Growth.Stage.RECOGNIZE -> R.string.stage_recognize
    Growth.Stage.RECALL -> R.string.stage_recall
    Growth.Stage.WRITE -> R.string.stage_write
    Growth.Stage.SPEAK -> R.string.stage_speak
    Growth.Stage.OWN -> R.string.stage_own
}

/** 여행기: 도시마다 한 쪽. 편지에 나온 장소가 차고, 다 차면 도시 소인. */
@Composable
private fun JournalPane(s: AppState, w: Work) {
    val p = Ink.palette
    val ui = uiLang()
    val seenPlaces = w.chapters.flatMap { c -> c.letters.filter { s.progress(w.series.id, c.id, it.id).shown > 0 }.flatMap { l -> l.moments } }
        .filterIsInstance<io.github.graviton94.todayletters.core.Moment.Location>().map { it.place }.toSet() + "station"
    val firstPlate = w.chapters.flatMap { it.letters }.firstOrNull { it.plate != null }?.plate?.image.orEmpty()
    w.kit.journal.forEach { page ->
        val got = page.places.count { it in seenPlaces }
        val full = got == page.places.size
        Box(Modifier.fillMaxWidth().background(Color(0xFFF4EBD5)).border(1.dp, Color(0xFFD9CCAE)).padding(Tokens.Space.s4)) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Text("${page.city[ui]} · ${page.years}", style = Type.title.ui(), color = Color(0xFF2A2118))
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    PlateThumb(firstPlate, true, Modifier.size(120.dp, 90.dp).graphicsLayer { rotationZ = -2.5f }.border(4.dp, Color(0xFFFBF6EA)))
                    Column(Modifier.weight(1f).graphicsLayer { rotationZ = 2f }.background(Color(0xFFE9DCC0)).border(1.dp, Color(0xFF8F6A27)).padding(8.dp)) {
                        Text(page.ticketLine.uppercase(), style = Type.caps.copy(fontSize = Tokens.Text.capsSm), color = Color(0xFF5A4626), modifier = Modifier.clearAndSetSemantics { })
                        Text(page.ticket[ui], style = Type.small.ui(), color = Color(0xFF5A4626))
                    }
                }
                page.places.forEach { pid ->
                    val pl = w.places.firstOrNull { it.id == pid } ?: return@forEach
                    val on = pid in seenPlaces
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), modifier = Modifier.heightIn(min = 36.dp)) {
                        CheckMark(on, 20.dp)
                        Text(pl.name[ui], style = Type.body.ui(), color = if (on) Color(0xFF2A2118) else Color(0xFF968871), modifier = Modifier.weight(1f))
                        if (on) Text(stringResource(R.string.map_open), style = Type.small.ui(), color = Color(0xFF8F6A27), modifier = Modifier.pressable { openMaps(s.ctx, pl, pl.name[ui]) }.padding(4.dp))
                    }
                }
            }
            // 도시 소인
            Box(Modifier.align(Alignment.BottomEnd).size(84.dp).graphicsLayer { rotationZ = -12f; alpha = if (full) 0.85f else 0.35f }.border(2.dp, Color(0xFF9A3B22), CircleShape), contentAlignment = Alignment.Center) {
                Text("${page.city[Lang.FR].uppercase()}\n${page.years}\n$got / ${page.places.size}", style = Type.caps.copy(fontSize = 10.sp, lineHeight = 13.sp), color = Color(0xFF9A3B22), textAlign = TextAlign.Center)
            }
        }
    }
    Text(stringResource(R.string.journal_note), style = Type.small.ui(), color = p.inkSoft)
}

/** 기념 우표: 업적마다 한 장. 아직 없는 것은 흐리게. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StampsPane(s: AppState, w: Work) {
    val p = Ink.palette
    val have = s.store.stamps
    Text(stringResource(R.string.stamps_count, w.kit.stamps.count { "${w.series.id}:${it.id}" in have }, w.kit.stamps.size), style = Type.small.ui(), color = p.inkSoft)
    FlowRow(Modifier.fillMaxWidth().background(Color(0xFFF4EBD5)).border(1.dp, Color(0xFFD9CCAE)).padding(Tokens.Space.s4),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        w.kit.stamps.forEach { st ->
            val on = "${w.series.id}:${st.id}" in have
            StampView(st.label, st.value, Color(st.color), if (on) st.image else "", 86.dp, 106.dp, faded = !on)
        }
    }
    Text(stringResource(R.string.stamps_note), style = Type.small.ui(), color = p.inkSoft)
}

/** 진도: 혼자 읽기 · 성장 단계별 낱말 · 지난 4주 · 업적. */
@Composable
private fun ProgressPane(s: AppState) {
    val p = Ink.palette
    val st = s.stats()
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
        AloneRing(st.readAlone, 76.dp, p.hair, p.giltText, p.ink)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.alone_title, st.readAlone), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
            Text(Growth.nextMilestone(st.readAlone)?.let { stringResource(R.string.alone_next, it) } ?: stringResource(R.string.alone_all), style = Type.small.ui(), color = p.inkSoft)
        }
    }
    // 성장 곡선: 단계별 낱말 수 (아래에서 위로 자라는 막대)
    val counts = Memory.counts(s.cards())
    val max = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
    Text(stringResource(R.string.growth_title), style = Type.small.ui(), color = p.giltText)
    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Growth.Stage.entries.forEachIndexed { i, stg ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${counts[i]}", style = Type.small.copy(fontFamily = Faces.display), color = p.ink)
                Box(Modifier.fillMaxWidth().height((70f * counts[i] / max + 4).dp).background(p.giltText.copy(alpha = 0.35f + 0.13f * i)))
                Text(stringResource(stg.label), style = Type.small.ui().copy(fontSize = Tokens.Text.capsSm), color = p.inkSoft, maxLines = 1)
            }
        }
    }
    Text(stringResource(R.string.growth_note), style = Type.small.ui(), color = p.inkSoft)
    // 지난 4주
    val today = s.today
    val days = s.store.doneDays()
    Text(stringResource(R.string.weeks_title, st.streak, st.bestStreak), style = Type.small.ui(), color = p.giltText)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        (0 until 2).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until 14).forEach { c ->
                    val d = today - 27 + r * 14 + c
                    Box(Modifier.weight(1f).aspectRatio(1f).background(if (d in days) p.giltText else p.hair.copy(alpha = if (d > today) 0.15f else 0.6f)))
                }
            }
        }
    }
    // 업적
    Text(stringResource(R.string.ach_title, s.store.achievements.size, Achievements.all.size), style = Type.small.ui(), color = p.giltText)
    Achievements.all.chunked(4).forEachIndexed { r, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEachIndexed { c, a ->
                val on = a.id in s.store.achievements
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Seal(r * 4 + c, on, 46.dp)
                        if (!on) Text("${(Achievements.progress(a, st) * 100).toInt()}%", style = Type.small.copy(fontSize = Tokens.Text.capsSm), color = p.inkSoft)
                    }
                    Text(text("ach_${a.id}", a.id), style = Type.small.ui().copy(fontSize = Tokens.Text.capsSm), color = if (on) p.ink else p.inkSoft, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ── 정기 소포 ─────────────────────────────────────────

@Composable
fun ParcelScreen(s: AppState, id: String) {
    val p = Ink.palette
    val w = s.work(id)
    val kit = w.kit.parcel
    val ui = uiLang()
    s.version
    var opened by remember { mutableStateOf<io.github.graviton94.todayletters.core.ParcelReturn?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.parcel_title), s, help = null, showBack = true, showSettings = false) { StampChip(s); Spacer(Modifier.width(Tokens.Space.s2)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            if (kit == null) { Text(stringResource(R.string.parcel_none), style = Type.body.ui(), color = p.inkSoft); return@Column }
            val ret = opened
            if (ret == null) {
                // 포장된 소포: 갈색 종이 · 끈 · 주소
                Box(Modifier.fillMaxWidth().aspectRatio(1.5f).background(Color(0xFFB08A5A)).border(1.dp, Color(0xFF7A5A30)), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 0.dp)) {
                        Box(Modifier.align(Alignment.Center).width(10.dp).fillMaxSize().background(Color(0xFFE9DCC0)))
                        Box(Modifier.align(Alignment.Center).height(10.dp).fillMaxWidth().background(Color(0xFFE9DCC0)))
                    }
                    Column(Modifier.background(Color(0xFFF4EBD5)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${w.fullName}", style = Type.body.copy(fontFamily = Faces.display), color = Color(0xFF2A2118))
                        Text(w.chapters.firstOrNull()?.letters?.firstOrNull()?.place.orEmpty(), style = Type.small.copy(fontFamily = Faces.display), color = Color(0xFF5A4E3E))
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { StampView("", w.kit.currency.label, Color(w.kit.currency.color), w = 34.dp, h = 42.dp) }
                }
                Text(stringResource(R.string.parcel_line, kit.label[ui], w.name[ui]), style = Type.body.ui(), color = p.ink)
                Text(stringResource(R.string.parcel_how, kit.cost), style = Type.small.ui(), color = p.inkSoft)
                val sent = s.parcelSent(id)
                if (sent) Text(stringResource(R.string.parcel_sent, LocalDate.now().plusMonths(1).withDayOfMonth(1).toString()), style = Type.body.ui(), color = p.correct)
                Primary(stringResource(R.string.parcel_send, kit.cost), enabled = !sent && s.wallet.balance >= kit.cost) { opened = s.sendParcel(w) }
                if (!sent && s.wallet.balance < kit.cost) Text(stringResource(R.string.parcel_short, kit.cost - s.wallet.balance), style = Type.small.ui(), color = p.inkSoft)
            } else {
                // 답례: 실제 편지의 한 문장 + 그림
                val (ch, letter) = w.chapters.firstNotNullOfOrNull { c -> c.letters.firstOrNull { it.id == ret.letter }?.let { c.id to it } } ?: (null to null)
                Text(ret.title[ui], style = Type.title.ui(), color = p.ink)
                val m = letter?.messages?.getOrNull(ret.message)
                val view = s.room(id)
                if (m != null) Slip(seed = 77, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.text[view.learn], style = Type.target.of(view.learn), color = p.slipInk)
                        Text(m.text[view.read], style = Type.base.of(view.read), color = p.slipSoft)
                        Text("— ${w.fullName}", style = Type.small.copy(fontFamily = Faces.display), color = p.slipSoft)
                    }
                }
                if (ret.image.isNotEmpty()) SketchImage(ret.image)
                @Suppress("UNUSED_EXPRESSION") ch
                Secondary(stringResource(R.string.close)) { s.back() }
            }
        }
    }
}

@Composable
private fun SketchImage(path: String) {
    val ctx = LocalContext.current
    val bmp = remember(path) { runCatching { ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
    if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).border(6.dp, Color(0xFFF4EBD5)),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
}


// ── 손님 (미니게임 틀 셋) ─────────────────────────────

@Composable
fun VisitorScreen(s: AppState) {
    val p = Ink.palette
    val (w, v) = s.visitorToday() ?: run { LaunchedEffect(Unit) { s.back() }; return }
    val ui = uiLang()
    var won by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(v.name[ui], s, help = null, showBack = true, showSettings = false) { StampChip(s); Spacer(Modifier.width(Tokens.Space.s2)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                VisitorFigure(p.ink)
                Text(stringResource(when (v.game) { VisitorGame.LISTEN -> R.string.game_listen_d; VisitorGame.TORN -> R.string.game_torn_d; VisitorGame.TELEGRAM -> R.string.game_telegram_d }),
                    style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
            }
            val game = remember(v) {
                // 틀에 필요한 재료가 없으면 (예: 그림이 둘 미만) 전보로
                if (v.game == VisitorGame.LISTEN && doneWithSpots(s, w).size < 2) VisitorGame.TELEGRAM
                else if (v.game == VisitorGame.TORN && doneLetters(s, w).isEmpty()) VisitorGame.TELEGRAM else v.game
            }
            when (game) {
                VisitorGame.LISTEN -> ListenGame(s, w) { won = true }
                VisitorGame.TORN -> TornGame(s, w) { won = true }
                VisitorGame.TELEGRAM -> TelegramGame(s, w) { won = true }
            }
        }
        Column(Modifier.background(p.paper).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            Primary(stringResource(R.string.visitor_finish), enabled = won) { s.back(); s.visitorDone() }
        }
    }
}

private fun doneLetters(s: AppState, w: Work) = w.chapters.flatMap { c -> c.letters.filter { s.progress(w.series.id, c.id, it.id).done }.map { c.id to it } }
private fun doneWithSpots(s: AppState, w: Work) = doneLetters(s, w).filter { (_, l) -> l.plate?.spots?.any { it.word != null } == true }

/** 듣고 고르기: 그림 속 자리의 낱말을 듣고, 그 낱말이 있는 그림을 고른다 (두 번 들으면 글자). */
@Composable
private fun ListenGame(s: AppState, w: Work, onWin: () -> Unit) {
    val p = Ink.palette
    val view = s.room(w.series.id)
    val pool = remember { doneWithSpots(s, w) }
    val pick = remember { pool.random() }
    val (ch, letter) = pick
    val spots = letter.plate!!.spots.withIndex().filter { it.value.word != null }
    val sp = remember { spots.random() }
    val choices = remember { (pool.filter { it != pick }.shuffled().take(3) + pick).shuffled() }
    val audio = s.narrator.path(w.series.id, ch, letter.id, "s${sp.index + 1}_${view.learn.code}")
    var plays by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { s.narrator.play(audio); plays = 1 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        SpeakerButton(false, stringResource(R.string.a11y_listen_word)) { s.narrator.play(audio); plays++ }
        Text(if (plays >= 2 || chosen != null) sp.value.word!!.text[view.learn] else "· · ·", style = Type.title.of(view.learn), color = p.ink)
    }
    choices.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            row.forEach { (_, l) ->
                val right = l.id == letter.id
                Box(Modifier.weight(1f).aspectRatio(1.25f)
                    .border(3.dp, when { chosen == null -> Color.Transparent; right -> p.correct; chosen == l.id -> p.wrong; else -> Color.Transparent })
                    .pressable(enabled = chosen == null) { chosen = l.id; if (right) onWin() }) {
                    PlateThumb(l.plate!!.image, true, Modifier.fillMaxSize())
                }
            }
        }
    }
    if (chosen != null) {
        val ok = chosen == letter.id
        Text(if (ok) stringResource(R.string.review_right) else stringResource(R.string.game_try), style = Type.body.ui(), color = if (ok) p.correct else p.wrong)
        if (!ok) Secondary(stringResource(R.string.game_again)) { chosen = null }
        if (ok) Text("${sp.value.title[uiLang()]} · ${sp.value.word!!.text[view.learn]} = ${sp.value.word!!.text[view.read]}", style = Type.small.ui(), color = p.inkSoft)
    }
}

/** 조각 잇기: 찢어진 편지 한 문장의 조각을 순서대로 누른다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TornGame(s: AppState, w: Work, onWin: () -> Unit) {
    val p = Ink.palette
    val view = s.room(w.series.id)
    val (ch, letter, line) = remember {
        val (c, l) = doneLetters(s, w).random()
        Triple(c, l, l.messages.maxBy { it.text[view.learn].length })
    }
    val words = Breaks.plain(line.text[view.learn]).split(Regex("\\s+")).filter { it.isNotEmpty() }
    // 3~5 조각으로 (낱말 수에 맞춰)
    val n = (words.size / 4).coerceIn(3, 5).coerceAtMost(words.size)
    val pieces = remember { words.chunked((words.size + n - 1) / n).map { it.joinToString(" ") } }
    val order = remember { pieces.indices.shuffled().let { if (it == pieces.indices.toList()) it.reversed() else it } }
    var placed by remember { mutableIntStateOf(0) }
    var miss by remember { mutableStateOf<Int?>(null) }
    Slip(seed = 31, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Tokens.Space.s4).heightIn(min = 90.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(pieces.take(placed).joinToString(" ").ifEmpty { "…" }, style = Type.target.of(view.learn), color = p.slipInk)
            if (placed == pieces.size) Text(line.text[view.read], style = Type.base.of(view.read), color = p.slipSoft)
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        order.forEachIndexed { k, i ->
            if (i < placed) return@forEachIndexed
            Box(Modifier.graphicsLayer { rotationZ = ((k * 37) % 9 - 4).toFloat() }.background(Color(0xFFF4EBD5))
                .border(1.dp, if (miss == i) p.wrong else Color(0xFFD9CCAE)).pressable {
                    if (i == placed) { placed++; miss = null; if (placed == pieces.size) { s.narrator.play(s.narrator.path(w.series.id, ch, letter.id, "m${letter.messages.indexOf(line) + 1}_${view.learn.code}")); onWin() } }
                    else miss = i
                }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(pieces[i], style = Type.base.of(view.learn), color = Color(0xFF2A2118))
            }
        }
    }
    if (placed == pieces.size) Text(stringResource(R.string.review_right), style = Type.body.ui(), color = p.correct)
}

/** 전보: 낱말 셋을 듣고 받아 적는다 (모은 낱말에서). */
@Composable
private fun TelegramGame(s: AppState, w: Work, onWin: () -> Unit) {
    val p = Ink.palette
    val view = s.room(w.series.id)
    val words = remember {
        val keys = s.cards().map { it.key }.ifEmpty { doneLetters(s, w).flatMap { (c, l) -> l.words.indices.map { s.cardKey(w.series.id, c, l.id, it) } } }
        keys.shuffled().take(3).mapNotNull { k -> s.cardWord(k)?.let { (_, cl, i) -> Triple(cl.first, cl.second, i) } }
    }
    if (words.isEmpty()) { Text(stringResource(R.string.review_empty), style = Type.body.ui(), color = p.inkSoft); LaunchedEffect(Unit) { onWin() }; return }
    var at by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var mark by remember { mutableStateOf<Memory.Mark?>(null) }
    val (ch, l, i) = words[at.coerceAtMost(words.size - 1)]
    val answer = firstSense(l.words[i].text[view.learn])
    val audio = s.narrator.path(w.series.id, ch, l.id, "w${i + 1}_${view.learn.code}")
    LaunchedEffect(at) { s.narrator.play(audio) }
    // 전보 용지
    Column(Modifier.fillMaxWidth().background(Color(0xFFE9DCC0)).border(1.dp, Color(0xFF8F6A27)).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text("TÉLÉGRAMME · ${at + 1} / ${words.size}", style = Type.caps, color = Color(0xFF5A4626), modifier = Modifier.clearAndSetSemantics { })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            SpeakerButton(false, stringResource(R.string.a11y_listen_word)) { s.narrator.play(audio) }
            BasicTextField(
                typed, { if (mark == null) typed = it }, singleLine = true,
                textStyle = Type.title.copy(fontFamily = Faces.text, color = Color(0xFF2A2118)), cursorBrush = SolidColor(Color(0xFF2A2118)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) mark = Memory.grade(answer, typed) }),
                modifier = Modifier.weight(1f).border(BorderStroke(1.dp, Color(0xFF8F6A27))).padding(10.dp),
            )
        }
        if (mark != null) Text(if (mark == Memory.Mark.WRONG) "$answer · ${l.words[i].text[view.read]}" else "✓ $answer · ${l.words[i].text[view.read]}",
            style = Type.body.ui(), color = if (mark == Memory.Mark.WRONG) p.wrong else p.correct)
    }
    if (mark == null) Primary(stringResource(R.string.play_check), enabled = typed.isNotBlank(), small = true) { mark = Memory.grade(answer, typed) }
    else if (at < words.size - 1) Primary(stringResource(R.string.ob_next), small = true) { at++; typed = ""; mark = null }
    else LaunchedEffect(Unit) { onWin() }
}
