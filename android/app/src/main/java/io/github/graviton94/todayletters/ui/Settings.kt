package io.github.graviton94.todayletters.ui

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * 앱 설정 (⚙, v21 4단계 U3): 묶음 카드 — 화면 · 소리와 진동 · 언어 · 알림 · 저장 공간 · 도움과 출처.
 * 고르는 칸은 그 자리에서 바뀌고, 글자 크기는 편지 첫 줄로 미리 보여 준다. 알림(도착 · 저녁)은 작품 설정에서 여기로 옮겼다.
 */
@Composable
fun SettingsScreen(s: AppState) {
    val p = Ink.palette
    val a = s.app
    var credits by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (credits) { CreditsScreen(s) { credits = false }; return }
    BackHandler { s.settingsOpen = false }
    s.version
    val w = s.works.firstOrNull()
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.settings), s, help = null, showBack = false, showSettings = false) {
            IconButton(stringResource(R.string.close), onClick = { s.settingsOpen = false }) { Text("×", style = Type.heading, color = p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Group(stringResource(R.string.set_display)) {
                SetRow(stringResource(R.string.set_theme)) {
                    Seg(listOf(ThemeMode.SYSTEM to stringResource(R.string.set_theme_system), ThemeMode.LIGHT to stringResource(R.string.set_theme_light), ThemeMode.DARK to stringResource(R.string.set_theme_dark)), a.theme) { s.update(a.copy(theme = it)) }
                }
                SetRow(stringResource(R.string.set_text), stringResource(R.string.set_text_preview), below = {
                    // 미리 보기: 첫 편지 첫 줄 (배우는 말 · 번역)
                    w?.let { wk ->
                        val m = wk.chapters.first().letters.first().messages.first().text
                        val view = s.room(wk.series.id)
                        Column(Modifier.fillMaxWidth().background(p.paper).padding(Tokens.Space.s3)) {
                            Text(m[view.learn].take(32) + "…", style = Type.target.of(view.learn), color = p.ink, maxLines = 1)
                            Text(m[view.read].take(20) + "…", style = Type.base.of(view.read), color = p.inkSoft, maxLines = 1)
                        }
                    }
                }) {
                    Seg(listOf(false to stringResource(R.string.set_text_normal), true to stringResource(R.string.set_text_large)), a.largeText) { s.update(a.copy(largeText = it)) }
                }
                SetRow(stringResource(R.string.set_pace2)) {
                    Seg(listOf(TypingPace.CALM to stringResource(R.string.set_pace_calm), TypingPace.QUICK to stringResource(R.string.set_pace_quick), TypingPace.INSTANT to stringResource(R.string.set_pace_instant2)), a.pace) { s.update(a.copy(pace = it)) }
                }
            }
            Group(stringResource(R.string.set_feel)) {
                SetRow(stringResource(R.string.set_sound), stringResource(R.string.set_sound_d)) { Toggle(a.sound) { s.update(a.copy(sound = it)) } }
                SetRow(stringResource(R.string.set_effects2), stringResource(R.string.set_effects_d)) { Toggle(a.effects) { s.update(a.copy(effects = it)) } }
                SetRow(stringResource(R.string.set_haptics)) { Toggle(a.haptics) { s.update(a.copy(haptics = it)) } }
            }
            Group(stringResource(R.string.set_language)) {
                if (Build.VERSION.SDK_INT >= 33) SetRow(stringResource(R.string.set_ui_lang2)) {
                    Seg(listOf(UiLang.EN to "English", UiLang.KO to "한국어"), a.ui) { ui ->
                        s.update(a.copy(ui = ui))
                        s.ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(ui.code)
                    }
                }
                SetRow(stringResource(R.string.set_read_lang2)) {
                    Seg(listOf(Lang.KO, Lang.EN, Lang.FR).map { it to langName(it) }, a.read) { s.update(a.copy(read = it)) }
                }
            }
            // 알림: 도착 (작품마다 켜고 끄던 것을 한 번에) · 저녁 (이어 읽기 지키기)
            Group(stringResource(R.string.set_g_notice)) {
                val arrivalOn = s.works.any { s.seriesSettings(it.series.id).arrivalNotice }
                var hour by remember { mutableStateOf(s.store.noticeHour) }
                var openArrival by remember { mutableStateOf(false) }
                SetRow(stringResource(R.string.set_arrival), stringResource(R.string.set_arrival_d), onClick = { openArrival = !openArrival },
                    below = if (!openArrival) null else ({
                        Seg(listOf(-1 to stringResource(R.string.off)) + listOf(7, 8, 12, 19, 21).map { it to stringResource(R.string.notice_hour, it) }, if (arrivalOn) hour else -1) { h ->
                            s.works.forEach { wk -> s.update(wk.series.id, s.seriesSettings(wk.series.id).copy(arrivalNotice = h >= 0)) }
                            if (h >= 0) { hour = h; s.store.noticeHour = h }
                            io.github.graviton94.todayletters.data.Notices.schedule(s.ctx)
                        }
                    })) {
                    Value(if (arrivalOn) stringResource(R.string.notice_hour, hour) else stringResource(R.string.off))
                }
                var eh by remember { mutableStateOf(s.store.eveningHour) }
                var openEvening by remember { mutableStateOf(false) }
                SetRow(stringResource(R.string.set_evening2), stringResource(R.string.set_evening_d), onClick = { openEvening = !openEvening },
                    below = if (!openEvening) null else ({
                        Seg(listOf(-1 to stringResource(R.string.off)) + listOf(19, 20, 21, 22).map { it to stringResource(R.string.notice_hour, it) }, eh) {
                            eh = it; s.store.eveningHour = it; io.github.graviton94.todayletters.data.Notices.schedule(s.ctx)
                        }
                    })) {
                    Value(if (eh >= 0) stringResource(R.string.notice_hour, eh) else stringResource(R.string.off))
                }
            }
            // 저장 공간 (v19): 받아 둔 낭독 · 남은 것 모두 받기 · 지우기
            Group(stringResource(R.string.set_storage)) {
                val stored = remember(s.version) { s.downloads.stored() }
                val ahead = remember(s.version) { s.works.mapNotNull { wk -> s.downloads.all(wk.series.id)?.takeIf { !s.downloads.ready(it) } } }
                val left = ahead.sumOf { s.downloads.remaining(it) }
                SetRow(stringResource(R.string.set_storage_row), below = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Bar(if (stored + left > 0) stored.toFloat() / (stored + left) else 1f)
                        if (left > 0) Text(stringResource(R.string.set_storage_left, sizeLabel(left)), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                }) { Value(sizeLabel(stored)) }
                ahead.firstOrNull()?.let { pk ->
                    SetRow(stringResource(R.string.set_storage_all, sizeLabel(left)), onClick = { s.settingsOpen = false; s.predownload(pk.series, null) }) { Chev() }
                }
                var sure by remember { mutableStateOf(false) }
                if (stored > 0) SetRow(stringResource(if (sure) R.string.set_storage_clear_sure else R.string.set_storage_clear), danger = true,
                    onClick = { if (sure) { s.clearDownloads(); sure = false } else sure = true }) { }
            }
            Group(stringResource(R.string.set_g_help)) {
                SetRow(stringResource(R.string.set_help_again), onClick = { s.coachReset(); s.settingsOpen = false }) { Chev() }
                var privacy by remember { mutableStateOf(false) }
                SetRow(stringResource(R.string.set_privacy_row), stringResource(R.string.set_privacy_d), onClick = { privacy = !privacy },
                    below = if (!privacy) null else ({ Text(stringResource(R.string.set_privacy), style = Type.small.ui(), color = p.inkSoft) })) { Chev() }
                SetRow(stringResource(R.string.set_credits), stringResource(R.string.set_credits_d), onClick = { credits = true }) { Chev() }
            }
            val ver = remember {
                runCatching { s.ctx.packageManager.getPackageInfo(s.ctx.packageName, 0).let { it.versionName.orEmpty() to androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } }.getOrNull()
            }
            ver?.let { (n, c) ->
                Text(stringResource(R.string.set_version, n, c.toInt()), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.hideInk,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s5))
            }
        }
    }
}

