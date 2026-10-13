package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Border
import io.github.graviton94.todayletters.core.Piece
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Spend
import io.github.graviton94.todayletters.core.Streak
import io.github.graviton94.todayletters.core.Access
import io.github.graviton94.todayletters.core.Plan
import io.github.graviton94.todayletters.core.Price
import io.github.graviton94.todayletters.data.Billing
import io.github.graviton94.todayletters.data.seriesPlan
import io.github.graviton94.todayletters.data.Frames
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 상점 (작품 안 넷째 이름표, v4 시안): 이름 아래 가진 화폐 한 줄 (92 우표), 아래로 작은 그림 · 이름 · 한 줄 · 금빛 값의 줄 —
 * 갤러리 칸 열기 · 액자 바꾸기 · 내일 편지 미리 열기 · 쉼표 · 카드 테두리. 설명 글은 두지 않는다.
 * 맨 아래 금테 배너 ‘모든 편지 열기’ → 구매 화면 ([Route.Purchase]).
 */
@Composable
fun ShopTab(s: AppState) {
    val p = Ink.palette
    val w = s.currentWork
    val id = w.series.id
    s.version
    val cur = w.kit.currency[uiLang()]
    var asked by remember { mutableStateOf<Spend?>(null) }
    var picking by remember { mutableStateOf(false) }
    var framingPick by remember { mutableStateOf(false) }
    var borders by remember { mutableStateOf(false) }
    var buying by remember { mutableStateOf<Piece?>(null) }
    var framing by remember { mutableStateOf<Piece?>(null) }
    // 미리 보기 그림: 아직 걸지 않은 소장품 하나 (갤러리 칸), 가진 작품 하나를 금 액자에 (액자)
    val unowned = w.kit.collection.filter { !s.owns(id, it) && s.available(it) }
    val ownedPieces = s.gallery(id).filter { s.owns(id, it) && s.available(it) }
    val previewPick = unowned.firstOrNull() ?: w.kit.collection.firstOrNull { s.available(it) }
    val previewFrame = (ownedPieces.lastOrNull() ?: previewPick)?.image
    Column(Modifier.fillMaxSize()) {
        SeriesHeader(s, stringResource(R.string.tab_shop), currency = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            // 가진 화폐: 상점 이름 아래 한 줄 (92 우표)
            Row(Modifier.padding(start = Tokens.Space.s6 - Tokens.Space.s5).semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${s.wallet(id).balance}", style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.giltText)
                Text(cur, style = Type.small.ui(), color = p.inkSoft)
            }
            val items = listOf(
                ShopItem(Spend.PICK_PIECE, stringResource(R.string.spend_pick), stringResource(R.string.spend_pick_d)) {
                    if (previewPick != null) AssetImage(previewPick.image, Modifier.fillMaxSize(), sample = 8) else ShopGlyph(Spend.PICK_PIECE)
                },
                ShopItem(Spend.FRAME, stringResource(R.string.spend_frame), stringResource(R.string.spend_frame_d)) {
                    val framed = previewFrame?.let { rememberFramed(it, Frames.Kind.MASTER) }
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E1811)).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (framed != null) Image(framed, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else ShopGlyph(Spend.FRAME)
                    }
                },
                ShopItem(Spend.EARLY_LETTER, stringResource(R.string.spend_early), null) { ShopGlyph(Spend.EARLY_LETTER) },
                ShopItem(Spend.REST, stringResource(R.string.spend_rest), stringResource(R.string.spend_rest_d, s.rests, Streak.MAX_RESTS)) { ShopGlyph(Spend.REST) },
                ShopItem(Spend.BORDER, stringResource(R.string.spend_border), stringResource(R.string.spend_border_d)) {
                    BorderSwatch(Border.GILT, Modifier.size(width = 34.dp, height = 26.dp))
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                items.forEach { item ->
                    val can = s.canBuy(item.sp, id)
                    ShopRow(item.title, item.sub, item.sp.cost, cur, can, onClick = {
                        when (item.sp) {
                            Spend.PICK_PIECE -> picking = true
                            Spend.FRAME -> framingPick = true
                            Spend.BORDER -> borders = true
                            else -> asked = item.sp
                        }
                    }, art = item.art)
                }
            }
            // 맨 아래 금테 배너 하나: 모든 편지 열기 → 구매 화면
            UnlockBanner(s, Modifier.padding(top = Tokens.Space.s3))
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
    asked?.let { sp ->
        Ask(stringResource(R.string.spend_confirm, sp.cost, cur), stringResource(R.string.spend_yes), stringResource(R.string.not_now),
            onYes = { s.buy(sp, id); asked = null }, onNo = { asked = null })
    }
    if (picking) PiecePicker(stringResource(R.string.pick_sheet_title), unowned, stringResource(R.string.pick_none), onPick = { buying = it; picking = false }) { picking = false }
    if (framingPick) PiecePicker(stringResource(R.string.frame_pick_title), ownedPieces, stringResource(R.string.frame_none), onPick = { framing = it; framingPick = false }) { framingPick = false }
    framing?.let { pc -> FramePicker(s, w, pc) { framing = null } }
    if (borders) BorderSheet(s, w) { borders = false }
    buying?.let { pc ->
        Ask(stringResource(R.string.pick_title, Spend.PICK_PIECE.cost, cur), stringResource(R.string.pick_yes), stringResource(R.string.not_now),
            onYes = { s.buy(Spend.PICK_PIECE, id, pc); buying = null }, onNo = { buying = null })
    }
}

/** 상점의 물건 하나: 무엇을 사는가 · 이름 · 짧은 한 줄 · 미리 보기 그림. */
private class ShopItem(val sp: Spend, val title: String, val sub: String?, val art: @Composable () -> Unit)

/**
 * 상점 줄 (v4 시안): 둥근 테두리 줄 하나에 왼쪽 작은 네모 그림 (44dp) · 이름과 흐린 한 줄 · 오른쪽 금빛 값 알약.
 * 살 수 없으면 줄은 누를 수 없고 값 알약은 테두리만. 값은 숫자만 보이고, 읽어 줄 때는 ‘120 우표’.
 */
@Composable
private fun ShopRow(title: String, sub: String?, cost: Int, cur: String, can: Boolean, onClick: () -> Unit, art: @Composable () -> Unit) {
    val p = Ink.palette
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).background(p.leaf, shape).border(Tokens.Stroke.hair, p.line, shape).clip(shape)
            .pressable(enabled = can, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(p.hide), contentAlignment = Alignment.Center) { art() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val pill = RoundedCornerShape(Tokens.Radius.round)
        Box(
            Modifier.heightIn(min = 24.dp).then(if (can) Modifier.background(p.gilt, pill) else Modifier.border(1.dp, p.hair, pill))
                .padding(horizontal = 10.dp, vertical = 2.dp).semantics { contentDescription = "$cost $cur" },
            contentAlignment = Alignment.Center,
        ) { Text("$cost", style = Type.small.ui().copy(fontSize = Tokens.Text.caps, fontWeight = FontWeight.SemiBold), color = if (can) p.onFill else p.hideInk, maxLines = 1) }
    }
}

