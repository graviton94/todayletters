package io.github.graviton94.todayletters.data

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * 새 버전 알림 (Play 인앱 업데이트, flexible): Play 가 새 버전을 알면 Play 의 확인 창을 띄우고, 받는 것은 Play 가 뒤에서.
 * 다 받으면 [onReady] → 서재 아래 띠 ‘다시 열기’ → [restart]. 묻기는 서재에서 하루 한 번 (‘나중에’ 로 닫으면 그날 끝).
 * Play 로 설치한 앱에서만 된다 (그 밖에는 조용히 아무것도 안 함).
 */
class Updates(private val activity: ComponentActivity, private val onReady: () -> Unit) {
    private val manager = runCatching { AppUpdateManagerFactory.create(activity) }.getOrNull()
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    private val listener = InstallStateUpdatedListener { st -> if (st.installStatus() == InstallStatus.DOWNLOADED) onReady() }

    init { runCatching { manager?.registerListener(listener) } }

    /** 다 받아 둔 것이 있으면 띠를 다시 띄운다 (앱을 다시 열었을 때). */
    fun resume() {
        runCatching { manager?.appUpdateInfo?.addOnSuccessListener { if (it.installStatus() == InstallStatus.DOWNLOADED) onReady() } }
    }

    /** 서재에서: 오늘 아직 안 물었고 새 버전이 있으면 Play 확인 창. */
    fun ask(store: Store, today: Long) {
        val m = manager ?: return
        if (store.updateAskedDay == today) return
        runCatching {
            m.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    store.updateAskedDay = today
                    runCatching { m.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()) }
                }
            }
        }
    }

    fun restart() { runCatching { manager?.completeUpdate() } }

    fun end() { runCatching { manager?.unregisterListener(listener) } }

    companion object {
        fun versionCode(ctx: Context): Long = runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        }.getOrDefault(0L)
        fun versionName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault("")
    }
}
