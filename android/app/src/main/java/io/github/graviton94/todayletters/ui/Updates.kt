package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.core.UpdateBand
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 서재 · 오늘에서만: ① 업데이트 뒤 첫 실행이면 ‘새로 바뀐 것’ 한 장 ② 서재 아래 업데이트 띠 (시안 update_backup.html, core UpdateBand):
 * 새 버전 알림 (나중에 · 받기) → 받는 중 % → 다 받음 (나중에 · 다시 열기). Play 의 창은 ‘받기’ 를 누를 때만 (data/Updates).
 */
@Composable
fun UpdateLayer(s: AppState) {
    // 앱을 켜면 오늘부터일 수도 있어 (읽던 작품) ‘새로 바뀐 것’ 은 서재와 오늘 둘 다에서
    if (s.route != Route.Library && s.route != Route.Inbox) return
    val busy = s.crashAsk || s.reward != null || s.levelUp != null || s.gradeUp != null || s.settingsOpen
    // 띠는 서재에서만 (편지 · 복습 중에는 띄우지 않는다)
    LaunchedEffect(s.route, s.whatsNew, busy) { if (s.route == Route.Library && !s.whatsNew && !busy) s.askUpdate() }
    if (s.route == Route.Library && !s.whatsNew && !busy && s.updateBand != UpdateBand.Hidden)
        Box(Modifier.fillMaxSize().navigationBarsSafe(), contentAlignment = Alignment.BottomCenter) { UpdateBandView(s, s.updateBand) }
    if (s.whatsNew && !busy) WhatsNew(s)
}

@Composable
private fun UpdateBandView(s: AppState, band: UpdateBand) {
    val p = Ink.palette
    val small = Type.small.ui()
    val pct = Type.body.ui().copy(fontFeatureSettings = "tnum", color = p.giltText)
    Column(
        Modifier.padding(Tokens.Space.s3).fillMaxWidth().background(p.paper).border(Tokens.Stroke.hair, p.line)
            .pressable(haptic = false) { }.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        @Composable fun Head(title: String, right: String?, rightStyle: androidx.compose.ui.text.TextStyle) =
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Type.body.ui(), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (right != null) Text(right, style = rightStyle, maxLines = 1, modifier = Modifier.padding(start = Tokens.Space.s2))
            }
        @Composable fun Bar(frac: Float) = Box(Modifier.fillMaxWidth().height(4.dp).background(p.hair)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).background(p.giltText))
        }
        @Composable fun Note(text: String) = Text(text, style = small, color = p.inkSoft, maxLines = 2)
        @Composable fun Buttons(yes: String, onYes: () -> Unit) =
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Secondary(stringResource(R.string.update_later), Modifier.weight(1f), small = true) { s.laterUpdate() }
                Primary(yes, Modifier.weight(1f), small = true, shine = true, onClick = onYes)
            }
        when (band) {
            is UpdateBand.Offer -> {
                Head(stringResource(R.string.update_offer_t), band.bytes.takeIf { it > 0 }?.let { stringResource(R.string.update_size, sizeLabel(it)) }, small.copy(color = p.inkSoft))
                Note(stringResource(R.string.update_offer_b))
                Buttons(stringResource(R.string.update_get)) { s.startUpdate() }
            }
            is UpdateBand.Fetching -> {
                Head(stringResource(R.string.update_fetching_t), "${band.percent}%", pct)
                Bar(band.percent / 100f)
                Note(stringResource(R.string.update_fetching_b))
            }
            UpdateBand.Failed -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.update_failed), style = small, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.heightIn(min = 48.dp).pressable { s.startUpdate() }.padding(start = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.update_retry), style = small.copy(fontWeight = FontWeight.SemiBold), color = p.giltText, maxLines = 1)
                }
            }
            UpdateBand.Ready -> {
                Head(stringResource(R.string.update_ready), "100%", pct)
                Bar(1f)
                Note(stringResource(R.string.update_ready_b))
                Buttons(stringResource(R.string.update_restart)) { s.restartForUpdate() }
            }
            UpdateBand.Hidden -> {}
        }
    }
}

@Composable
private fun WhatsNew(s: AppState) {
    val p = Ink.palette
    BackHandler { s.closeWhatsNew() }
    val ver = remember(s) { io.github.graviton94.todayletters.data.Updates.versionName(s.ctx).removeSuffix("-dev") }
    Box(Modifier.fillMaxSize().background(p.scrim).clickable { s.closeWhatsNew() }.systemBarsSafe(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.86f).background(p.paper).border(Tokens.Stroke.hair, p.line).clickable(enabled = false) {}
                .padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Caps(stringResource(R.string.whatsnew_cap, ver), p.giltText, small = true)
            Text(stringResource(R.string.whatsnew_title), style = Type.heading.ui(), color = p.ink)
            stringResource(R.string.whatsnew_items_100).split('\n').filter { it.isNotBlank() }.forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text("·", style = Type.body.ui(), color = p.giltText)
                    Text(it, style = Type.body.ui(), color = p.ink)
                }
            }
            Box(Modifier.padding(top = Tokens.Space.s2)) { Primary(underLabel(s).first, small = true) { s.closeWhatsNew() } }
        }
    }
}
