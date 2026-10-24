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
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.launch
import io.github.graviton94.todayletters.data.lexicons
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
    // 되돌리기 어렵거나 데이터가 드는 일은 한 번 묻는다
    var askTutorial by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var timeSheet by remember { mutableStateOf(false) }
    var exportSheet by remember { mutableStateOf(false) }
    var importChooser by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<io.github.graviton94.todayletters.core.Backup.Records?>(null) }
    val ctx = s.ctx
    val records = io.github.graviton94.todayletters.data.Records
    fun toast(id: Int) = android.widget.Toast.makeText(ctx, ctx.getString(id), android.widget.Toast.LENGTH_SHORT).show()
    val openRecords = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { val r = records.read(ctx, uri); if (r == null) toast(R.string.backup_bad) else importing = r }
    }
    val activity = androidx.compose.ui.platform.LocalContext.current.let { io.github.graviton94.todayletters.data.Billing.activityOf(it) }
    if (credits) { CreditsScreen(s) { credits = false }; return }
    BackHandler { s.settingsOpen = false }
    s.version
    val w = s.works.firstOrNull()
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsSafe().navigationBarsSafe()) {
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
                            Text(m[view.learn], style = Type.target.of(view.learn), color = p.ink)
                            Text(m[view.read], style = Type.base.of(view.read), color = p.inkSoft)
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
                    Seg((listOf(Lang.KO, Lang.EN) + s.works.map { it.series.original }).distinct().map { it to langName(it) }, a.read) { s.update(a.copy(read = it)) }
                }
                // 배울 언어 · 번역 줄 · 걸음 · 듣기 빠르기는 작품마다: 지금 작품의 작품 설정으로 바로
                s.works.firstOrNull { it.series.id == s.current }?.let { wk ->
                    SetRow(stringResource(R.string.set_series_go, wk.name[uiLang()]), onClick = { s.settingsOpen = false; s.go(io.github.graviton94.todayletters.core.Route.SeriesSettings(wk.series.id)) }) { Chev() }
                }
            }
            // 알림: 도착 (작품마다 켜고 끄던 것을 한 번에) · 저녁 (이어 읽기 지키기)
            Group(stringResource(R.string.set_g_notice)) {
                // 도착 알림 시각: 누르면 시계 바퀴 (오전/오후 · 시 · 5분 단위) · 저장 / 끄기
                val arrivalOn = s.works.any { s.seriesSettings(it.series.id).arrivalNotice }
                SetRow(stringResource(R.string.set_arrival), stringResource(R.string.set_arrival_d), onClick = { timeSheet = true }) {
                    Value(if (arrivalOn) noticeTime(s.store.noticeHour, s.store.noticeMinute) else stringResource(R.string.off))
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
                // 받기가 뒤에서 끝나도 (앱을 닫았다 열어도) 새로 센다
                val gen = io.github.graviton94.todayletters.data.AssetFiles.generation
                val stored = remember(s.version, gen) { s.downloads.stored() }
                val ahead = remember(s.version, gen) { s.works.mapNotNull { wk -> s.downloads.all(wk.series.id, s.chapterIdOpen(wk.series.id))?.takeIf { !s.downloads.ready(it) } } }
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
                if (stored > 0) SetRow(stringResource(R.string.set_storage_clear), danger = true, onClick = { askClear = true }) { }
            }
            // 기록 보관 (시안 backup.html): 진도 · 설정 파일 하나로 내보내기 / 가져오기. 산 것은 파일이 아니라 Play 에서.
            Group(stringResource(R.string.set_g_backup)) {
                val last = s.store.backupDay.also { s.version }
                SetRow(stringResource(R.string.backup_export), onClick = { exportSheet = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        if (last >= 0) java.time.LocalDate.ofEpochDay(last).let { d -> Value(stringResource(R.string.backup_last, d.monthValue, d.dayOfMonth)) }
                        Chev()
                    }
                }
                SetRow(stringResource(R.string.backup_import), stringResource(R.string.backup_import_d2), onClick = { importChooser = true }) { Chev() }
            }
            Text(stringResource(R.string.backup_note), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            Group(stringResource(R.string.set_g_help)) {
                // 튜토리얼 다시 하기: 묻고 → 빈센트의 편지 I 로 처음부터 (진도는 그대로, AppState.replayTutorial)
                SetRow(stringResource(R.string.set_help_again), onClick = { askTutorial = true }) { Chev() }
                var privacy by remember { mutableStateOf(false) }
                SetRow(stringResource(R.string.set_privacy_row), stringResource(R.string.set_privacy_d), onClick = { privacy = !privacy },
                    below = if (!privacy) null else ({ Text(stringResource(R.string.set_privacy), style = Type.small.ui(), color = p.inkSoft) })) { Chev() }
                // 의견 보내기: 메일 앱으로 (오류 · 제안 · 기타, 기기 정보와 최근 오류 기록은 고를 때만)
                SetRow(stringResource(R.string.set_feedback), stringResource(R.string.set_feedback_d), onClick = { s.feedback = AppState.FeedbackAsk() }) { Chev() }
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
    if (timeSheet) NoticeTimeSheet(s) { timeSheet = false }
    BackupExportFlow(s, exportSheet) { exportSheet = false }
    if (importChooser) BackupImportChooser(ctx.getString(R.string.backup_file),
        onPick = { importChooser = false; runCatching { openRecords.launch(arrayOf("application/json", "application/octet-stream")) } },
        onClose = { importChooser = false })
    importing?.let { r ->
        BackupImportSheet(r.summary, remember(r) { records.summary(s.store, s.today) }, onImport = {
            importing = null
            // 이 폰의 이용권 · 개발용 열쇠는 그대로, 나머지는 파일로. 그다음 화면을 처음부터 (새 AppState → Play 에서 이용권 다시 확인 · 알림 다시 걸기).
            if (s.store.replaceRecords(r.values)) {
                toast(R.string.backup_done)
                s.settingsOpen = false
                activity?.recreate()
            } else toast(R.string.backup_bad)
        }, onClose = { importing = null })
    }
    if (askTutorial) Ask(stringResource(R.string.tutorial_again_t), stringResource(R.string.tutorial_again_yes), stringResource(R.string.cancel),
        onYes = { askTutorial = false; s.replayTutorial() }, onNo = { askTutorial = false }, body = stringResource(R.string.tutorial_again_b))
    if (askClear) Ask(stringResource(R.string.clear_t), stringResource(R.string.clear_yes), stringResource(R.string.cancel),
        onYes = { askClear = false; s.clearDownloads() }, onNo = { askClear = false }, body = stringResource(R.string.clear_b, sizeLabel(s.downloads.stored())))
}

/** 알림 시각 글자: 오전 8:30 · 8:30 AM. */
@Composable
internal fun noticeTime(h24: Int, m: Int): String {
    val h12 = (h24 % 12).let { if (it == 0) 12 else it }
    return stringResource(if (h24 < 12) R.string.notice_at_am else R.string.notice_at_pm, h12, m)
}

/**
 * 편지 알림 시각 (APK 20 피드백 7): 위아래로 굴리는 시계 바퀴 셋 — 오전/오후 · 시 · 분 (5분 단위).
 * 저장하면 모든 작품의 도착 알림을 켜고 그 시각에 맞춰 다시 건다. 끄기는 모든 작품의 도착 알림을 끈다.
 */
@Composable
fun NoticeTimeSheet(s: AppState, onClose: () -> Unit) {
    val p = Ink.palette
    BackHandler(onBack = onClose)
    val h24 = s.store.noticeHour.coerceIn(0, 23)
    var pm by remember { mutableStateOf(h24 >= 12) }
    var hour by remember { mutableStateOf((h24 % 12).let { if (it == 0) 12 else it }) }
    var minute by remember { mutableStateOf((s.store.noticeMinute.coerceIn(0, 59) / 5) * 5) }
    val hours = (1..12).toList()
    val minutes = (0..55 step 5).toList()
    fun commit(on: Boolean) {
        if (on) { s.store.noticeHour = (hour % 12) + if (pm) 12 else 0; s.store.noticeMinute = minute }
        s.works.forEach { wk -> s.update(wk.series.id, s.seriesSettings(wk.series.id).copy(arrivalNotice = on)) }
        io.github.graviton94.todayletters.data.Notices.schedule(s.ctx)
        onClose()
    }
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose).statusBarsSafe(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsSafe().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(stringResource(R.string.notice_sheet_t), style = Type.heading.ui(), color = p.ink)
            Text(stringResource(R.string.notice_sheet_d), style = Type.small.ui(), color = p.inkSoft)
            Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Wheel(listOf(stringResource(R.string.notice_am), stringResource(R.string.notice_pm)), if (pm) 1 else 0, Modifier.weight(1f)) { pm = it == 1 }
                Wheel(hours.map { "$it" }, hours.indexOf(hour).coerceAtLeast(0), Modifier.weight(1f)) { hour = hours[it] }
                Wheel(minutes.map { it.toString().padStart(2, '0') }, minutes.indexOf(minute).coerceAtLeast(0), Modifier.weight(1f)) { minute = minutes[it] }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.weight(1f)) { Secondary(stringResource(R.string.off), small = true) { commit(false) } }
                Box(Modifier.weight(1f)) { Primary(stringResource(R.string.save), small = true) { commit(true) } }
            }
        }
    }
}

