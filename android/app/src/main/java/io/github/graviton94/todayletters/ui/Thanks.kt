package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 고마워요 한 장 (결제가 끝난 뒤 한 번, [AppState.thanks]): 오늘의 편지 마침 한 장과 같은 꼴 (짙은 막 위 가운데 카드 · 밀랍 도장 · 금테).
 * 구독이면 “이제 모든 작품의 모든 편지가 열렸어요”, 작품 소장이면 “○○의 편지는 이제 평생 당신 것이에요”. ‘읽으러 가기’ → [AppState.thanksGo].
 */
@Composable
fun ThanksSheet(s: AppState) {
    val t = s.thanks ?: return
    val p = Ink.palette
    val w = s.work(t.series?.takeIf { id -> s.works.any { it.series.id == id } } ?: s.current)
    val close = { s.thanks = null }
    androidx.activity.compose.BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).pressable(haptic = false, onClick = close), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.92f).background(p.paper).border(1.dp, p.giltText).pressable(haptic = false) { }
                .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Box(
                Modifier.size(64.dp).graphicsLayer { rotationZ = -8f }.background(Brush.radialGradient(listOf(Color(0xFFC25546), Color(0xFF9A3027), Color(0xFF6E1C15))), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Portrait(w.portrait, w.fullName, 40.dp) }
            Text(stringResource(R.string.thanks_title), style = Type.title.ui(), color = p.ink, textAlign = TextAlign.Center)
            Text(if (t.series == null) stringResource(R.string.thanks_sub) else stringResource(R.string.thanks_own, w.name[uiLang()]),
                // 한 줄에 (작품 이름이 길어도): 조금 작은 글자
                style = Type.body.ui().copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = Tokens.Space.s2))
            Primary(stringResource(R.string.thanks_go), shine = true) { s.thanksGo() }
        }
    }
}
