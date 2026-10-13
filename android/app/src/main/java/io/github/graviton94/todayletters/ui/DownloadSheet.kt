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
import io.github.graviton94.todayletters.core.Fetching
import io.github.graviton94.todayletters.data.DownloadJob

/** 바이트 → "5.1MB" (1MB 아래면 "820KB"). */
fun sizeLabel(bytes: Long): String = io.github.graviton94.todayletters.core.Fetching.size(bytes)

/**
 * 앱 밖(R2)에 둔 장의 낭독 받기. 먼저 크기(와 모바일 데이터인지)를 알려 주고, 받을지 · 소리 없이 읽을지 · 뒤로 갈지 고르게 한다.
 * 받기는 [DownloadJob] (포그라운드 서비스와 알림) 이 하므로 이 시트를 닫거나 앱을 닫아도 이어진다. 받는 동안에는 막대와 %, 받은 양 / 전체.
 * ‘멈추기’ 로 언제든 멈추고, 다시 받으면 받은 데부터. 인터넷이 끊기면 기다렸다가 이어 받는다.
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
    val name = s.work(ask.series).name[uiLang()]
    val chapterTitle = if (ask.chapter == io.github.graviton94.todayletters.data.Downloads.ALL) stringResource(R.string.dl_all_chapters)
        else s.work(ask.series).chapters.firstOrNull { it.id == ask.chapter }?.title?.get(uiLang()).orEmpty()

    // 이 시트가 보고 있는 동안에는 끝 알림 대신 여기서 (기다리던 편지를 바로 연다)
    DisposableEffect(ask) {
        DownloadJob.watching = ask.series to ask.chapter
        onDispose { if (DownloadJob.watching == ask.series to ask.chapter) DownloadJob.watching = null }
    }
    LaunchedEffect(job?.phase) {
        if (job?.phase == DownloadJob.Phase.DONE) { DownloadJob.forget(); s.downloadFinished(silent = false) }
    }

    // 닫아도 받기는 이어진다 (알림에서 보고 멈출 수 있다)
    fun close() { s.downloadAsk = null }
    fun start() { DownloadJob.start(ctx, ask.pack, name) }
    BackHandler { close() }

    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false) { close() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding()
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Caps("AUDIO", p.giltText, small = true, decorative = true)
            // 미리 받기 (설정 · 목차): 묻는 말로, 어느 장 · 얼마나인지 먼저
            if (ask.letter == null) {
                Text(stringResource(R.string.dl_ahead_t), style = Type.heading.ui(), color = p.ink)
                Text("$chapterTitle · ${sizeLabel(if (running) ask.pack.bytes else remaining)}", style = Type.body.ui(), color = p.ink)
            } else Text(stringResource(R.string.dl_title, chapterTitle), style = Type.heading.ui(), color = p.ink)
            if (!running) {
                Text(stringResource(if (failed) R.string.dl_failed else if (ask.letter == null) R.string.dl_body_ahead else R.string.dl_body, sizeLabel(remaining), name), style = Type.small.ui(), color = p.inkSoft)
                // 데이터 요금: 모바일 데이터면 지금 그렇다고, 아니어도 와이파이가 아니면 요금이 나올 수 있다고
                if (!failed) Text(stringResource(if (mobile) R.string.dl_mobile else R.string.dl_data), style = Type.small.ui(), color = p.giltText)
                Box(Modifier.height(Tokens.Space.s2))
                Primary(stringResource(if (failed || job?.phase == DownloadJob.Phase.STOPPED) R.string.dl_retry else R.string.dl_go, sizeLabel(remaining))) { start() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.heightIn(min = 44.dp).pressable { close() }, contentAlignment = Alignment.CenterStart) {
                        Text(stringResource(R.string.back), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = p.ink)
                    }
                    if (ask.letter != null) Box(Modifier.heightIn(min = 44.dp).pressable { s.downloadFinished(silent = true) }, contentAlignment = Alignment.CenterEnd) {
                        Text(stringResource(R.string.dl_silent), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = p.inkSoft)
                    }
                }
            } else {
                val frac = if (job!!.total <= 0) 1f else job.done.toFloat() / job.total
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${Fetching.percent(job.done, job.total)}%", style = Type.display.copy(fontFamily = Faces.display), color = p.ink, modifier = Modifier.weight(1f))
                    // 23 / 64MB · 와이파이
                    Text("${Fetching.progress(job.done, job.total)} · ${stringResource(if (mobile) R.string.dl_cell else R.string.dl_wifi)}", style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(p.hair)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(4.dp).background(p.giltText))
                }
                if (job.phase == DownloadJob.Phase.WAITING) Text(stringResource(R.string.dl_waiting), style = Type.small.ui(), color = p.giltText)
                else Text(stringResource(R.string.dl_bg), style = Type.small.ui(), color = p.inkSoft)
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable { DownloadJob.stop() }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.dl_pause), style = Type.body.ui().copy(textDecoration = TextDecoration.Underline), color = p.ink)
                }
            }
        }
    }
}