/**
 * 시계 바퀴 한 줄: 세 칸이 보이고 가운데 칸이 고른 값. 굴리면 칸에 맞춰 멈춘다 (snap). 칸을 눌러도 그 값으로.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
private fun Wheel(labels: List<String>, start: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = Ink.palette
    val rowH = 44.dp
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = state)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 가운데에 가장 가까운 칸
    val center by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: start
        }
    }
    androidx.compose.runtime.LaunchedEffect(center) { if (center in labels.indices) onSelect(center) }
    Box(modifier.height(rowH * 3)) {
        // 가운데 칸 표시: 위아래 가는 선
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowH).drawBehind {
            drawLine(p.line, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
            drawLine(p.line, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
        })
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), state = state, flingBehavior = fling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = rowH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(labels.size) { i ->
                val on = i == center
                Box(Modifier.fillMaxWidth().height(rowH).semantics { this.selected = on }
                    .pressable(haptic = false) { scope.launch { state.animateScrollToItem(i) } }, contentAlignment = Alignment.Center) {
                    Text(labels[i], style = if (on) Type.heading.ui() else Type.body.ui(), color = if (on) p.ink else p.hideInk, maxLines = 1)
                }
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
        // 이름이 한 줄에 다 들어가지 않을 만큼 고르는 칸이 넓으면 (번역 네 칸 등) 고르는 칸을 이름 아래로 내린다
        androidx.compose.ui.layout.Layout(content = {
            Text(label, style = Type.body.ui(), color = if (danger) p.wrong else p.ink)
            if (sub != null) Text(sub, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft) else Box(Modifier)
            Box { trailing() }
        }) { ms, c ->
            val gap = Tokens.Space.s3.roundToPx()
            val line = 2.dp.roundToPx()
            val maxW = c.maxWidth
            val trailW = ms[2].maxIntrinsicWidth(c.maxHeight).coerceAtMost(maxW)
            val labelW = ms[0].maxIntrinsicWidth(c.maxHeight)
            val loose = c.copy(minWidth = 0, minHeight = 0)
            if (labelW + gap + trailW <= maxW) {
                val tr = ms[2].measure(loose.copy(maxWidth = trailW))
                val textW = (maxW - trailW - gap).coerceAtLeast(0)
                val lb = ms[0].measure(loose.copy(maxWidth = textW))
                val sb = ms[1].measure(loose.copy(maxWidth = textW))
                val textH = lb.height + (if (sb.height > 0) line + sb.height else 0)
                val h = maxOf(textH, tr.height)
                layout(maxW, h) {
                    val ty = (h - textH) / 2
                    lb.placeRelative(0, ty); sb.placeRelative(0, ty + lb.height + line)
                    tr.placeRelative(maxW - tr.width, (h - tr.height) / 2)
                }
            } else {
                val lb = ms[0].measure(loose)
                val sb = ms[1].measure(loose)
                val tr = ms[2].measure(loose)
                val textH = lb.height + (if (sb.height > 0) line + sb.height else 0)
                val h = textH + gap + tr.height
                layout(maxW, h) {
                    lb.placeRelative(0, 0); sb.placeRelative(0, lb.height + line)
                    tr.placeRelative(0, textH + gap)
                }
            }
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
 * 출처 · 오픈소스 고지: 편지 글(1914년판), 그림마다 소장처와 라이선스(편지 데이터에서), 메달이 된 유물(medals.json), 초상, 낭독 모델, 글꼴 OFL 전문.
 * 그림 목록은 작품 데이터에서 만들어서, 편지가 늘어도 이 화면을 고칠 일이 없다.
 */
