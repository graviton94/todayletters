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
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = Type.label.ui(), color = Ink.palette.ink)
