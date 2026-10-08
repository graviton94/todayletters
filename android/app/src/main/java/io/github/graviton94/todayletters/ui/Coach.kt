package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay

/** 화면마다의 도움말 묶음 (strings 의 coach_*). 처음 한 번 저절로, (?) 로 다시. */
private val steps: Map<String, List<Int>> = mapOf(
    "inbox" to listOf(R.string.coach_inbox_row, R.string.coach_inbox_settings),
    "library" to listOf(R.string.coach_library),
    "series" to listOf(R.string.coach_series),
    "room" to listOf(R.string.coach_room_bubble, R.string.coach_room_hide, R.string.coach_room_info),
    "play_constellation" to listOf(R.string.coach_play_stars),
    "play_aloud" to listOf(R.string.coach_play_aloud),
)

/** 도움말 층: 화면이 조용할 때(설정이 닫혀 있을 때) 아래에서 올라오는 카드. 건너뛰기 · n / N · 다음. */
@Composable
fun CoachLayer(s: AppState) {
    val p = Ink.palette
    val screen = helpId(s.route) ?: return
    s.coachTick
    val list = steps[screen] ?: return
    if (!s.coach.due(screen, calm = !s.settingsOpen)) return
    var ready by remember(screen) { mutableStateOf(false) }
    var i by remember(screen, s.coachTick) { mutableIntStateOf(0) }
    LaunchedEffect(screen, s.coachTick) { ready = false; delay(Tokens.Motion.coachDelayMs.toLong()); ready = true }
    if (!ready) return
    val alpha = Tokens.Alpha.coachScrim * 0.5f
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (i < list.lastIndex) i++ else s.coachDone(screen)
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Tokens.Space.s5).background(p.paper).border(Tokens.Stroke.hair, p.gilt).padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Caps("Guide", p.giltText, small = true)
            Text(stringResource(list[i]), style = Type.body.ui(), color = p.ink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.coach_skip), style = Type.label.ui(), color = p.inkSoft,
                    modifier = Modifier.heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) { s.coachDone(screen) }.padding(top = Tokens.Space.s3),
                )
                Text("${i + 1} / ${list.size}", style = Type.small, color = p.inkSoft, modifier = Modifier.weight(1f).padding(horizontal = Tokens.Space.s4))
                Text(
                    stringResource(if (i < list.lastIndex) R.string.coach_next else R.string.coach_ok), style = Type.label.ui(), color = p.giltText,
                    modifier = Modifier.heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) {
                        if (i < list.lastIndex) i++ else s.coachDone(screen)
                    }.padding(top = Tokens.Space.s3),
                )
            }
        }
    }
}
