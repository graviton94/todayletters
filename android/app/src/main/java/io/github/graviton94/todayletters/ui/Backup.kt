package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