/** 묶음 카드: 금빛 작은 제목 + 종이 카드 안의 줄들. */
@Composable
private fun Group(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = Ink.palette
    Caps(title, p.giltText, small = true, modifier = Modifier.padding(start = 4.dp, top = Tokens.Space.s4, bottom = 2.dp))
    Column(Modifier.fillMaxWidth().background(p.leaf).border(Tokens.Stroke.hair, p.hair)) { content() }
}

/** 카드 안의 한 줄: 이름 (+ 설명) · 오른쪽에 고르는 칸 · 필요하면 아래에 더. 줄 사이 가는 선. */
@Composable
private fun SetRow(label: String, sub: String? = null, danger: Boolean = false, onClick: (() -> Unit)? = null,
                   below: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit) {
    val p = Ink.palette
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(haptic = false, onClick = onClick) else Modifier)
        .drawBehind { drawLine(p.hair, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f) }
        .heightIn(min = 52.dp).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = Type.body.ui(), color = if (danger) p.wrong else p.ink)
                if (sub != null) Text(sub, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
            }
            trailing()
        }
        below?.invoke()
    }
}

/** 붙은 고르기 칸: 고른 것은 잉크로 채움. */
@Composable
private fun <T> Seg(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val p = Ink.palette
    Row(Modifier.border(1.dp, p.ink).horizontalScroll(rememberScrollState())) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == selected
            if (i > 0) Box(Modifier.width(1.dp).heightIn(min = 36.dp).background(p.ink))
            Box(Modifier.heightIn(min = 36.dp).background(if (on) p.ink else androidx.compose.ui.graphics.Color.Transparent)
                .semantics { this.selected = on }.pressable(role = androidx.compose.ui.semantics.Role.RadioButton) { onPick(v) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center) {
                Text(label, style = Type.small.ui(), color = if (on) p.paper else p.ink, maxLines = 1)
            }
        }
    }
}

/** 켜고 끄기. */
@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = Ink.palette
    Box(Modifier.size(46.dp, 28.dp).semantics { toggleableState = androidx.compose.ui.state.ToggleableState(on) }
        .background(if (on) p.ink else p.line, CircleShape).pressable(role = androidx.compose.ui.semantics.Role.Switch) { onChange(!on) }.padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(22.dp).background(p.paper, CircleShape))
    }
}

@Composable private fun Value(text: String) = Text(text, style = Type.small.ui(), color = Ink.palette.inkSoft)
@Composable private fun Chev() = Text("›", style = Type.heading, color = Ink.palette.hideInk)

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
            // 갤러리 소장품 (오늘의 자료로 걸리는 작품)
            s.works.forEach { w -> w.kit.collection.forEach { pc ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${pc.title[ui]} · ${pc.date}", style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                    Text(pc.collection, style = Type.small.ui(), color = p.inkSoft)
                }
            } }

            Caps(stringResource(R.string.credits_museum_h), p.giltText)
            Text(stringResource(R.string.credits_museum), style = Type.small.ui(), color = p.ink)
            Caps(stringResource(R.string.credits_sound_h), p.giltText)
            Text(stringResource(R.string.credits_sound), style = Type.small.ui(), color = p.ink)

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
