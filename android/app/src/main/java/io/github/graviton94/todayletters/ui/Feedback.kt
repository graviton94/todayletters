package io.github.graviton94.todayletters.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Feedback
import io.github.graviton94.todayletters.data.CrashLog
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** 기기 정보 한 줄 (의견 보내기의 미리 보기와 메일 본문에 같은 글자). */
fun feedbackDevice(s: AppState): Feedback.Device = Feedback.Device(
    android = Build.VERSION.RELEASE.orEmpty(),
    model = Build.MODEL.orEmpty(),
    app = CrashLog.versionName(s.ctx),
    learn = s.current.takeIf { it.isNotEmpty() }?.let { s.seriesSettings(it).learn.code }.orEmpty(),
    crashes = CrashLog.count(s.ctx),
)

fun deviceLine(ctx: Context, d: Feedback.Device) = ctx.getString(R.string.fb_device, d.android, d.model, d.app, d.learn, d.crashes)

/**
 * 메일 앱 열기 (ACTION_SENDTO mailto): 받는 사람 · 제목 · 본문이 채워져 있고, 보내기는 사용자가 직접.
 * 메일 앱이 없으면 false.
 */
fun openMail(ctx: Context, subject: String, body: String): Boolean {
    val uri = Uri.parse("mailto:${Feedback.TO}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
    val i = Intent(Intent.ACTION_SENDTO, uri)
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(Feedback.TO))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { ctx.startActivity(i) }.isSuccess
}

/**
 * 의견 보내기 (설정 › 의견 보내기, 멈춘 뒤 ‘보내기’): 오류 · 제안 · 기타, 쓰는 칸, ‘기기 정보와 최근 오류 기록 함께 보내기’ (미리 보기 한 줄),
 * 메일 앱으로 보내기. 외부 서비스 없이 사용자의 메일로만.
 */
@Composable
fun FeedbackSheet(s: AppState, ask: AppState.FeedbackAsk) {
    val p = Ink.palette
    val ctx = LocalContext.current
    var kind by remember(ask) { mutableStateOf(ask.kind) }
    var text by remember(ask) { mutableStateOf("") }
    var attach by remember(ask) { mutableStateOf(true) }
    var noMail by remember(ask) { mutableStateOf(false) }
    val device = remember(ask) { feedbackDevice(s) }
    val line = deviceLine(ctx, device)
    val kinds = listOf(Feedback.Kind.BUG to stringResource(R.string.fb_bug), Feedback.Kind.IDEA to stringResource(R.string.fb_idea), Feedback.Kind.OTHER to stringResource(R.string.fb_other))
    fun close() { s.feedback = null }
    fun send() {
        val label = kinds.first { it.first == kind }.second
        val crash = if (attach) CrashLog.last(ctx) else null
        val body = Feedback.body(text, if (attach) line else null, crash, ctx.getString(R.string.fb_crash_head))
        if (openMail(ctx, Feedback.subject(ctx.getString(R.string.app_name).replace("⁠", ""), label, device.app), body)) {
            if (crash != null) CrashLog.clear(ctx)
            close()
        } else noMail = true
    }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false) { close() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding().imePadding()
                // 키보드가 올라와 자리가 모자라면 시트 안이 스크롤된다 (쓰는 칸이 화면 밖으로 밀리지 않게)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = Tokens.Space.s6, vertical = Tokens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Text(stringResource(R.string.fb_title), style = Type.heading.ui(), color = p.ink)
            // 오류 · 제안 · 기타
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                kinds.forEach { (k, label) ->
                    val on = k == kind
                    Box(Modifier.heightIn(min = 40.dp).border(1.dp, if (on) p.ink else p.line).background(if (on) p.ink else androidx.compose.ui.graphics.Color.Transparent)
                        .semantics { selected = on }.pressable(role = Role.RadioButton) { kind = k }.padding(horizontal = Tokens.Space.s4),
                        contentAlignment = Alignment.Center) {
                        Text(label, style = Type.small.ui(), color = if (on) p.paper else p.ink)
                    }
                }
            }
            // 쓰는 칸
            Box(Modifier.fillMaxWidth().heightIn(min = 112.dp).border(Tokens.Stroke.hair, p.line).padding(Tokens.Space.s3)) {
                if (text.isEmpty()) Text(stringResource(R.string.fb_hint), style = Type.body.ui(), color = p.hideInk)
                BasicTextField(text, { text = it.take(4000) }, textStyle = Type.body.ui().copy(color = p.ink), cursorBrush = SolidColor(p.ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp))
            }
            // 기기 정보와 최근 오류 기록 함께 보내기 (미리 보기 한 줄)
            Row(Modifier.fillMaxWidth().semantics { toggleableState = ToggleableState(attach) }.pressable(role = Role.Checkbox, haptic = false) { attach = !attach }
                .heightIn(min = 48.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.padding(top = 2.dp).size(20.dp).border(1.5.dp, p.ink).background(if (attach) p.ink else androidx.compose.ui.graphics.Color.Transparent),
                    contentAlignment = Alignment.Center) {
                    if (attach) Text("✓", style = Type.small, color = p.paper)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.fb_attach), style = Type.body.ui(), color = p.ink)
                    Text(line, style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft)
                }
            }
            if (noMail) Text(stringResource(R.string.fb_no_mail, Feedback.TO), style = Type.small.ui(), color = p.wrong)
            Primary(stringResource(R.string.fb_send)) { send() }
            Text("${stringResource(R.string.fb_note)}\n${Feedback.TO}", style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = p.inkSoft,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 지난번에 앱이 멈췄으면 다음에 열 때 한 번: 보내기 (의견 보내기, 오류 · 함께 보내기) · 괜찮아요. */
@Composable
fun CrashAsk(s: AppState) {
    if (!s.crashAsk) return
    BackHandler { s.answerCrash(send = false) }
    Ask(stringResource(R.string.crash_t), stringResource(R.string.crash_send), stringResource(R.string.crash_no),
        onYes = { s.answerCrash(send = true) }, onNo = { s.answerCrash(send = false) }, body = stringResource(R.string.crash_b))
}
