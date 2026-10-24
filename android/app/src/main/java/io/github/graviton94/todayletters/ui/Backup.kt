package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
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
 * 기록 보관 (시안 backup.html → update_backup.html): 내보내기 “기록을 어디에 둘까요?” (Drive · 휴대폰, 방법마다 무엇을 누르는지),
 * 가져오기 “어디에 둔 기록을 가져올까요?” (둘 다 파일 고르는 창), 가져오기 전 확인 “이 기록으로 바꿀까요?” (파일 · 지금 폰 나란히).
 * 파일 일은 data/Records, 무엇이 들어가는지는 core/Backup. [file] 은 파일 이름 앞부분 (backup_file).
 */
@Composable
fun BackupExportSheet(summary: Backup.Summary, file: String, onDrive: () -> Unit, onPhone: () -> Unit, onClose: () -> Unit) {
    BackupSheet(stringResource(R.string.backup_sheet_t), onClose,
        sub = stringResource(R.string.backup_summary, summary.letters, summary.cards, summary.made),
        note = stringResource(R.string.backup_note) + " " + stringResource(R.string.backup_note_copy)) {
        BackupOption(stringResource(R.string.backup_drive_t), stringResource(R.string.backup_drive_tag), lead = true, onClick = onDrive,
            steps = listOf(stringResource(R.string.backup_drive_1), stringResource(R.string.backup_drive_2)))
        BackupOption(stringResource(R.string.backup_save), stringResource(R.string.backup_phone_tag), onClick = onPhone,
            line = stringResource(R.string.backup_phone_b, file))
    }
}

@Composable
fun BackupImportChooser(file: String, onPick: () -> Unit, onClose: () -> Unit) {
    BackupSheet(stringResource(R.string.backup_in_t), onClose, note = stringResource(R.string.backup_in_note)) {
        BackupOption(stringResource(R.string.backup_in_drive_t), stringResource(R.string.backup_in_drive_tag), lead = true, onClick = onPick,
            steps = listOf(stringResource(R.string.backup_in_drive_1), stringResource(R.string.backup_in_drive_2, file)))
        BackupOption(stringResource(R.string.backup_in_phone_t), null, onClick = onPick, line = stringResource(R.string.backup_in_phone_b, file))
    }
}

/** 가져오기 전 확인: [file] 파일의 기록 · [here] 지금 폰의 기록. */
@Composable
fun BackupImportSheet(file: Backup.Summary, here: Backup.Summary, onImport: () -> Unit, onClose: () -> Unit) {
    BackupSheet(stringResource(R.string.backup_ask_t), onClose) {
        BackupOption(stringResource(R.string.backup_ask_file), null, line = stringResource(R.string.backup_ask_file_b, file.letters, file.cards, file.made))
        BackupOption(stringResource(R.string.backup_ask_here), null, line = stringResource(R.string.backup_ask_here_b, here.letters, here.cards))
        Text(stringResource(R.string.backup_ask_w), style = Type.small.ui(), color = Ink.palette.giltText, maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Secondary(stringResource(R.string.backup_no), Modifier.weight(1f), small = true, onClick = onClose)
            Primary(stringResource(R.string.backup_yes), Modifier.weight(1f), small = true, onClick = onImport)
        }
    }
}

@Composable
private fun BackupSheet(title: String, onClose: () -> Unit, sub: String? = null, note: String? = null, body: @Composable () -> Unit) {
    val p = Ink.palette
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(p.scrim).pressable(haptic = false, onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(p.paper).pressable(haptic = false) { }.navigationBarsPadding().padding(Tokens.Space.s5),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(title, style = Type.heading.ui(), color = p.ink, maxLines = 1)
                if (sub != null) Text(sub, style = Type.small.ui(), color = p.inkSoft, maxLines = 1)
            }
            body()
            if (note != null) Column {
                Box(Modifier.fillMaxWidth().height(Tokens.Stroke.hair).background(p.line))
                Text(note, style = Type.small.ui(), color = p.inkSoft, modifier = Modifier.padding(top = Tokens.Space.s2))
            }
        }
    }
}

/** 방법 카드: 이름 + 오른쪽 작은 금빛 표 + 할 일 (번호 줄) 또는 한 줄. [lead] 는 먼저 권하는 것 (금테). */
@Composable
private fun BackupOption(title: String, tag: String?, lead: Boolean = false, steps: List<String> = emptyList(), line: String? = null, onClick: (() -> Unit)? = null) {
    val p = Ink.palette
    val small = Type.small.ui()
    Column(
        Modifier.fillMaxWidth().border(Tokens.Stroke.hair, if (lead) p.giltText else p.line)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.body.ui(), color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
            if (tag != null) Text(tag, style = small.copy(fontSize = Tokens.Text.caps), color = p.giltText, maxLines = 1, modifier = Modifier.padding(start = Tokens.Space.s2))
        }
        steps.forEachIndexed { i, t ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text("${i + 1}.", style = small.copy(fontFeatureSettings = "tnum"), color = p.inkSoft)
                Text(t, style = small, color = p.inkSoft)
            }
        }
        if (line != null) Text(line, style = small, color = p.inkSoft)
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
    BackupExportSheet(Records.summary(s.store, s.today), ctx.getString(R.string.backup_file),
        onPhone = { onClose(); runCatching { save.launch(name()) } },
        // Drive 는 공유 창에서 고른다 (로그인 · 권한 없이, Drive 의 ‘저장’ 창이 폴더를 묻는다)
        onDrive = {
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
