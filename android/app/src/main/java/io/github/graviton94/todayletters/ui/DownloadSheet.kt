package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Faces
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 바이트 → "5.1MB" (1MB 아래면 "820KB"). */
fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_000_000) String.format(java.util.Locale.US, "%.1fMB", bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)}KB"

/**
 * 앱 밖(R2)에 둔 장의 낭독 받기. 먼저 크기(와 모바일 데이터인지)를 알려 주고, 받을지 · 소리 없이 읽을지 · 뒤로 갈지 고르게 한다.
 * 받는 동안에는 막대와 %, 받은 양 / 전체. 뒤로 가기나 ‘그만 받기’로 언제든 멈출 수 있다.
 */
@Composable
fun DownloadSheet(s: AppState, ask: AppState.DownloadAsk) {
    val p = Ink.palette
    val scope = rememberCoroutineScope()
    val remaining = remember(ask) { s.downloads.remaining(ask.pack) }
    val mobile = remember(ask) { s.downloads.onMobileData() }
    var job by remember { mutableStateOf<Job?>(null) }
    var got by remember { mutableLongStateOf(0L) }
    var frac by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    val chapterTitle = if (ask.chapter == io.github.graviton94.todayletters.data.Downloads.ALL) stringResource(R.string.dl_all_chapters)
        else s.work(ask.series).chapters.firstOrNull { it.id == ask.chapter }?.title?.get(uiLang()).orEmpty()

    fun close() { job?.cancel(); job = null; s.downloadAsk = null }
    fun start() {
        failed = false
        job = scope.launch {
            try {
                s.downloads.fetch(ask.pack) { done, total -> got = done; frac = if (total == 0L) 1f else done.toFloat() / total }
                job = null
                s.downloadFinished(silent = false)
            } catch (e: CancellationException) { throw e } catch (_: Throwable) { job = null; failed = true }
        }
    }
    BackHandler { close() }

    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false) { if (job == null) close() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding()
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps("AUDIO", p.giltText, small = true, decorative = true)
            Text(stringResource(R.string.dl_title, chapterTitle), style = Type.heading.ui(), color = p.ink)
            if (job == null) {
                Text(stringResource(if (failed) R.string.dl_failed else if (ask.letter == null) R.string.dl_body_ahead else R.string.dl_body, sizeLabel(remaining)), style = Type.small.ui(), color = p.inkSoft)
                if (mobile && !failed) Text(stringResource(R.string.dl_mobile), style = Type.small.ui(), color = p.giltText)
                Box(Modifier.height(Tokens.Space.s2))
                Primary(stringResource(if (failed) R.string.dl_retry else R.string.dl_go, sizeLabel(remaining))) { start() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.heightIn(min = 44.dp).pressable { close() }, contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(R.string.back), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = p.ink)
                    }
                    if (ask.letter != null) Box(Modifier.heightIn(min = 44.dp).pressable { s.downloadFinished(silent = true) }, contentAlignment = Alignment.CenterEnd) {
                        Text(stringResource(R.string.dl_silent), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = p.inkSoft)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(frac * 100).toInt()}%", style = Type.display.copy(fontFamily = Faces.display), color = p.ink, modifier = Modifier.weight(1f))
                    Text("${sizeLabel(got)} / ${sizeLabel(ask.pack.bytes)}", style = Type.small, color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(p.hair)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).background(p.giltText))
                }
                Text(stringResource(R.string.dl_loading), style = Type.small.ui(), color = p.inkSoft)
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable { close() }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dl_stop), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = p.ink)
                }
            }
        }
    }
}