/** 그림이 없는 상점 카드의 작은 그림: 편지 봉투와 봉랍 (미리 열기) · 이번 주 점 하나가 빈 고리 (쉼표). */
@Composable
private fun ShopGlyph(sp: Spend) {
    val p = Ink.palette
    val wax = Color(0xFF9A3027)
    when (sp) {
        Spend.REST -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i ->
                Box(Modifier.size(6.dp).then(if (i == 3) Modifier.border(1.5.dp, p.giltText, CircleShape) else Modifier.background(if (i < 3) p.giltText else p.hair, CircleShape)))
            }
        }
        else -> Canvas(Modifier.size(width = 32.dp, height = 22.dp)) {
            val st = Stroke(1.dp.toPx())
            val w = size.width; val h = size.height
            drawRect(p.paper, Offset.Zero, Size(w, h))
            drawRect(p.inkSoft, Offset.Zero, Size(w, h), style = st)
            val flap = Path().apply { moveTo(0f, 0f); lineTo(w / 2, h * 0.58f); lineTo(w, 0f) }
            drawPath(flap, p.inkSoft, style = st)
            drawCircle(wax, h * 0.16f, Offset(w / 2, h * 0.58f))
        }
    }
}

/** 고르기 시트 (갤러리 칸 · 액자): 그림 칸 셋씩. 고를 것이 없으면 한 줄. */
@Composable
private fun PiecePicker(title: String, pieces: List<Piece>, empty: String, onPick: (Piece) -> Unit, onClose: () -> Unit) {
    val p = Ink.palette
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).background(p.paper).pressable(haptic = false) { }.navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(title, style = Type.heading.ui(), color = p.ink)
            if (pieces.isEmpty()) Text(empty, style = Type.small.ui(), color = p.inkSoft)
            pieces.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { pc ->
                        Column(Modifier.weight(1f).pressable { onPick(pc) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AssetImage(pc.image, Modifier.fillMaxWidth().aspectRatio(1f), sample = 8)
                            Text(pc.title[uiLang()], style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Secondary(stringResource(R.string.close), onClick = onClose)
        }
    }
}

/**
 * 상점 맨 아래 금테 배너 하나: 모든 편지 열기 · 시리즈마다 잠긴 장 · 구독 월 값 · 이 시리즈 값 ›. 누르면 구매 화면.
 * 모두 열려 있으면 (구독 · 다 소장) 그렇다고만.
 */
@Composable
fun UnlockBanner(s: AppState, modifier: Modifier = Modifier) {
    val p = Ink.palette
    s.version
    val ent = s.entitlement
    val ranges = s.works.filter { !ent.owns(it.series.id) }.mapNotNull { w ->
        Access.lockedRange(w.chapters)?.let { r -> stringResource(R.string.unlock_range, w.name[uiLang()], roman(r.first), roman(r.last)) }
    }
    Row(
        modifier.fillMaxWidth().background(p.leaf).border(1.5.dp, p.gilt).pressable { s.go(Route.Purchase) }.padding(Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.unlock_all), style = Type.heading.ui(), color = p.giltText)
            if (ranges.isEmpty()) Text(stringResource(R.string.unlock_open), style = Type.small.ui(), color = p.inkSoft)
            else {
                Text(ranges.joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                Text(stringResource(R.string.unlock_prices, s.billing.price(Plan.Monthly).formatted, s.billing.price(s.currentWork.seriesPlan).formatted),
                    style = Type.small.ui(), color = p.ink)
            }
        }
        Text("›", style = Type.heading, color = p.giltText)
    }
}

