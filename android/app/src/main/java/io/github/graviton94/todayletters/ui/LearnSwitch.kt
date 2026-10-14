package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.Langs
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/*
 * 배우는 말 바꾸기 (v22): 서재의 작품 카드와 시리즈 안 머리에 작은 단추 하나. 누르면 고르기 시트.
 * 진도는 언어마다 따로라, 고르면 그 언어의 진도(읽은 편지 · 카드 · 오늘의 할 일)로 건너간다. 처음 바꿀 때 한 번 알림.
 */

/** 배우는 말 이름: 앱 글자 언어 이름 + (그 말 스스로의 이름, 다를 때만) — “프랑스어 (Français)”, “한국어”. */
@Composable
fun learnLabel(l: io.github.graviton94.todayletters.core.Lang): String {
    val ui = stringResource(langLabel(l)); val own = langName(l)
    return if (io.github.graviton94.todayletters.core.Breaks.plain(ui) == own) ui else "$ui ($own)"
}

/** 작은 단추: “배우는 말: 프랑스어 (Français) ▾”. [ink] · [line] 은 그 자리의 글자 · 테 색. */
@Composable
fun LearnChip(s: AppState, id: String, ink: Color = Ink.palette.ink, line: Color = Ink.palette.line) {
    var open by remember(id) { mutableStateOf(false) }
    val learn = s.room(id).learn
    val label = learnLabel(learn)
    val a11y = stringResource(R.string.learn_switch_a11y, label)
    Box(
        Modifier.heightIn(min = 44.dp).semantics(mergeDescendants = true) { contentDescription = a11y }.pressable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.learn_switch, label) + " ▾", style = Type.small.ui(), color = ink, maxLines = 1,
            modifier = Modifier.border(1.dp, line).padding(horizontal = 10.dp, vertical = 4.dp))
    }
    if (open) LearnPicker(s, id) { open = false }
}

/** 고르기 시트: 고를 수 있는 배우는 말마다 그 언어의 진도 (편지 n / N). */
@Composable
fun LearnPicker(s: AppState, id: String, onClose: () -> Unit) {
    val p = Ink.palette
    val w = s.work(id)
    val cur = s.room(id).learn
    val total = w.chapters.sumOf { it.letters.size }
    // 바꾸기 전에 한 번 묻는다 (진도는 언어마다 따로라 화면의 편지 · 카드가 바뀐다)
    var confirm by remember { mutableStateOf<Lang?>(null) }
    BackHandler(onBack = onClose)
    androidx.compose.ui.window.Popup(onDismissRequest = onClose, properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(p.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter) {
            InSeries(w) {
                val q = Ink.palette
                Column(
                    Modifier.fillMaxWidth().background(q.paper).clickable(enabled = false) {}.navigationBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(Tokens.Space.s5),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                ) {
                    Rule()
                    Caps(w.name[uiLang()].uppercase(), q.giltText, small = true, decorative = true)
                    Text(stringResource(R.string.learn_switch_title), style = Type.heading.ui(), color = q.ink)
                    Text(stringResource(R.string.lang_notice_body), style = Type.small.ui(), color = q.inkSoft)
                    Langs.learnChoices(w.series, s.app.read).forEach { l ->
                        val on = l == cur
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { selected = on }
                                .background(if (on) q.leaf else Color.Transparent).border(if (on) 1.6.dp else 1.dp, if (on) q.ink else q.line)
                                .clickable(role = Role.RadioButton) { if (l == cur) onClose() else confirm = l }
                                .padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(langLabel(l)) + if (l == w.series.original) " " + stringResource(R.string.ob2_original) else "",
                                    style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = q.ink)
                                Text(stringResource(R.string.lib_letters, s.doneIn(id, l), total), style = Type.small.ui(), color = q.inkSoft)
                            }
                            if (on) Capsule(stringResource(R.string.learn_switch_now), CapsuleKind.FILLED)
                        }
                    }
                    Secondary(stringResource(R.string.close), onClick = onClose)
                }
            }
            confirm?.let { l ->
                Ask(stringResource(R.string.learn_confirm_t), stringResource(R.string.learn_confirm_yes), stringResource(R.string.cancel),
                    onYes = { confirm = null; s.switchLearn(id, l); onClose() }, onNo = { confirm = null },
                    body = stringResource(R.string.learn_confirm_b, stringResource(langLabel(cur)), stringResource(langLabel(l))))
            }
        }
    }
}

/** 처음 배우는 말을 바꿨을 때 한 번: ‘언어마다 진도가 따로 저장돼요’. */
@Composable
fun LangNoticeHost(s: AppState) {
    if (!s.langNotice) return
    val p = Ink.palette
    BackHandler { s.langNotice = false }
    Box(Modifier.fillMaxSize().background(p.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { s.langNotice = false },
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.84f).background(p.paper).border(Tokens.Stroke.hair, p.gilt).clickable(enabled = false) {}
                .padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps("Guide", p.giltText, small = true, decorative = true)
            Text(stringResource(R.string.lang_notice_title), style = Type.body.ui().copy(fontWeight = FontWeight.SemiBold), color = p.ink)
            Text(stringResource(R.string.lang_notice_body), style = Type.small.ui(), color = p.inkSoft)
            Primary(stringResource(R.string.coach_ok), small = true) { s.langNotice = false }
        }
    }
}
