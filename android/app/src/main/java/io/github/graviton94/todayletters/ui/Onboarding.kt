package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 언어 이름: 그 언어 자신의 글자로 (Français, English, 한국어). 언어 고르기 단추에 쓴다. */
fun langName(l: Lang) = when (l) {
    Lang.KO -> "한국어"; Lang.EN -> "English"; Lang.FR -> "Français"; Lang.NL -> "Nederlands"; Lang.DE -> "Deutsch"; Lang.JA -> "日本語"
}

/** 언어 이름: 앱 글자 언어로 (프랑스어 / French). 설명 문장 안에 쓴다. */
fun langLabel(l: Lang) = when (l) {
    Lang.KO -> R.string.lang_ko; Lang.EN -> R.string.lang_en; Lang.FR -> R.string.lang_fr; Lang.NL -> R.string.lang_nl; Lang.DE -> R.string.lang_de; Lang.JA -> R.string.lang_ja
}

/** 아직 열리지 않은 작품 (오프닝 봉투 · 처음 소개의 "곧 도착"). */
private data class Soon(val portrait: String, val title: Int, val lang: Lang)
private val soon = listOf(
    Soon("mozart.jpg", R.string.soon_mozart, Lang.DE),
    Soon("napoleon.jpg", R.string.soon_napoleon, Lang.FR),
    Soon("basho.jpg", R.string.soon_basho, Lang.JA),
)

/**
 * 처음 소개 (첫 설치 한 번), 세 단계:
 *  0. 반가워요: 이 앱이 무엇인지, 하루가 어떻게 흘러가는지 (읽기 → 낱말 → 따라 읽기 → 답장 → 그림)
 *  1. 이렇게 도착해요: 실제 첫 문장 한 조각으로 원문 · 번역 · 듣기를 보여 준다 (묻는 것 없음)
 *  2. 작품과 배울 언어: 작품을 고르면 그 자리에서 배울 언어. 번역 언어는 시스템 언어로 시작 (설정에서 변경)
 * 답장 방식(모두 켜짐) · 하루 편지 수(1통)는 묻지 않고, 작품 설정에서 바꾼다.
 */
@Composable
fun Onboarding(s: AppState, startStep: Int = 0) {
    val p = Ink.palette
    val work = s.works.first()
    var step by remember { mutableIntStateOf(startStep) }
    var learn by remember { mutableStateOf(Langs.defaultLearn(work.series, s.app.read)) }
    BackHandler(enabled = step > 0) { step-- }
    DisposableEffect(Unit) { onDispose { s.narrator.stop() } }

    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f).statusBarsPadding().navigationBarsPadding().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4)) {
        Row(Modifier.heightIn(min = Tokens.Size.touch), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            if (step > 0) IconButton(stringResource(R.string.back), onClick = { step-- }) { Chevron(p.ink) }
            repeat(3) { i -> Box(Modifier.width(28.dp).height(2.dp).background(if (i <= step) p.giltText else p.hair)) }
            Spacer(Modifier.weight(1f))
            Caps("${step + 1} / 3")
        }
        AnimatedContent(
            step, Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(Tokens.Motion.pageMs)) + slideInHorizontally(tween(Tokens.Motion.pageMs)) { it / 8 * dir }) togetherWith
                    (fadeOut(tween(Tokens.Motion.fadeMs)) + slideOutHorizontally(tween(Tokens.Motion.pageMs)) { -it / 8 * dir })
            },
            label = "ob",
        ) { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Tokens.Space.s4, bottom = Tokens.Space.s4)) {
                when (st) {
                    0 -> Hello(s, work)
                    1 -> Arrive(s, work)
                    else -> Pick(s, work, learn) { learn = it }
                }
            }
        }
        Primary(stringResource(when (step) { 0 -> R.string.ob_start_hello; 1 -> R.string.ob_next; else -> R.string.ob2_start })) {
            if (step < 2) step++
            else {
                s.narrator.stop()
                s.finishOnboarding(s.app, work.series.id, Langs.start(work.series, s.app).copy(learn = learn))
            }
        }
    }
}

