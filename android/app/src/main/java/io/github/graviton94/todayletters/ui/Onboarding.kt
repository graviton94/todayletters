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

/**
 * 처음 소개 (첫 설치 한 번, v21 4단계 U4): 한 장. 무엇인지 · 하루가 어떻게 흐르는지 · 어떤 말로 읽을지.
 * ‘첫 편지 받기’면 서재 · 전환 · 오늘을 건너 바로 첫 편지 (도착 장면부터). 입구에서 첫 편지까지 두 번.
 * 번역 언어는 폰 언어로 시작하고, 답장 방식 · 하루 편지 수는 묻지 않는다 (작품 설정에서).
 */
@Composable
fun Onboarding(s: AppState) {
    val p = Ink.palette
    val work = s.works.first()
    val read = s.app.read
    var learn by remember { mutableStateOf(Langs.defaultLearn(work.series, read)) }
    val line = work.chapters.first().letters.first().messages.first().text
    Column(Modifier.fillMaxSize().desk(p.paper, p.lamp, 0.3f).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Portrait(work.portrait, work.fullName, 46.dp)
                Caps("BIENVENUE", p.giltText, small = true, decorative = true)
            }
            Text(stringResource(R.string.ob_one_title, if (uiLang() == Lang.KO) josa(work.fullName, "이", "가") else work.fullName),
                style = Type.title.ui(), color = p.ink, modifier = Modifier.padding(top = Tokens.Space.s2))
            Text(stringResource(R.string.ob_one_body, work.recipient[uiLang()], if (uiLang() == Lang.KO) josa(work.recipient[uiLang()], "이", "가") else work.recipient[uiLang()]),
                style = Type.body.ui(), color = p.inkSoft)
            Column(Modifier.padding(top = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                listOf(stringResource(R.string.ob_one_1, work.name[uiLang()]), stringResource(R.string.ob_one_2), stringResource(R.string.ob_one_3)).forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        Box(Modifier.size(28.dp).border(1.dp, p.giltText, CircleShape), contentAlignment = Alignment.Center) {
                            Text(roman(i + 1), style = Type.small.copy(fontFamily = io.github.graviton94.todayletters.design.Faces.display), color = p.giltText)
                        }
                        Text(t, style = Type.body.ui(), color = p.ink)
                    }
                }
            }
            Text(stringResource(R.string.ob_one_q), style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink,
                modifier = Modifier.padding(top = Tokens.Space.s5))
            Langs.learnChoices(work.series, read).forEach { l ->
                val on = l == learn
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { selected = on }
                        .background(if (on) p.leaf else Color.Transparent).border(if (on) 1.6.dp else 1.dp, if (on) p.ink else p.line)
                        .clickable(role = Role.RadioButton) { learn = l }.padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(langLabel(l)) + if (l == work.series.original) " " + stringResource(R.string.ob2_original) else "",
                            style = Type.body.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(line[l].take(34) + "…", style = Type.small.of(l).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = p.inkSoft, maxLines = 1)
                    }
                    Box(Modifier.size(18.dp).then(if (on) Modifier.background(p.ink, CircleShape) else Modifier.border(1.4.dp, p.line, CircleShape)))
                }
            }
            Text(stringResource(R.string.ob_one_note, stringResource(langLabel(read))), style = Type.small.ui(), color = p.hideInk)
        }
        Box(Modifier.padding(Tokens.Space.s4)) {
            Primary(stringResource(R.string.ob_one_go)) {
                s.narrator.stop()
                s.finishOnboarding(s.app, work.series.id, Langs.start(work.series, s.app).copy(learn = learn))
                s.startFirstLetter(work.series.id)
            }
        }
    }
}
