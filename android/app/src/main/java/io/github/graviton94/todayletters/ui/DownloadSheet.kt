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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type
import io.github.graviton94.todayletters.core.Fetching
import io.github.graviton94.todayletters.data.DownloadJob
import io.github.graviton94.todayletters.data.Downloads

/** 바이트 → "5.1MB" (1MB 아래면 "820KB"). */
fun sizeLabel(bytes: Long): String = io.github.graviton94.todayletters.core.Fetching.size(bytes)

/**
 * 앱 밖(R2)에 둔 낭독 · 그림 받기 (시안 assets.html). 묻는 말 한 줄 · 크기 한 줄 (모바일 데이터면 그 줄 하나 더) · ‘다운로드 · 27MB’ · ‘다음에’.
 * 처음 묶음 (서문 + 무료 장) 은 “모차르트의 시대로 떠날까요?”, 그 뒤의 장은 “III장으로 떠날까요?”, 맺음은 “맺음으로 떠날까요?”.
 * 받기는 [DownloadJob] (포그라운드 서비스와 알림) 이 하므로 이 시트를 닫거나 앱을 닫아도 이어진다. 받는 동안에는 막대 · 받은 양 / 전체 · %.
 * ‘멈추기’ 로 언제든 멈추고, 다시 받으면 받은 데부터. 인터넷이 끊기면 기다렸다가 이어 받는다.
 * 기다리는 편지 · 화면이 있으면 다 받은 뒤 바로 열고, 없으면 (표지 · 처음 들어올 때) “…에 닿았어요 · 첫 장 열기 ›”.
 */
@Composable
fun DownloadSheet(s: AppState, ask: AppState.DownloadAsk) {
    val p = Ink.palette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val job = DownloadJob.state?.takeIf { it.series == ask.series && it.chapter == ask.chapter }
    val running = job?.active == true
    val remaining = remember(ask, job?.phase) { s.downloads.remaining(ask.pack) }
    val mobile = remember(ask, job?.phase) { s.downloads.onMobileData() }
    val failed = job?.phase == DownloadJob.Phase.FAILED || job?.phase == DownloadJob.Phase.TIMED_OUT
    val waiting = ask.letter != null || ask.then != null
    var reached by remember(ask) { mutableStateOf(false) }
    val w = s.work(ask.series)
    val name = w.known[uiLang()]
    val first = ask.chapter == Downloads.FIRST || ask.chapter == Downloads.ALL
    val numeral = w.chapters.indexOfFirst { it.id == ask.chapter }.let { if (it >= 0) roman(it + 1) else ask.chapter }
    val title = when {
        first -> stringResource(R.string.dl_first_t, name)
        ask.chapter == "E" -> stringResource(R.string.dl_epilogue_t)
        else -> stringResource(R.string.dl_ch_t, numeral)
    }
    val going = when {
        first -> stringResource(R.string.dl_first_going, name)
        ask.chapter == "E" -> stringResource(R.string.dl_epilogue_going)
        else -> stringResource(R.string.dl_ch_going, numeral)
    }
    val sub = when (ask.chapter) {
        Downloads.FIRST -> stringResource(R.string.dl_first_b, sizeLabel(remaining))
        Downloads.ALL -> "${stringResource(R.string.dl_all_chapters)} · ${sizeLabel(remaining)}"
        else -> w.chapters.firstOrNull { it.id == ask.chapter }?.title?.get(uiLang()).orEmpty()
    }

    // 이 시트가 보고 있는 동안에는 끝 알림 대신 여기서 (기다리던 편지를 바로 연다)
    DisposableEffect(ask) {
        DownloadJob.watching = ask.series to ask.chapter
        onDispose { if (DownloadJob.watching == ask.series to ask.chapter) DownloadJob.watching = null }
    }
    LaunchedEffect(job?.phase) {
        if (job?.phase == DownloadJob.Phase.DONE) {
            DownloadJob.forget()
            if (waiting || ask.chapter != Downloads.FIRST) s.downloadFinished(silent = false) else reached = true
        }
    }

    // 닫아도 받기는 이어진다 (알림에서 보고 멈출 수 있다)
    fun close() = s.downloadLater()
    fun start() { DownloadJob.start(ctx, ask.pack, name) }
    BackHandler { close() }

    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false) { close() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding()
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            when {
                reached -> {
                    Line(stringResource(R.string.dl_first_done, name), Type.heading.ui(), p.ink)
                    Line(stringResource(R.string.dl_done_b), Type.small.ui(), p.inkSoft)
                    Box(Modifier.height(Tokens.Space.s2))
                    Primary(stringResource(R.string.dl_open_first)) { s.downloadFinished(silent = false) }
                }
                running -> {
                    val frac = if (job!!.total <= 0) 1f else job.done.toFloat() / job.total
                    Line(going, Type.heading.ui(), p.ink)
                    Box(Modifier.height(Tokens.Space.s1))
                    Box(Modifier.fillMaxWidth().height(4.dp).background(p.hair)) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).background(p.giltText))
                    }
                    // 11.3 / 27MB          42%
                    Row(Modifier.fillMaxWidth()) {
                        Text(Fetching.progress(job.done, job.total), style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${Fetching.percent(job.done, job.total)}%", style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
                    }
                    Line(stringResource(if (job.phase == DownloadJob.Phase.WAITING) R.string.dl_note_wait else R.string.dl_bg), Type.small.ui(),
                        if (job.phase == DownloadJob.Phase.WAITING) p.giltText else p.inkSoft)
                    Box(Modifier.height(Tokens.Space.s2))
                    Secondary(stringResource(R.string.dl_pause)) { DownloadJob.stop() }
                }
                failed -> {
                    Line(stringResource(R.string.dl_failed_t), Type.heading.ui(), p.ink)
                    Line(stringResource(R.string.dl_failed, sizeLabel(remaining)), Type.small.ui(), p.inkSoft)
                    Box(Modifier.height(Tokens.Space.s2))
                    Primary(stringResource(R.string.dl_retry)) { start() }
                    Later { close() }
                }
                else -> {
                    Line(title, Type.heading.ui(), p.ink)
                    if (sub.isNotEmpty()) Line(sub, Type.small.ui(), p.inkSoft)
                    // 와이파이면 데이터 줄은 없다
                    if (mobile) Line(stringResource(R.string.dl_mobile), Type.small.ui(), p.giltText)
                    Box(Modifier.height(Tokens.Space.s2))
                    Primary(stringResource(R.string.dl_go, sizeLabel(remaining))) { start() }
                    Later { close() }
                }
            }
        }
    }
}

/** 시트의 한 줄: 360dp 에서도 한 줄 (넘치면 말줄임). */
@Composable
private fun Line(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) =
    Text(text, style = style, color = color, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

/** ‘다음에’: 밑줄 친 글자 단추, 가운데. */
@Composable
private fun Later(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.dl_later), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = Ink.palette.inkSoft)
    }
}
