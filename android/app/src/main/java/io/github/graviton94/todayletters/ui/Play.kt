package io.github.graviton94.todayletters.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Exercises
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.core.Route
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 답장 (플레이어블). 뒤로 가기는 바로 나가지 않고 한 번 묻는다. */
@Composable
fun Play(s: AppState, r: Route.Play) {
    var asking by remember { mutableStateOf(false) }
    BackHandler { asking = true }
    when (r.mode) {
        ReplyMode.ALOUD -> Aloud(s, r)
        else -> Constellation(s, r)
    }
    if (asking) Ask(
        stringResource(R.string.play_leave_title), stringResource(R.string.play_leave_yes), stringResource(R.string.play_leave_no),
        onYes = { asking = false; s.recorder.stop(); s.back() }, onNo = { asking = false },
    )
}

@Composable
private fun PlayHeader(s: AppState, label: String, step: String) {
    val p = Ink.palette
    Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2, vertical = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(stringResource(R.string.back), onClick = { s.back() }) { Chevron(p.ink) }
        Caps(label, p.giltText, modifier = Modifier.weight(1f))
        Caps(step)
        IconButton(stringResource(R.string.help), onClick = { helpId(s.route)?.let { s.coachAgain(it) } }) { Text("?", style = Type.label, color = p.ink) }
    }
    Rule()
}

/** 별자리 잇기: 낱말을 차례로 누르면 별처럼 이어진다. 라이트 · 다크 모두 같은 모양, 색만 다르다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Constellation(s: AppState, r: Route.Play) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    val (_, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val (sentence, tri) = Exercises.replyFor(letter, view.learn)
    val (answer, pieces) = remember(letter.id, view.learn) { Exercises.constellation(sentence, letter.id.hashCode()) }
    var picked by remember(letter.id, view.learn) { mutableStateOf(listOf<Int>()) }
    var result by remember { mutableStateOf<Boolean?>(null) }

    Column(Modifier.fillMaxSize()) {
        PlayHeader(s, "Réponse · ${stringResource(R.string.mode_constellation)}", "I / II")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text("“${tri[view.read]}”", style = Type.title.of(view.read), color = p.ink)
            // 하늘: 고른 낱말이 별로 이어진다
            Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).background(p.sky).border(Tokens.Stroke.hair, p.hair).padding(Tokens.Space.s4)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    picked.forEachIndexed { k, idx ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            if (k > 0) Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
                                drawLine(p.gilt, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                            }
                            Box(Modifier.size(Tokens.Size.star).background(p.gilt, CircleShape))
                            Text(
                                pieces[idx], style = Type.chipWord.of(view.learn), color = p.ink,
                                modifier = Modifier.clickable(enabled = result != true, role = Role.Button) { picked = picked.filterIndexed { j, _ -> j != k }; result = null },
                            )
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                pieces.forEachIndexed { idx, w ->
                    val used = idx in picked
                    Box(
                        Modifier.heightIn(min = Tokens.Size.chip).border(Tokens.Stroke.hair, if (used) p.hair else p.ink)
                            .clickable(enabled = !used && result != true, role = Role.Button) {
                                if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                picked = picked + idx; result = null
                            }.padding(horizontal = Tokens.Space.s4),
                        contentAlignment = Alignment.Center,
                    ) { Text(w, style = Type.chipWord.of(view.learn), color = if (used) p.hair else p.ink) }
                }
            }
            when (result) {
                true -> Text(stringResource(R.string.play_right), style = Type.label.ui(), color = p.correct)
                false -> Text(stringResource(R.string.play_wrong, answer.joinToString(" ")), style = Type.label.of(view.learn), color = p.wrong)
                null -> {}
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4)) {
            if (result == true) Primary(stringResource(R.string.play_send)) { s.replied(r.room, ReplyMode.CONSTELLATION); s.back() }
            else Primary(stringResource(R.string.play_check), enabled = picked.size == pieces.size) {
                result = Exercises.isRight(answer, picked.map { pieces[it] })
                if (result == false) picked = emptyList()
            }
        }
    }
}

/** 따라 읽기: 원어 듣기 → 녹음 → 보내기. MVP 는 녹음한 것만으로 완료 (채점 없음). 녹음은 기기 안에만. */
@Composable
private fun Aloud(s: AppState, r: Route.Play) {
    val p = Ink.palette
    val ctx = LocalContext.current
    val (chapter, letter) = s.letterOf(r.room)
    val view = s.room(r.room.series)
    val i = letter.messages.lastIndex
    val line = letter.messages[i].text
    var recording by remember { mutableStateOf(false) }
    var has by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    DisposableEffect(Unit) { onDispose { s.recorder.stop() } }

    Column(Modifier.fillMaxSize()) {
        PlayHeader(s, "Réponse · ${stringResource(R.string.mode_aloud)}", "II / II")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(stringResource(R.string.aloud_prompt), style = Type.body.ui(), color = p.inkSoft)
            Incoming { Pair2(line[view.learn], view.learn, line[view.read], view.read) }
            Secondary(stringResource(R.string.aloud_listen)) { s.narrator.play(s.narrator.path(r.room.series, chapter, letter.id, "m${i + 1}_${view.learn.code}")) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(Tokens.Size.mic).background(if (recording) p.wrong else p.paper, CircleShape).border(Tokens.Stroke.hair, p.giltText, CircleShape)
                        .clickable(role = Role.Button) {
                            if (!allowed) { ask.launch(Manifest.permission.RECORD_AUDIO); return@clickable }
                            if (recording) { s.recorder.stop(); recording = false; has = true } else { s.narrator.stop(); recording = s.recorder.start() }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(if (recording) "■" else "●", style = Type.heading, color = if (recording) p.onFill else p.giltText) }
            }
            Text(
                stringResource(if (recording) R.string.aloud_recording else if (has) R.string.aloud_recorded else R.string.aloud_tap),
                style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (has) Secondary(stringResource(R.string.aloud_mine)) { s.recorder.play() }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s4)) {
            Primary(stringResource(R.string.play_send), enabled = has && !recording) { s.replied(r.room, ReplyMode.ALOUD); s.back() }
        }
    }
}

/** 화면 안에서 묻기 (시스템 대화상자 대신). */
@Composable
fun Ask(title: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
    val p = Ink.palette
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onNo), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(Tokens.Space.s6).background(p.paper).border(Tokens.Stroke.hair, p.ink).clickable(enabled = false) {}.padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Text(title, style = Type.heading.ui(), color = p.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.weight(1f)) { Secondary(yes, onClick = onYes) }
                Box(Modifier.weight(1f)) { Primary(no, onClick = onNo) }
            }
        }
    }
}
