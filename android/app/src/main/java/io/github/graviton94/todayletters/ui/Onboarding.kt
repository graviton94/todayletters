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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    Lang.KO -> "한국어"; Lang.EN -> "English"; Lang.FR -> "Français"; Lang.NL -> "Nederlands"; Lang.DE -> "Deutsch"; Lang.JA -> "日本語"; Lang.ES -> "Español"
}

/** 언어 이름: 앱 글자 언어로 (프랑스어 / French). 설명 문장 안에 쓴다. */
fun langLabel(l: Lang) = when (l) {
    Lang.KO -> R.string.lang_ko; Lang.EN -> R.string.lang_en; Lang.FR -> R.string.lang_fr; Lang.NL -> R.string.lang_nl; Lang.DE -> R.string.lang_de; Lang.JA -> R.string.lang_ja; Lang.ES -> R.string.lang_es
}

/**
 * 처음 설치의 환영 한 장 (튜토리얼 1, core 의 Tutorial): “첫 방문을 환영해요. 우선, 처음이니 제 친구 빈센트 반 고흐와 함께 연습해 봐요.”
 * 누르면 서재로 — 서재가 첫 작품 카드를 강조하고, 배울 말은 그 카드에서 고른다.
 */
@Composable
fun Onboarding(s: AppState) {
    val p = Ink.palette
    val work = s.works.first()
    val ko = uiLang() == Lang.KO
    val name = work.kit.fullName[uiLang()].ifBlank { work.fullName }
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f).statusBarsSafe().navigationBarsSafe()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s8),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4, Alignment.CenterVertically)) {
            Box(Modifier.border(1.5.dp, p.giltText, CircleShape).padding(3.dp)) { Portrait(work.portrait, work.fullName, 88.dp) }
            Text(stringResource(R.string.welcome_title), style = Type.title.ui(), color = p.ink, modifier = Modifier.padding(top = Tokens.Space.s3))
            Text(stringResource(R.string.welcome_body, if (ko) josa(name, "과", "와") else name), style = Type.body.ui(), color = p.inkSoft)
            Text(stringResource(R.string.welcome_note), style = Type.small.ui(), color = p.inkSoft)
        }
        Box(Modifier.padding(Tokens.Space.s4)) {
            Primary(stringResource(R.string.welcome_go), shine = true) { s.narrator.stop(); s.finishWelcome() }
        }
    }
}

/**
 * 배울 말 고르기 (작품마다): 원어 · 영어 · 한국어 가운데 읽는 언어를 뺀 것, 그 말로 쓴 첫 줄 맛보기와 함께.
 * 서재의 튜토리얼 카드 · 다른 작품에 처음 들어갈 때의 시트 ([WelcomeSheet]) 가 같이 쓴다.
 */
@Composable
fun LearnPicker(s: AppState, work: io.github.graviton94.todayletters.data.Work, learn: Lang, ink: Color = Ink.palette.ink, soft: Color = Ink.palette.inkSoft,
                line: Color = Ink.palette.line, fill: Color = Ink.palette.leaf, onPick: (Lang) -> Unit) {
    val sample = work.chapters.first().letters.first().messages.first().text
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Langs.learnChoices(work.series, s.app.read).forEach { l ->
            val on = l == learn
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = on }
                    .background(if (on) fill else Color.Transparent).border(if (on) 1.6.dp else 1.dp, if (on) ink else line)
                    .clickable(role = Role.RadioButton) { onPick(l) }.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(langLabel(l)) + if (l == work.series.original) " · " + stringResource(R.string.ob2_original) else "",
                        style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = ink)
                    // 첫 줄 맛보기: 배우는 문장은 자르지 않는다 (F05)
                    Text(io.github.graviton94.todayletters.core.Breaks.plain(sample[l]), style = Type.small.of(l).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = soft)
                }
                Box(Modifier.size(18.dp).then(if (on) Modifier.background(ink, CircleShape) else Modifier.border(1.4.dp, line, CircleShape)))
            }
        }
    }
}

/**
 * 다른 작품에 처음 들어갈 때 (튜토리얼 뒤): 대표 그림 위에 배울 말만 고르는 시트. 고르면 바로 편지 I (프롤로그는 목차에서).
 */
@Composable
fun WelcomeSheet(s: AppState, id: String) {
    val w = s.work(id)
    val t = darkTone(w)
    var learn by remember(id) { mutableStateOf(Langs.defaultLearn(w.series, s.app.read)) }
    Box(Modifier.fillMaxSize().background(t.paper)) {
        AssetImage(w.kit.hero, Modifier.fillMaxSize(), sample = 2)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x33000000), 0.4f to Color(0x1A000000), 0.75f to t.paper)))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsSafe().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.border(1.5.dp, t.accent, CircleShape).padding(1.5.dp)) { Portrait(w.portrait, w.fullName, 40.dp) }
                Column(Modifier.weight(1f)) {
                    Text(w.kit.place, style = Type.capsSm, color = t.accent)
                    Text(w.fullName, style = Type.heading.ui(), color = t.ink)
                }
            }
            Text(stringResource(R.string.learn_sheet_q), style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = t.ink)
            LearnPicker(s, w, learn, ink = t.ink, soft = t.soft, line = t.ink.copy(alpha = 0.35f), fill = t.ink.copy(alpha = 0.08f)) { learn = it }
            Text(stringResource(R.string.learn_sheet_note), style = Type.small.ui(), color = t.soft)
            Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.button).background(t.ink).pressable { s.chooseLearn(id, learn) }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ob_one_go), style = Type.body.ui(), color = t.paper)
            }
        }
    }
}
