package io.github.graviton94.todayletters.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 앱을 켰을 때.
 *  봉투(그날 첫 실행): 봉투 · 소인 → 봉랍을 눌러 열기 → 첫 줄이 타이핑으로 → 눌러서 들어가기
 *  짧게(같은 날 다시): 제목이 한 글자씩 → 눌러서 들어가기
 * 불러오기는 그 사이에 끝난다 (따로 로딩 화면 없음). 어디를 눌러도 건너뛸 수 있다.
 */
@Composable
fun Intro(s: AppState, envelope: Boolean) {
    val p = Ink.palette
    val haptic = LocalHapticFeedback.current
    var step by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(Tokens.Motion.loadingMinMs.toLong()); ready = true }

    val work = s.works.firstOrNull()
    val next = work?.let { w -> s.openable(w.series.id).firstOrNull { (c, l) -> !s.progress(w.series.id, c, l.id).done } }
    val learn = work?.let { s.seriesSettings(it.series.id).learn }
    val firstLine = next?.second?.messages?.firstOrNull()?.text?.let { t -> learn?.let { t[it] } }.orEmpty()
    val waiting = work?.let { s.waiting(it.series.id) } ?: 0

    fun advance() {
        if (s.app.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (!envelope || step >= 2) { if (ready) s.nextStage() } else step++
    }

    Box(
        Modifier.fillMaxSize().background(p.paper).statusBarsPadding()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { advance() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s7),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s6),
        ) {
            Caps("Lettres de Vincent")
            if (envelope) Envelope(step, next?.second?.date.orEmpty(), waiting, firstLine)
            else TitleLetters(stringResource(R.string.app_name))
            val breathe = rememberInfiniteTransition(label = "breathe")
            val a by breathe.animateFloat(0.35f, 1f, infiniteRepeatable(tween(Tokens.Motion.tapPulseMs / 2), RepeatMode.Reverse), label = "a")
            AnimatedVisibility(ready, enter = fadeIn(tween(Tokens.Motion.introFadeMs))) {
                Text(
                    stringResource(if (envelope && step == 0) R.string.intro_seal else R.string.intro_tap),
                    style = Type.label.ui(), color = p.inkSoft, modifier = Modifier.alpha(if (s.reducedMotion) 1f else a),
                )
            }
        }
    }
}

@Composable
private fun Envelope(step: Int, letterDate: String, waiting: Int, firstLine: String) {
    val p = Ink.palette
    val today = LocalDate.now()
    val day = today.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)).uppercase()
    Box(
        Modifier.fillMaxWidth().height(220.dp).rotate(-1.5f).background(p.leaf).border(Tokens.Stroke.hair, p.hair),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.TopEnd).padding(Tokens.Space.s4)) { Postmark("Arles", day, letterDate.take(4).ifEmpty { "1888" }) }
        Column(Modifier.align(Alignment.CenterStart).padding(start = Tokens.Space.s6), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text("à Théo", style = Type.title.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = p.ink)
            if (waiting > 0) Text(stringResource(R.string.intro_new, waiting), style = Type.small.ui(), color = p.inkSoft)
        }
        if (step == 0) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Tokens.Space.s4)) {
            SealMark(io.github.graviton94.todayletters.design.Tokens.Seals.vincent, "V", Tokens.Size.avatarLg)
        }
    }
    if (step >= 1 && firstLine.isNotEmpty()) {
        var n by remember { mutableIntStateOf(0) }
        LaunchedEffect(firstLine) { while (n < firstLine.length) { delay(Tokens.Motion.typeCharMs.toLong()); n += Tokens.Motion.typeChunk } }
        Text(
            firstLine.take(n.coerceAtMost(firstLine.length)),
            style = Type.target, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TitleLetters(title: String) {
    val p = Ink.palette
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(title) { while (n < title.length) { delay(120); n++ } }
    Text(title.take(n), style = Type.display.ui(), color = p.ink, textAlign = TextAlign.Center)
}
