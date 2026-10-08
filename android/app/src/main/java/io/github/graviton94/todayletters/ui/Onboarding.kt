package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.core.ReplyMode
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 언어 이름: 그 언어 자신의 글자로 (Français, English, 한국어). */
fun langName(l: Lang) = when (l) {
    Lang.KO -> "한국어"; Lang.EN -> "English"; Lang.FR -> "Français"; Lang.NL -> "Nederlands"; Lang.DE -> "Deutsch"; Lang.JA -> "日本語"
}

/**
 * 처음 소개 (첫 설치 한 번): 읽는 언어 → 첫 작품 → 배우는 언어 → 답장 방식 · 하루 편지 수.
 * 알림 묻기는 첫 편지를 받은 뒤로 미룬다 (이유를 먼저 보여 주고 한 번만).
 */
@Composable
fun Onboarding(s: AppState) {
    val p = Ink.palette
    val work = s.works.first()
    var step by remember { mutableIntStateOf(0) }
    var read by remember { mutableStateOf(s.app.read) }
    var learn by remember { mutableStateOf(Langs.defaultLearn(work.series, s.app.read)) }
    var modes by remember { mutableStateOf(setOf(ReplyMode.MATCH, ReplyMode.CONSTELLATION, ReplyMode.ALOUD)) }
    var perDay by remember { mutableIntStateOf(1) }
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caps("Bienvenue", modifier = Modifier.weight(1f))
            Caps(listOf("I", "II", "III", "IV")[step] + " / IV")
        }
        Box(Modifier.padding(top = Tokens.Space.s2)) { Rule() }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn(tween(Tokens.Motion.fadeMs)) togetherWith fadeOut(tween(Tokens.Motion.fadeMs)) }, label = "ob") { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Tokens.Space.s6), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                when (st) {
                    0 -> {
                        Text(stringResource(R.string.ob_read), style = Type.title.ui(), color = p.ink)
                        Text(stringResource(R.string.ob_read_note), style = Type.body.ui(), color = p.inkSoft)
                        Choices(listOf(Lang.KO, Lang.EN, work.series.original).distinct().map { it to langName(it) }, read) {
                            read = it
                            if (learn == it) learn = Langs.defaultLearn(work.series, it)
                        }
                    }
                    1 -> {
                        Text(stringResource(R.string.ob_series), style = Type.title.ui(), color = p.ink)
                        Row(
                            Modifier.fillMaxWidth().border(Tokens.Stroke.rule, p.ink).padding(Tokens.Space.s4),
                            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SealMark(io.github.graviton94.todayletters.design.Tokens.Seals.vincent, "V")
                            Column(Modifier.weight(1f)) {
                                Text(work.title[read], style = Type.heading.of(read), color = p.ink)
                                Text("${work.sender} → Théo · ${work.years}", style = Type.small, color = p.inkSoft)
                            }
                        }
                        PlateImage("", Modifier.fillMaxWidth().heightIn(min = Tokens.Size.plate))
                    }
                    2 -> {
                        Text(stringResource(R.string.ob_learn), style = Type.title.ui(), color = p.ink)
                        Text(stringResource(R.string.ob_learn_note), style = Type.body.ui(), color = p.inkSoft)
                        Choices(Langs.learnChoices(work.series, read).map { it to langName(it) }, learn) { learn = it }
                        val m = work.chapters.first().letters.first().messages.getOrNull(1)?.text
                        if (m != null) Incoming { Pair2(m[learn], learn, m[read], read) }
                    }
                    else -> {
                        Text(stringResource(R.string.ob_modes), style = Type.title.ui(), color = p.ink)
                        listOf(
                            ReplyMode.MATCH to R.string.mode_match, ReplyMode.CONSTELLATION to R.string.mode_constellation,
                            ReplyMode.ALOUD to R.string.mode_aloud,
                        ).forEachIndexed { i, (mode, label) ->
                            val on = mode in modes
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row)
                                    .clickable(role = Role.Checkbox) { modes = if (on) modes - mode else modes + mode },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
                            ) {
                                Text(listOf("I", "II", "III")[i], style = Type.numeral, color = p.giltText)
                                Text(stringResource(label), style = Type.body.ui(), color = p.ink, modifier = Modifier.weight(1f))
                                Box(Modifier.size(24.dp).background(if (on) p.fill else p.paper).border(Tokens.Stroke.hair, p.ink))
                            }
                            Hair()
                        }
                        Text(stringResource(R.string.ob_pace), style = Type.label.ui(), color = p.giltText)
                        Choices(listOf(1, 2, 3).map { it to stringResource(R.string.letters_n, it) }, perDay) { perDay = it }
                    }
                }
            }
        }
        Primary(stringResource(R.string.ob_continue), enabled = step != 3 || modes.isNotEmpty()) {
            if (step < 3) step++
            else {
                val a = s.app.copy(read = read)
                s.finishOnboarding(a, work.series.id, Langs.start(work.series, a).copy(learn = learn, modes = modes, lettersPerDay = perDay))
            }
        }
    }
}