/** 0단계: 반가워요. 처음 쓰는 사람에게 앱이 무엇이고 하루가 어떻게 흘러가는지. */
@Composable
private fun Hello(s: AppState, work: io.github.graviton94.todayletters.data.Work) {
    val p = Ink.palette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Portrait(work.portrait, work.fullName, 44.dp)
        Text(stringResource(R.string.ob0_hello), style = Type.caps.ui(), color = p.giltText)
    }
    Spacer(Modifier.height(Tokens.Space.s4))
    Text(stringResource(R.string.ob0_title), style = Type.title.ui(), color = p.ink)
    Spacer(Modifier.height(Tokens.Space.s3))
    Text(stringResource(R.string.ob0_body), style = Type.body.ui(), color = p.inkSoft)
    Spacer(Modifier.height(Tokens.Space.s6))
    Text(stringResource(R.string.ob0_day), style = Type.small.ui(), color = p.giltText)
    Spacer(Modifier.height(Tokens.Space.s3))
    val days = listOf(
        R.string.ob0_d1 to R.string.ob0_d1d, R.string.ob0_d2 to R.string.ob0_d2d, R.string.ob0_d3 to R.string.ob0_d3d,
        R.string.ob0_d4 to R.string.ob0_d4d, R.string.ob0_d5 to R.string.ob0_d5d,
    )
    days.forEachIndexed { i, (t, d) ->
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            // 시간의 줄: 동그라미 숫자를 세로선으로 잇는다
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp).fillMaxHeight()) {
                Box(Modifier.size(30.dp).border(1.dp, p.giltText, CircleShape).background(if (i == days.lastIndex) p.giltText else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(roman(i + 1), style = Type.small.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = if (i == days.lastIndex) p.paper else p.giltText)
                }
                if (i < days.lastIndex) Box(Modifier.width(1.dp).weight(1f).background(p.hair))
            }
            Column(Modifier.weight(1f).padding(bottom = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(t), style = Type.body.ui(), color = p.ink)
                Text(stringResource(d), style = Type.small.ui(), color = p.inkSoft)
            }
        }
    }
}

/** 1단계: 편지는 이렇게 도착해요. */
@Composable
private fun Arrive(s: AppState, work: io.github.graviton94.todayletters.data.Work) {
    val p = Ink.palette
    val first = work.chapters.first().letters.first()
    val learn = Langs.defaultLearn(work.series, s.app.read)
    val read = s.app.read
    val line = first.messages.first().text
    var playing by remember { mutableStateOf(false) }
    Text(stringResource(R.string.ob1_title), style = Type.title.ui(), color = p.ink)
    Spacer(Modifier.height(Tokens.Space.s2))
    Text(stringResource(R.string.ob1_note), style = Type.small.ui(), color = p.inkSoft)
    Spacer(Modifier.height(Tokens.Space.s5))
    Slip(seed = 7, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = Tokens.Space.s4, end = Tokens.Space.s4, top = Tokens.Space.s4, bottom = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Portrait(work.portrait, work.fullName, 30.dp)
                Text(work.sender, style = Type.heading.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.slipInk, modifier = Modifier.weight(1f))
                Text("${first.place.uppercase()} · ${dayOf(first.date).uppercase()} ${first.date.take(4)}", style = Type.capsSm, color = p.slipSoft)
            }
            Pair2(line[learn], learn, line[read], read)
            Hair()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                SpeakerButton(playing, stringResource(R.string.listen_line)) {
                    if (playing) { s.narrator.stop(); playing = false }
                    else playing = s.narrator.play(s.narrator.path(work.series.id, work.chapters.first().id, first.id, "m1_${learn.code}")) { playing = false }
                }
                Text(stringResource(R.string.ob1_listen), style = Type.small.ui(), color = p.slipSoft)
            }
        }
    }
    Spacer(Modifier.height(Tokens.Space.s6))
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        listOf(
            Triple("I", R.string.ob1_a_t, R.string.ob1_a_d),
            Triple("II", R.string.ob1_b_t, R.string.ob1_b_d),
            Triple("III", R.string.ob1_c_t, R.string.ob1_c_d),
        ).forEach { (n, t, d) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.size(30.dp).border(1.dp, p.giltText, CircleShape), contentAlignment = Alignment.Center) {
                    Text(n, style = Type.label.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.giltText)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(t), style = Type.body.ui(), color = p.ink)
                    Text(stringResource(d), style = Type.small.ui(), color = p.inkSoft)
                }
            }
        }
    }
}

