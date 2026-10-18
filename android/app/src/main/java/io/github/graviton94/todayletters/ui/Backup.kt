package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.core.BackupNudge
import io.github.graviton94.todayletters.data.Billing
import io.github.graviton94.todayletters.data.Records
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Backup
import io.github.graviton94.todayletters.design.Ink
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/**
 * 기록 보관 (시안 backup.html): 내보내기 시트 “기록을 어디에 둘까요?” 와 가져오기 전 확인 “이 기록으로 바꿀까요?”.
 * 파일 일은 data/Records, 무엇이 들어가는지는 core/Backup.
 */
@Composable
fun BackupExportSheet(summary: Backup.Summary, onSave: () -> Unit, onShare: () -> Unit, onClose: () -> Unit) {
    BackupSheet(stringResource(R.string.backup_sheet_t), summary, warn = false,
        yes = stringResource(R.string.backup_save), no = stringResource(R.string.backup_share), onYes = onSave, onNo = onShare, onClose = onClose)
}

@Composable
fun BackupImportSheet(summary: Backup.Summary, onImport: () -> Unit, onClose: () -> Unit) {
    BackupSheet(stringResource(R.string.backup_ask_t), summary, warn = true,
        yes = stringResource(R.string.backup_yes), no = stringResource(R.string.backup_no), onYes = onImport, onNo = onClose, onClose = onClose)
}

@Composable
private fun BackupSheet(title: String, summary: Backup.Summary, warn: Boolean, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit, onClose: () -> Unit) {
    val p = Ink.palette
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Text(title, style = Type.heading.ui(), color = p.ink, maxLines = 1)
            Text(stringResource(R.string.backup_summary, summary.letters, summary.cards, summary.made), style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
            if (warn) Text(stringResource(R.string.backup_ask_w), style = Type.small.ui(), color = p.giltText, maxLines = 1)
            Column(Modifier.padding(top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Primary(yes, onClick = onYes)
                Secondary(no, onClick = onNo)
            }
        }
    }
}

/**
 * 내보내기 시트와 그 뒤의 일 (저장 창 · 공유 창). 저장 창의 결과를 받으려면 늘 그려 두고 [open] 으로 시트만 켠다.
 * 설정 › 기록 보관과 오늘 화면의 보관 한 줄이 같이 쓴다.
 */
@Composable
fun BackupExportFlow(s: AppState, open: Boolean, onClose: () -> Unit) {
    val ctx = s.ctx
    val activity = LocalContext.current.let { Billing.activityOf(it) }
    fun toast(id: Int) = android.widget.Toast.makeText(ctx, ctx.getString(id), android.widget.Toast.LENGTH_SHORT).show()
    // 내보낸 날 · 그때 마친 편지 수 (보관 한 줄이 다시 센다)
    fun exported() { s.store.backupDay = s.today; s.store.backupLetters = Records.summary(s.store, s.today).letters; s.bump() }
    fun name() = Backup.fileName(ctx.getString(R.string.backup_file), Records.made(s.today))
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            if (Records.write(ctx, uri, Records.export(s.store, s.today))) { exported(); toast(R.string.backup_saved) } else toast(R.string.backup_bad)
        }
    }
    if (!open) return
    BackupExportSheet(Records.summary(s.store, s.today),
        onSave = { onClose(); runCatching { save.launch(name()) } },
        onShare = {
            onClose()
            Records.shareIntent(ctx, Records.export(s.store, s.today), name(), ctx.getString(R.string.backup_export))?.let { i ->
                runCatching { if (activity != null) activity.startActivity(i) else ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onSuccess { exported() }
            }
        },
        onClose = onClose)
}

/**
 * 오늘 화면의 보관 한 줄 (core [BackupNudge]): “보관 뒤 편지 N통 · 기록 보관하기 › · 나중에”. 알림은 보내지 않는다.
 * ‘기록 보관하기’ 는 설정과 같은 시트, ‘나중에’ 는 14일 미룸.
 */
@Composable
fun BackupNudgeLine(s: AppState, onBackup: () -> Unit) {
    val p = Ink.palette
    s.version
    // 예전부터 쓰던 사람: 이미 한 장을 마쳤으면 오늘을 그날로 (내일부터 한 줄)
    LaunchedEffect(Unit) { if (s.store.backupChapterDay < 0 && s.anyChapterDone()) { s.store.backupChapterDay = s.today; s.bump() } }
    val line = remember(s.version, s.today) {
        BackupNudge.line(s.today, s.store.backupDay, s.store.backupLetters, Records.summary(s.store, s.today).letters, s.store.backupChapterDay, s.store.backupSnooze)
    } ?: return
    val small = Type.small.ui()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (line.since) R.string.nudge_since else R.string.nudge_first, line.letters) + " · ", style = small, color = p.inkSoft, maxLines = 1)
        Box(Modifier.heightIn(min = 48.dp).pressable(onClick = onBackup), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nudge_go), style = small, color = p.giltText, maxLines = 1)
        }
        Text(" · ", style = small, color = p.inkSoft, maxLines = 1)
        Box(Modifier.heightIn(min = 48.dp).pressable(haptic = false) { s.store.backupSnooze = BackupNudge.snooze(s.today); s.bump() }, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.nudge_later), style = small, color = p.inkSoft, maxLines = 1)
        }
    }
}
