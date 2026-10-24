package io.github.graviton94.todayletters.data

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.github.graviton94.todayletters.core.UpdateBand
import io.github.graviton94.todayletters.core.UpdateFlow

/**
 * 새 버전 알림 (Play 인앱 업데이트, flexible, 시안 update_backup.html): Play 의 창을 저절로 띄우지 않는다.
 * 새 버전이 있으면 서재 아래 우리 띠 (core [UpdateBand]) → ‘받기’ 를 누를 때만 Play 의 확인 창 → 받는 중 % → 다 받으면 ‘다시 열기’ → [restart].
 * 띠의 상태 규칙은 core [UpdateFlow]. 알리기는 하루 한 번 (‘나중에’ · Play 창 닫기면 그날 끝, [Store.updateAskedDay]).
 * Play 로 설치한 앱에서만 된다 (그 밖에는 조용히 띠 없음).
 */
class Updates(
    private val activity: ComponentActivity,
    private val store: Store,
    private val today: () -> Long,
    private val band: () -> UpdateBand,
    private val setBand: (UpdateBand) -> Unit,
) {
    private val manager = runCatching { AppUpdateManagerFactory.create(activity) }.getOrNull()
    /** 이번에 앱을 연 동안 다 받은 띠에서 ‘나중에’ 를 눌렀다 (다음에 앱을 열면 다시). */
    private var readyDismissed = false
    private var lastTotal = 0L
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            val accepted = r.resultCode == android.app.Activity.RESULT_OK
            val failed = r.resultCode == ActivityResult.RESULT_IN_APP_UPDATE_FAILED
            if (!accepted && !failed) store.updateAskedDay = today()
            setBand(UpdateFlow.onDialog(band(), accepted, failed, lastTotal))
        }
    private val listener = InstallStateUpdatedListener { st ->
        setBand(UpdateFlow.onInstall(band(), install(st.installStatus()), st.bytesDownloaded(), st.totalBytesToDownload(), readyDismissed))
    }

    init { runCatching { manager?.registerListener(listener) } }

    /** 서재에 들어올 때 · 앱으로 돌아올 때: Play 에 물어 띠를 맞춘다 (받는 중이면 %, 다 받았으면 다시 열기, 새 버전이면 알림). */
    fun check() {
        val m = manager ?: return
        runCatching {
            m.appUpdateInfo.addOnSuccessListener { info ->
                lastTotal = info.totalBytesToDownload()
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                setBand(UpdateFlow.onInfo(band(), available, install(info.installStatus()), info.bytesDownloaded(), info.totalBytesToDownload(),
                    askedToday = store.updateAskedDay == today(), readyDismissed = readyDismissed))
            }
        }
    }

    /** ‘받기’ · ‘다시 받기’: 그때 새로 물은 정보로 Play 의 확인 창 (정보 하나는 한 번만 쓸 수 있다). */
    fun start() {
        val m = manager ?: return
        runCatching {
            m.appUpdateInfo.addOnSuccessListener { info ->
                lastTotal = info.totalBytesToDownload()
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    runCatching { m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()) }
                        .onFailure { setBand(UpdateBand.Failed) }
                } else setBand(UpdateFlow.onInfo(band(), false, install(info.installStatus()), info.bytesDownloaded(), info.totalBytesToDownload(), true, readyDismissed))
            }
        }
    }

    /** ‘나중에’: 알림 · 실패 띠는 그날 끝, 다 받은 띠는 다음에 앱을 열 때까지. */
    fun later() {
        if (band() is UpdateBand.Ready) readyDismissed = true else store.updateAskedDay = today()
        setBand(UpdateBand.Hidden)
    }

    fun restart() { runCatching { manager?.completeUpdate() } }

    fun end() { runCatching { manager?.unregisterListener(listener) } }

    private fun install(status: Int): UpdateFlow.Install = when (status) {
        InstallStatus.PENDING -> UpdateFlow.Install.PENDING
        InstallStatus.DOWNLOADING -> UpdateFlow.Install.DOWNLOADING
        InstallStatus.DOWNLOADED -> UpdateFlow.Install.DOWNLOADED
        InstallStatus.INSTALLING -> UpdateFlow.Install.INSTALLING
        InstallStatus.INSTALLED -> UpdateFlow.Install.INSTALLED
        InstallStatus.FAILED -> UpdateFlow.Install.FAILED
        InstallStatus.CANCELED -> UpdateFlow.Install.CANCELED
        else -> UpdateFlow.Install.NONE
    }

    companion object {
        fun versionCode(ctx: Context): Long = runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        }.getOrDefault(0L)
        fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }
}
