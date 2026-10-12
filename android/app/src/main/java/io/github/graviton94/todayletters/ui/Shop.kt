package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Border
import io.github.graviton94.todayletters.core.Piece
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.Spend
import io.github.graviton94.todayletters.core.Streak
import io.github.graviton94.todayletters.data.Billing
import io.github.graviton94.todayletters.data.Frames
import io.github.graviton94.todayletters.data.Work
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 상점 (작품 안 넷째 이름표, APK 20 피드백): 위에 가진 화폐, 아래로 그림이 있는 카드 —
 * 갤러리 칸 열기 · 액자 바꾸기 · 내일 편지 미리 열기 · 쉼표 · 카드 테두리. 설명 글은 두지 않는다.
 * 맨 아래 ‘구독 · 시리즈’ → 작품 사기 · 구독 화면 ([Route.Purchase]).
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
            // 가진 화폐
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${s.wallet(id).balance}", style = Type.display.copy(fontFamily = Faces.display), color = p.giltText)
                Text(cur, style = Type.body.ui(), color = p.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
            }
            val items = listOf(
                ShopItem(Spend.PICK_PIECE, stringResource(R.string.spend_pick), stringResource(R.string.spend_pick_d)) {
                    if (previewPick != null) AssetImage(previewPick.image, Modifier.fillMaxSize(), sample = 4) else ShopGlyph(Spend.PICK_PIECE)
                },
                ShopItem(Spend.FRAME, stringResource(R.string.spend_frame), stringResource(R.string.spend_frame_d)) {
                    val framed = previewFrame?.let { rememberFramed(it, Frames.Kind.MASTER) }
                    Box(Modifier.fillMaxSize().background(Color(0xFF1E1811)).padding(8.dp), contentAlignment = Alignment.Center) {
                        if (framed != null) Image(framed, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else ShopGlyph(Spend.FRAME)
                    }
                },
                ShopItem(Spend.EARLY_LETTER, stringResource(R.string.spend_early), null) { ShopGlyph(Spend.EARLY_LETTER) },
                ShopItem(Spend.REST, stringResource(R.string.spend_rest), stringResource(R.string.spend_rest_d, s.rests, Streak.MAX_RESTS)) { ShopGlyph(Spend.REST) },
                ShopItem(Spend.BORDER, stringResource(R.string.spend_border), stringResource(R.string.spend_border_d)) {
                    BorderSwatch(Border.GILT, Modifier.size(width = 84.dp, height = 62.dp))
                },
            )
            items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    row.forEach { item ->
                        val can = s.canBuy(item.sp, id)
                        ShopCard(item.title, item.sub, "${item.sp.cost} $cur", can, Modifier.weight(1f).fillMaxHeight(), onClick = {
                            when (item.sp) {
                                Spend.PICK_PIECE -> picking = true
                                Spend.FRAME -> framingPick = true
                                Spend.BORDER -> borders = true
                                else -> asked = item.sp
                            }
                        }, art = item.art)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // 구독 · 시리즈 (작품 사기 · 구독)
            Row(
                Modifier.padding(top = Tokens.Space.s3).fillMaxWidth().heightIn(min = 60.dp).border(1.5.dp, p.ink).pressable { s.go(Route.Purchase) }
                    .padding(horizontal = Tokens.Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.shop_plans), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink, modifier = Modifier.weight(1f))
                Text("›", style = Type.heading, color = p.ink)
            }
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

/** 상점 카드: 위에 그림 (미리 보기), 아래에 이름 · 짧은 한 줄 · 값 단추. 살 수 없으면 값은 테두리만. */
@Composable
private fun ShopCard(title: String, sub: String?, price: String, can: Boolean, modifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit) {
    val p = Ink.palette
    Column(modifier.background(p.leaf).border(Tokens.Stroke.hair, p.line).pressable(enabled = can, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.45f).background(p.hide), contentAlignment = Alignment.Center) { art() }
        Column(Modifier.weight(1f).fillMaxWidth().padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
            if (sub != null) Text(sub, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 40.dp).then(if (can) Modifier.background(p.ink) else Modifier.border(1.dp, p.hair)),
                contentAlignment = Alignment.Center,
            ) { Text(price, style = Type.small.ui().copy(fontWeight = FontWeight.SemiBold), color = if (can) p.paper else p.hideInk) }
        }
    }
}

/** 그림이 없는 상점 카드의 작은 그림: 편지 봉투와 봉랍 (미리 열기) · 이번 주 점 하나가 빈 고리 (쉼표). */
@Composable
private fun ShopGlyph(sp: Spend) {
    val p = Ink.palette
    val wax = Color(0xFF9A3027)
    when (sp) {
        Spend.REST -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(7) { i ->
                Box(Modifier.size(12.dp).then(if (i == 4) Modifier.border(1.5.dp, p.giltText, CircleShape) else Modifier.background(if (i < 4) p.giltText else p.hair, CircleShape)))
            }
        }
        else -> Canvas(Modifier.size(width = 96.dp, height = 64.dp)) {
            val st = Stroke(1.4.dp.toPx())
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
 * 상점 › 구독 · 시리즈: 구독 (모든 작품) 과 작품마다 사기 (1장은 무료). 결제는 [Billing] 한 곳에서 —
 * 아직 열리지 않아서 단추는 ‘곧 열려요’.
 */
@Composable
fun PurchaseScreen(s: AppState) {
    val p = Ink.palette
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.shop_plans), s, help = null, showBack = true, showSettings = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(stringResource(R.string.buy_sub_h), p.giltText, small = true)
            Column(Modifier.fillMaxWidth().background(p.leaf).border(1.5.dp, p.giltText).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(stringResource(R.string.buy_sub_t), style = Type.heading.ui(), color = p.ink)
                Text(stringResource(R.string.buy_sub_d), style = Type.small.ui(), color = p.inkSoft)
                BuyButton(stringResource(R.string.buy_sub_h), Billing.Product.Subscription)
            }
            Caps(stringResource(R.string.buy_series_h), p.giltText, small = true, modifier = Modifier.padding(top = Tokens.Space.s3))
            s.works.forEach { w -> SeriesOffer(s, w) }
            Spacer(Modifier.height(Tokens.Space.s5))
        }
    }
}

@Composable
private fun SeriesOffer(s: AppState, w: Work) {
    val p = Ink.palette
    Column(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Portrait(w.portrait, w.fullName, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${w.name[uiLang()]} → ${w.recipient[uiLang()]}", style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
                Text(stringResource(R.string.lib_letters, s.doneCount(w.series.id), w.chapters.sumOf { it.letters.size }), style = Type.small.ui(), color = p.inkSoft)
            }
            Capsule(stringResource(R.string.buy_free_ch), CapsuleKind.FILLED)
        }
        Text(stringResource(R.string.buy_series_full), style = Type.small.ui(), color = p.ink)
        BuyButton(stringResource(R.string.buy_series_full), Billing.Product.Series(w.series.id))
    }
}

/** 사기 단추: 결제가 열리면 채운 단추, 그 전에는 테두리와 ‘곧 열려요’. */
@Composable
private fun BuyButton(label: String, product: Billing.Product) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val ready = Billing.ready
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (ready) Modifier.background(p.ink) else Modifier.border(1.dp, p.line))
            .pressable(enabled = ready) { Billing.buy(ctx, product) },
        contentAlignment = Alignment.Center,
    ) { Text(if (ready) label else stringResource(R.string.buy_soon), style = Type.body.ui(), color = if (ready) p.paper else p.inkSoft) }
}
