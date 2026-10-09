package io.github.graviton94.todayletters.ui

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Lang
import io.github.graviton94.todayletters.core.ThemeMode
import io.github.graviton94.todayletters.core.TypingPace
import io.github.graviton94.todayletters.core.UiLang
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 앱 설정 (⚙): 이름표 맨 위 화면에서만 연다. 작품마다의 설정은 작품 표지에서. */
@Composable
fun SettingsScreen(s: AppState) {
    val p = Ink.palette
    val a = s.app
    var credits by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (credits) { CreditsScreen(s) { credits = false }; return }
    BackHandler { s.settingsOpen = false }
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.settings), s, help = null, showBack = false, showSettings = false) {
            IconButton(stringResource(R.string.close), onClick = { s.settingsOpen = false }) { Text("×", style = Type.heading, color = p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(stringResource(R.string.set_display), p.giltText)
            Label(stringResource(R.string.set_theme))
            Choices(
                listOf(ThemeMode.SYSTEM to stringResource(R.string.set_theme_system), ThemeMode.LIGHT to stringResource(R.string.set_theme_light), ThemeMode.DARK to stringResource(R.string.set_theme_dark)),
                a.theme,
            ) { s.update(a.copy(theme = it)) }
            Label(stringResource(R.string.set_text))
            Choices(listOf(false to stringResource(R.string.set_text_normal), true to stringResource(R.string.set_text_large)), a.largeText) { s.update(a.copy(largeText = it)) }
            Label(stringResource(R.string.set_pace))
            Choices(
                listOf(TypingPace.CALM to stringResource(R.string.set_pace_calm), TypingPace.QUICK to stringResource(R.string.set_pace_quick), TypingPace.INSTANT to stringResource(R.string.set_pace_instant)),
                a.pace,
            ) { s.update(a.copy(pace = it)) }

            Caps(stringResource(R.string.set_language), p.giltText)
            if (Build.VERSION.SDK_INT >= 33) {
                Label(stringResource(R.string.set_ui_lang))
                Choices(listOf(UiLang.EN to "English", UiLang.KO to "한국어"), a.ui) { ui ->
                    s.update(a.copy(ui = ui))
                    s.ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(ui.code)
                }
            }
            Label(stringResource(R.string.set_read_lang))
            Choices(listOf(Lang.KO, Lang.EN, Lang.FR).map { it to langName(it) }, a.read) { s.update(a.copy(read = it)) }

            Caps(stringResource(R.string.set_feel), p.giltText)
            Label(stringResource(R.string.set_sound))
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), a.sound) { s.update(a.copy(sound = it)) }
            Label(stringResource(R.string.set_haptics))
            Choices(listOf(true to stringResource(R.string.on), false to stringResource(R.string.off)), a.haptics) { s.update(a.copy(haptics = it)) }

            Caps(stringResource(R.string.set_help), p.giltText)
            Secondary(stringResource(R.string.set_help_again)) { s.coachReset(); s.settingsOpen = false }
            Text(stringResource(R.string.set_privacy), style = Type.small.ui(), color = p.inkSoft)
            Text(stringResource(R.string.set_sources), style = Type.small.ui(), color = p.inkSoft)
            Secondary(stringResource(R.string.set_credits)) { credits = true }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = Type.label.ui(), color = Ink.palette.ink)

/**
 * 출처 · 오픈소스 고지: 편지 글(1914년판), 그림마다 소장처와 라이선스(편지 데이터에서), 초상, 낭독 모델, 글꼴 OFL 전문.
 * 그림 목록은 작품 데이터에서 만들어서, 편지가 늘어도 이 화면을 고칠 일이 없다.
 */
@Composable
private fun CreditsScreen(s: AppState, onClose: () -> Unit) {
    val p = Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ui = uiLang()
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.set_credits), s, help = null, showBack = false, showSettings = false) {
            IconButton(stringResource(R.string.close), onClick = onClose) { Text("×", style = Type.heading, color = p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(stringResource(R.string.credits_text_h), p.giltText)
            Text(stringResource(R.string.credits_text), style = Type.small.ui(), color = p.ink)

            Caps(stringResource(R.string.credits_images_h), p.giltText)
            Text(stringResource(R.string.credits_images_note), style = Type.small.ui(), color = p.inkSoft)
            s.works.forEach { w -> w.chapters.forEach { c -> c.letters.forEach { l ->
                l.plate?.let { pl ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${pl.title[ui]} · ${pl.date}", style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(pl.collection, style = Type.small.ui(), color = p.inkSoft)
                        if (pl.source.isNotEmpty()) Text(pl.source, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                }
                l.moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Photo>().filter { it.sketch && it.credit.isNotEmpty() }.forEach { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.caption[ui], style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(m.credit, style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            } } }

            Caps(stringResource(R.string.credits_portraits_h), p.giltText)
            Text(stringResource(R.string.credits_portraits), style = Type.small.ui(), color = p.ink)

            Caps(stringResource(R.string.credits_voice_h), p.giltText)
            Text(stringResource(R.string.credits_voice), style = Type.small.ui(), color = p.ink)

            Caps(stringResource(R.string.credits_about_h), p.giltText)
            Text(stringResource(R.string.credits_about), style = Type.small.ui(), color = p.ink)
            Text(stringResource(R.string.credits_links), style = Type.small.ui(), color = p.inkSoft)

            Caps(stringResource(R.string.credits_fonts_h), p.giltText)
            listOf("Cormorant Garamond" to "cormorantgaramond", "Crimson Pro" to "crimsonpro", "Cinzel" to "cinzel", "Noto Serif KR" to "notoserifkr").forEach { (name, file) ->
                val text = androidx.compose.runtime.remember(file) { runCatching { ctx.assets.open("licenses/OFL-$file.txt").bufferedReader().readText() }.getOrDefault("") }
                var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                    Text(text.lineSequence().firstOrNull().orEmpty(), style = Type.small.ui(), color = p.inkSoft)
                    Text(stringResource(R.string.credits_licence) + if (open) " ▴" else " ▾", style = Type.small.ui(), color = p.giltText,
                        modifier = Modifier.pressable { open = !open }.padding(vertical = 6.dp))
                    if (open) Text(text, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                }
            }
        }
    }
}