@Composable
private fun CreditsScreen(s: AppState, onClose: () -> Unit) {
    val p = Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ui = uiLang()
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(p.paper).statusBarsSafe().navigationBarsSafe()) {
        TopBar(stringResource(R.string.set_credits), s, help = null, showBack = false, showSettings = false) {
            IconButton(stringResource(R.string.close), onClick = onClose) { Text("×", style = Type.heading, color = p.ink) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps(stringResource(R.string.credits_text_h), p.giltText)
            if (s.works.any { it.series.original == Lang.FR }) Text(stringResource(R.string.credits_text), style = Type.small.ui(), color = p.ink)
            if (s.works.any { it.series.original == Lang.DE }) Text(stringResource(R.string.credits_text_de), style = Type.small.ui(), color = p.ink)
            if (s.works.any { it.series.original == Lang.ES }) Text(stringResource(R.string.credits_text_es), style = Type.small.ui(), color = p.ink)

            Caps(stringResource(R.string.credits_images_h), p.giltText)
            Text(stringResource(R.string.credits_images_note), style = Type.small.ui(), color = p.inkSoft)
            s.works.forEach { w -> (w.prologue.map { it.letter } + w.chapters.flatMap { it.letters } + (w.outro?.epilogues ?: emptyList())).distinctBy { it.plate?.image ?: it.id }.forEach { l ->
                l.plate?.let { pl ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${pl.title[ui]} · ${pl.date}", style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        // 작가 · 소장처 · 라이선스 (짧게). 라이선스가 따로 없으면 소장처 줄에 들어 있다 (“… · CC0”)
                        Text(listOf(pl.credit, pl.collection, pl.license).filter { it.isNotBlank() }.joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                        if (pl.source.isNotEmpty()) Text(pl.source, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                }
                l.moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Photo>().filter { it.credit.isNotEmpty() }.forEach { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.caption[ui], style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(m.credit, style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            }
                // 맺음말의 그림
                w.outro?.let { o ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(deco(w.series.original, "Épilogue", "Epilog", "Epílogo"), style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(o.credit, style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            }
            // 편지 속 그 곡: 곡 · 연주 · 라이선스 (녹음이 있는 것만)
            val music = s.works.flatMap { w -> w.chapters.flatMap { it.letters }.flatMap { l -> l.moments.filterIsInstance<io.github.graviton94.todayletters.core.Moment.Music>() } }
                .filter { it.playable }.distinctBy { it.audio }
            if (music.isNotEmpty()) {
                Caps(stringResource(R.string.credits_music_h), p.giltText)
                music.forEach { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.piece[ui], style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(listOf(m.performer, m.license).filter { it.isNotBlank() }.joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                    }
                }
            }
            // 갤러리 소장품 (오늘의 자료로 걸리는 작품)
            s.works.forEach { w -> w.kit.collection.forEach { pc ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${pc.title[ui]} · ${pc.date}", style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                    Text(pc.collection, style = Type.small.ui(), color = p.inkSoft)
                }
            } }

            // 상점 줄의 작은 그림 (모든 시리즈가 같은 그림, data/shop.json): 작품 · 작가 · 때 / 소장처 · 번호 · 라이선스 · 부분 / 주소
            val shopArt = androidx.compose.runtime.remember { io.github.graviton94.todayletters.data.ShopArt.all(ctx) }
            if (shopArt.isNotEmpty()) {
                Caps(stringResource(R.string.credits_shop_h), p.giltText)
                shopArt.forEach { pc ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(listOf(pc.title(ui), pc.artist, pc.date).filter { it.isNotBlank() }.joinToString(" · "),
                            style = Type.small.ui().copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                        Text(listOf(pc.museum, pc.objectId, pc.license, pc.detail(ui)).filter { it.isNotBlank() }.joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                        if (pc.url.isNotEmpty()) Text(pc.url, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                    }
                }
            }

            // 메달 진열장: 메달이 된 고대 유물 (소장처 · 소장품 번호 · 라이선스, data/medals.json)
            Caps(stringResource(R.string.credits_medals_h), p.giltText)
            Text(stringResource(R.string.credits_medals_note), style = Type.small.ui(), color = p.inkSoft)
            (s.medalBook.medals.map { it.source } + listOfNotNull(s.medalBook.spare)).distinctBy { it.objectId }.forEach { src ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${src.title} · ${src.date}", style = Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = p.ink)
                    Text(listOf(src.artist, src.museum, "#${src.objectId}", src.license).filter { it.isNotBlank() }.joinToString(" · "), style = Type.small.ui(), color = p.inkSoft)
                    Text(src.url, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                }
            }

            // 낱말 자료: 등급(FrequencyWords) · 예문(Tatoeba, 문장마다 작성자)
            if (s.baseWorks.any { it.lexicons.isNotEmpty() }) {
                Caps(stringResource(R.string.credits_vocab_h), p.giltText)
                Text(stringResource(R.string.credits_vocab), style = Type.small.ui(), color = p.ink)
                val examples = androidx.compose.runtime.remember { s.baseWorks.flatMap { w -> w.lexicons.flatMap { lx -> lx.entries.mapNotNull { it.example?.takeIf { ex -> ex.fromTatoeba } } } }.distinctBy { it.id }.sortedBy { it.author.lowercase() } }
                var openEx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (examples.isNotEmpty()) {
                    Text(stringResource(R.string.credits_vocab_examples) + " (${examples.size})" + if (openEx) " ▴" else " ▾", style = Type.small.ui(), color = p.giltText,
                        modifier = Modifier.pressable { openEx = !openEx }.padding(vertical = 6.dp))
                    if (openEx) Text(examples.joinToString("\n") { "#${it.id} · ${it.author} — ${it.text}" }, style = Type.small.copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                }
            }

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
            // 맨 끝: 전체 출처와 라이선스 (누리집)
            Text(stringResource(R.string.credits_full), style = Type.small.ui().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = p.ink,
                modifier = Modifier.heightIn(min = 44.dp).pressable {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://graviton94.github.io/todayletters/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(vertical = 12.dp))
        }
    }
}