/**
 * 구매 화면 (모든 편지 열기): 연 구독 (기본 선택 · 할인) · 월 구독 · 지금 시리즈 소장. 결제는 [Billing] 한 곳에서.
 * 들어오는 곳: 잠긴 장 (III장부터) · 상점 배너 · 나의 기록의 이용권 줄. 값은 Play 값, Play 상품이 없으면 계획한 값과 ‘곧 열려요’.
 * 자동 결제 · 해지 · 구매 복원은 Play 정책상 작은 글씨로 늘 보인다.
 */
@Composable
fun PurchaseScreen(s: AppState) {
    val p = Ink.palette
    s.version
    val ctx = LocalContext.current
    val w = s.currentWork
    val ent = s.entitlement
    val b = s.billing
    val own = w.seriesPlan
    val plans = listOf(Plan.Yearly, Plan.Monthly, own)
    var pick by rememberSaveable { mutableIntStateOf(if (ent.onPlan(Plan.Monthly)) 1 else 0) }
    var note by remember { mutableStateOf<Int?>(null) }
    val yearly = b.price(Plan.Yearly)
    val monthly = b.price(Plan.Monthly)
    // 할인은 두 값에서 계산한다 (둘 다 Play 값이거나 둘 다 계획한 값이고 같은 돈일 때만)
    val off = if (yearly.fromPlay == monthly.fromPlay && yearly.currency == monthly.currency) Access.yearlyDiscount(monthly.micros, yearly.micros) else 0
    val range = Access.lockedRange(w.chapters)
    val name = w.name[uiLang()]
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.unlock_all), s, help = null, showBack = true, showSettings = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(stringResource(R.string.buy_title), style = Type.title.ui(), color = p.ink, modifier = Modifier.padding(bottom = Tokens.Space.s2))
            PlanCard(stringResource(R.string.plan_yearly), off.takeIf { it > 0 }?.let { stringResource(R.string.plan_off, it) }, yearly.formatted,
                stringResource(R.string.plan_yearly_d, perMonth(yearly)), pick == 0,
                if (ent.onPlan(Plan.Yearly)) stringResource(R.string.plan_active) else null) { pick = 0 }
            PlanCard(stringResource(R.string.plan_monthly), null, monthly.formatted, stringResource(R.string.plan_monthly_d), pick == 1,
                if (ent.onPlan(Plan.Monthly)) stringResource(R.string.plan_active) else null) { pick = 1 }
            PlanCard(stringResource(R.string.plan_series, name), null, b.price(own).formatted,
                range?.let { stringResource(R.string.plan_series_d, roman(it.first), roman(it.last)) } ?: stringResource(R.string.fine_series), pick == 2,
                if (w.series.id in ent.owned) stringResource(R.string.plan_owned) else null) { pick = 2 }
            val plan = plans[pick]
            val sub = plan !is Plan.Series
            when {
                sub && ent.subscribed -> Cta(stringResource(R.string.buy_manage), true) {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Billing.manageUrl(ctx.packageName)))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                !sub && w.series.id in ent.owned -> Cta(stringResource(R.string.plan_owned), false) { }
                b.available(plan) -> Cta(when (plan) {
                    Plan.Yearly -> stringResource(R.string.cta_yearly)
                    Plan.Monthly -> stringResource(R.string.cta_monthly)
                    is Plan.Series -> stringResource(R.string.cta_series, name)
                }, true) { Billing.activityOf(ctx)?.let { b.buy(it, plan) } }
                else -> Cta(stringResource(R.string.buy_soon), false) { }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(when (plan) { Plan.Yearly -> R.string.fine_yearly; Plan.Monthly -> R.string.fine_monthly; is Plan.Series -> R.string.fine_series }),
                    style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft, textAlign = TextAlign.Center)
                Box(Modifier.heightIn(min = 48.dp).pressable { b.restore { e -> note = if (e == null) R.string.buy_offline else R.string.buy_restored } }
                    .padding(horizontal = Tokens.Space.s4), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.buy_restore), style = Type.small.ui().copy(fontSize = Tokens.Text.caps, textDecoration = TextDecoration.Underline), color = p.inkSoft)
                }
                note?.let { Text(stringResource(it), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft) }
            }
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
}

