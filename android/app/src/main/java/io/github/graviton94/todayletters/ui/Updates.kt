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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 서재에서만: ① 업데이트 뒤 첫 실행이면 ‘새로 바뀐 것’ 한 장 ② 새 버전을 다 받아 두었으면 아래 띠 ‘다시 열기’
 * ③ (둘 다 아니면) 오늘 아직 안 물었을 때 Play 의 새 버전 확인 창 (data/Updates).
 */
@Composable
fun UpdateLayer(s: AppState) {
    if (s.route != Route.Library) return
    val busy = s.crashAsk || s.reward != null || s.levelUp != null || s.gradeUp != null || s.settingsOpen
    LaunchedEffect(s.route, s.whatsNew, busy) { if (!s.whatsNew && !busy) s.askUpdate() }
    if (s.updateReady && !s.whatsNew) Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        val p = Ink.palette
        Row(
            Modifier.padding(Tokens.Space.s3).fillMaxWidth().background(p.fill)
                .clickable { s.restartForUpdate() }.heightIn(min = 52.dp).padding(horizontal = Tokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.update_ready), style = Type.small.ui(), color = p.onFill, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.update_restart), style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.onFill)
        }
    }
    if (s.whatsNew && !busy) WhatsNew(s)
}

@Composable
private fun WhatsNew(s: AppState) {
    val p = Ink.palette
    BackHandler { s.closeWhatsNew() }
    val ver = remember(s) { io.github.graviton94.todayletters.data.Updates.versionName(s.ctx).removeSuffix("-dev") }
    Box(Modifier.fillMaxSize().background(p.scrim).clickable { s.closeWhatsNew() }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.86f).background(p.paper).border(Tokens.Stroke.hair, p.line).clickable(enabled = false) {}
                .padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Caps(stringResource(R.string.whatsnew_cap, ver), p.giltText, small = true)
            Text(stringResource(R.string.whatsnew_title), style = Type.heading.ui(), color = p.ink)
            stringArrayResource(R.array.whatsnew_items).forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text("·", style = Type.body.ui(), color = p.giltText)
                    Text(it, style = Type.body.ui(), color = p.ink)
                }
            }
            Box(Modifier.padding(top = Tokens.Space.s2)) { Primary(stringResource(R.string.whatsnew_ok), small = true) { s.closeWhatsNew() } }
        }
    }
}
