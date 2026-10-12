package io.github.graviton94.todayletters.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
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
    "inbox" to listOf(R.string.coach_inbox_row, R.string.coach_inbox_learn, R.string.coach_inbox_settings),
    "library" to listOf(R.string.coach_library, R.string.coach_library_learn),
    "words" to listOf(R.string.coach_words),
    "gallery" to listOf(R.string.coach_gallery),
    "milestones" to listOf(R.string.coach_milestones),
    "series" to listOf(R.string.coach_series),
    "room" to listOf(R.string.coach_room_bubble, R.string.coach_room_hide, R.string.coach_room_info),
    "play_aloud" to listOf(R.string.coach_play_aloud),
)

/**
 * 도움말 층: (?) 를 누르면 아래에서 올라오는 카드. 건너뛰기 · n / N · 다음. 저절로는 뜨지 않는다
 * (처음 설치의 튜토리얼이 편지 I 의 걸음마다 한 줄씩 안내하고, 그 뒤로는 (?) 로만, core 의 Coach · Tutorial).
 */
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
        // 바탕은 터치만 막고 (단추가 아님), 넘기기는 카드에서만
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Tokens.Space.s5).background(p.paper).border(Tokens.Stroke.hair, p.gilt)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (i < list.lastIndex) i++ else s.coachDone(screen)
                }.padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Caps(stringResource(R.string.help), p.giltText, small = true)
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

/**
 * 튜토리얼의 안내 한 줄 (편지 I, core 의 Tutorial): 그 걸음에 처음 들어왔을 때만. 화면에 머무는 동안은 그대로 보이고,
 * 다음에 들어오면 없다. 튜토리얼을 마친 뒤에는 어느 작품에서도 나오지 않는다.
 */
@Composable
fun GuideLine(s: AppState, step: String, modifier: Modifier = Modifier) {
    val show = remember(step) { s.guideDue(step) }
    if (!show) return
    LaunchedEffect(step) { s.guideShown(step) }
    val p = Ink.palette
    Row(
        modifier.fillMaxWidth().background(p.leaf).border(1.dp, p.giltText).padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        Box(Modifier.size(8.dp).background(p.giltText, androidx.compose.foundation.shape.CircleShape))
        Text(stringResource(guideText(step)), style = Type.small.ui(), color = p.ink)
    }
}

private fun guideText(step: String) = when (step) {
    "read" -> R.string.guide_read
    "match" -> R.string.guide_match
    "aloud" -> R.string.guide_aloud
    "reply" -> R.string.guide_reply
    else -> R.string.guide_walk
}