/** 연 구독을 달로 나눈 값: Play 값이면 그 돈의 표기로, 아니면 ₩. */
private fun perMonth(y: Price): String {
    val micros = Access.perMonth(y.micros)
    if (!y.fromPlay) return Price.won(micros / 1_000_000)
    return runCatching {
        val cur = java.util.Currency.getInstance(y.currency)
        java.text.NumberFormat.getCurrencyInstance(java.util.Locale.getDefault()).apply {
            currency = cur; maximumFractionDigits = cur.defaultFractionDigits.coerceAtLeast(0); minimumFractionDigits = maximumFractionDigits
        }.format(micros / 1_000_000.0)
    }.getOrElse { Price.won(micros / 1_000_000) }
}

/** 고르는 칸 하나: 이름 (할인 표 · 이용 중) · 값 · 한 줄. 고른 칸은 금테. */
@Composable
private fun PlanCard(title: String, tag: String?, price: String, sub: String, on: Boolean, state: String?, onClick: () -> Unit) {
    val p = Ink.palette
    Column(
        Modifier.fillMaxWidth().background(p.leaf).border(if (on) 2.dp else Tokens.Stroke.hair, if (on) p.gilt else p.line)
            .pressable(role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(title, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
            if (tag != null) Box(Modifier.background(p.gilt, RoundedCornerShape(Tokens.Radius.round)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(tag, style = Type.small.ui().copy(fontSize = Tokens.Text.caps, fontWeight = FontWeight.SemiBold), color = p.onFill)
            }
            if (state != null) Capsule(state, CapsuleKind.DONE)
            Spacer(Modifier.weight(1f))
            Text(price, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, maxLines = 1)
        }
        Text(sub, style = Type.small.ui(), color = p.inkSoft)
    }
}

/** 결제 단추: 살 수 있으면 금빛으로 채운 단추, 아니면 테두리만 (‘곧 열려요’ · 소장함). */
@Composable
private fun Cta(label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = Ink.palette
    Box(
        Modifier.padding(top = Tokens.Space.s2).fillMaxWidth().heightIn(min = Tokens.Size.button)
            .then(if (enabled) Modifier.background(p.gilt) else Modifier.border(1.dp, p.line))
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = if (enabled) p.onFill else p.inkSoft) }
}