/** 2단계: 누구의 편지부터 · 배울 언어. */
@Composable
private fun Pick(s: AppState, work: io.github.graviton94.todayletters.data.Work, learn: Lang, onLearn: (Lang) -> Unit) {
    val p = Ink.palette
    val read = s.app.read
    val first = work.chapters.first()
    val line = first.letters.first().messages.first().text
    Text(stringResource(R.string.ob_series), style = Type.title.ui(), color = p.ink)
    Spacer(Modifier.height(Tokens.Space.s4))
    Slip(seed = 11, modifier = Modifier.fillMaxWidth().border(1.5.dp, p.giltText)) {
        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Portrait(work.portrait, work.fullName, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(work.title[uiLang()], style = Type.heading.ui(), color = p.slipInk)
                    Text("${work.name[uiLang()]} → ${work.recipient[uiLang()]} · ${work.years}", style = Type.small.ui(), color = p.slipSoft)
                }
                CheckMark(true, 22.dp)
            }
            Text(
                stringResource(R.string.ob2_meta, "${roman(1)}. ${first.title[uiLang()]}", stringResource(langLabel(work.series.original))),
                style = Type.small.ui(), color = p.slipSoft,
            )
            Hair()
            Text(stringResource(R.string.ob2_learn), style = Type.capsSm.ui(), color = Color(0xFF7A5A20))
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Langs.learnChoices(work.series, read).forEach { l ->
                    val on = l == learn
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = on }
                            .background(if (on) p.slipInk else Color.Transparent).border(1.dp, if (on) p.slipInk else p.slipSoft.copy(alpha = 0.5f))
                            .clickable(role = Role.RadioButton) { onLearn(l) }.padding(vertical = Tokens.Space.s2),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(langLabel(l)), style = Type.body.ui(), color = if (on) p.slip else p.slipInk)
                        Text(
                            stringResource(if (l == work.series.original) R.string.ob2_original else R.string.ob2_translation),
                            style = Type.small.ui().copy(fontSize = 11.sp), color = if (on) p.slip.copy(alpha = 0.75f) else p.slipSoft,
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(p.slipLaid.copy(alpha = 0.12f)).padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(line[learn].take(34) + "…", style = Type.target.of(learn), color = p.slipInk, maxLines = 1)
                Text(line[read].take(22) + "…", style = Type.base.of(read), color = p.slipSoft, maxLines = 1)
            }
            Text(stringResource(R.string.ob2_note, stringResource(langLabel(read))), style = Type.small.ui(), color = p.slipSoft)
        }
    }
    Spacer(Modifier.height(Tokens.Space.s6))
    Caps(stringResource(R.string.ob2_soon), p.inkSoft, small = true)
    Spacer(Modifier.height(Tokens.Space.s2))
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        soon.forEach { w ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).dashed(p.line).padding(horizontal = Tokens.Space.s3),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                Box(Modifier.size(34.dp).background(Color.Transparent)) { Portrait(w.portrait, stringResource(w.title), 34.dp) }
                Text(stringResource(w.title), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.weight(1f))
                Text(stringResource(langLabel(w.lang)), style = Type.small.ui(), color = p.inkSoft)
            }
        }
    }
}
