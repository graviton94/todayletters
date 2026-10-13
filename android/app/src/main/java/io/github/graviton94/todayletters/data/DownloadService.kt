package io.github.graviton94.todayletters.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.core.Fetching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 낭독 미리 받기 (받기 화면에서 사용자가 직접 시작): 받는 일은 앱 프로세스에서 하고, [DownloadService] 가 그동안 포그라운드로
 * 알림(진행 막대 · 멈추기)을 걸어 둔다. 그래서 앱을 닫아도 이어서 받고, 끝나면 “받기 끝” 알림 한 번.
 * 받기 화면과 알림 어디서든 [stop]. 받다 만 파일은 남겨서 다시 받으면 받은 데부터 ([Downloads.fetch]).
 * 인터넷이 끊기면 잠시 기다렸다가 이어 받는다 ([Fetching.backoff]). 화면은 [state] 를 본다.
 */
object DownloadJob {
    enum class Phase {
        RUNNING,
        /** 인터넷을 기다리는 중 (끊겼거나 잠깐 실패). */
        WAITING,
        DONE,
        /** 사용자가 멈춤. */
        STOPPED,
        /** 인터넷은 있는데 잇따라 실패. */
        FAILED,
        /** 시스템이 받기 시간을 끝냄 (Android 15 의 데이터 동기화 시간 제한): “멈췄어요, 다시 받기”. */
        TIMED_OUT,
    }
    data class State(val series: String, val chapter: String, val name: String, val done: Long, val total: Long, val phase: Phase) {
        val active get() = phase == Phase.RUNNING || phase == Phase.WAITING
    }

    var state by mutableStateOf<State?>(null)
        private set
    /** 받기 화면이 이 받기를 보고 있는가 (보고 있으면 끝 알림 대신 화면이 알린다). */
    var watching: Pair<String, String>? = null
    val active get() = state?.active == true

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var listener: (() -> Unit)? = null
    internal fun listen(l: (() -> Unit)?) { listener = l }

    private fun set(st: State?) {
        val apply = { state = st; listener?.invoke() }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else main.post { apply() }
    }

    /**
     * 받기 시작 (받기 화면의 단추에서만: 앱이 화면에 있을 때 사용자가 누른 것). 포그라운드 서비스를 켜지 못하면
     * (Android 12+ 의 제한 등) 알림 없이 앱이 열려 있는 동안 받는다.
     */
    fun start(ctx: Context, pack: Downloads.Pack, name: String) {
        val app = ctx.applicationContext
        state?.let { if (it.active && it.series == pack.series && it.chapter == pack.chapter) return }
        job?.cancel()
        val downloads = Downloads(app)
        set(State(pack.series, pack.chapter, name, pack.bytes - downloads.remaining(pack), pack.bytes, Phase.RUNNING))
        job = scope.launch { run(app, downloads, pack) }
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+) · SecurityException: 앱이 열려 있는 동안만 받는다
        }
    }

    private suspend fun run(app: Context, downloads: Downloads, pack: Downloads.Pack) {
        var failures = 0
        var waits = 0
        var last = 0L
        while (true) {
            try {
                var seen = -1L
                downloads.fetch(pack) { d, t ->
                    // 받는 게 늘면 잇따른 실패는 처음부터 센다
                    if (seen in 0 until d) failures = 0
                    seen = d
                    val now = System.currentTimeMillis()
                    // 화면 · 알림은 0.25초에 한 번만
                    if (now - last >= 250 || d >= t) {
                        last = now
                        state?.let { set(it.copy(done = d, total = t, phase = Phase.RUNNING)) }
                    }
                }
                state?.let { set(it.copy(done = it.total, phase = Phase.DONE)) }
                finished(app)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val st = state ?: return
                if (!downloads.online()) {
                    // 인터넷이 끊김: 이어질 때까지 기다렸다가 받은 데부터 (이건 실패로 세지 않는다)
                    set(st.copy(phase = Phase.WAITING))
                    while (!downloads.online()) delay(Fetching.backoff(waits++))
                    waits = 0
                } else {
                    failures++
                    if (failures >= Fetching.MAX_FAILURES) { set(st.copy(phase = Phase.FAILED)); return }
                    set(st.copy(phase = Phase.WAITING))
                    delay(Fetching.backoff(failures - 1))
                }
            }
        }
    }

    /** 멈추기 (받기 화면 · 알림). 받다 만 것은 남는다. */
    fun stop() {
        job?.cancel(); job = null
        state?.takeIf { it.active }?.let { set(it.copy(phase = Phase.STOPPED)) }
    }

    /** 시스템이 받기 시간을 끝냈다 (서비스의 onTimeout): 멈추고 “멈췄어요, 다시 받기” 알림. 다시 받기는 앱에서 사용자가. */
    internal fun timedOut(ctx: Context) {
        job?.cancel(); job = null
        val st = state?.takeIf { it.active } ?: return
        set(st.copy(phase = Phase.TIMED_OUT))
        notifyEnd(ctx, ctx.getString(R.string.dl_note_paused), ctx.getString(R.string.dl_note_paused_b))
    }

    /** 화면 사진 · 시험용: 받는 중인 모습을 그대로 놓는다. */
    @androidx.annotation.VisibleForTesting
    internal fun show(st: State?) { job?.cancel(); job = null; set(st) }

    /** 끝난 받기를 잊는다 (받기 화면이 결과를 받아 갔을 때). */
    fun forget() { if (state?.active != true) set(null) }

    private fun finished(app: Context) {
        val st = state ?: return
        if (watching == st.series to st.chapter) return
        notifyEnd(app, app.getString(R.string.dl_note_done), app.getString(R.string.dl_note_done_b, st.name))
    }

    private fun notifyEnd(ctx: Context, title: String, text: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        DownloadService.channel(ctx, nm)
        val n = NotificationCompat.Builder(ctx, DownloadService.CHANNEL)
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(DownloadService.openApp(ctx))
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        runCatching { nm.notify(DownloadService.ID_END, n) }
    }
}

/**
 * 미리 받기의 포그라운드 서비스 (데이터 동기화). 받는 동안 진행 막대와 ‘멈추기’ 가 있는 알림을 걸어 두고,
 * 받기가 끝나거나 멈추면 내려온다. Android 15 의 데이터 동기화 시간 제한에 걸리면 ([onTimeout]) 깨끗이 멈춘다.
 */
class DownloadService : Service() {
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var lastPost = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadJob.listen { refresh() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACT_STOP) {
            DownloadJob.stop()
            done()
            return START_NOT_STICKY
        }
        // startForegroundService 로 켜졌다: 5초 안에 앞에 선다 (못 서면 알림 없이 앱 안에서만 이어 받는다)
        runCatching {
            ServiceCompat.startForeground(this, ID, progress(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        }
        if (!DownloadJob.active) done()
        return START_NOT_STICKY
    }

    /** Android 15+: 데이터 동기화 서비스가 하루 쓸 수 있는 시간을 다 썼다. 몇 초 안에 멈춰야 한다. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        DownloadJob.timedOut(this)
        done()
    }

    /** 최근 앱에서 앱을 닫아도 받기는 이어진다 (아무것도 하지 않음). */
    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }

    override fun onDestroy() {
        DownloadJob.listen(null)
        super.onDestroy()
    }

    private fun refresh() {
        if (!DownloadJob.active) { done(); return }
        val now = System.currentTimeMillis()
        if (now - lastPost < 1000) return
        lastPost = now
        runCatching { nm.notify(ID, progress()) }
    }

    private fun done() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runCatching { nm.cancel(ID) }
        stopSelf()
    }

    private fun progress(): Notification {
        channel(this, nm)
        val st = DownloadJob.state
        val waiting = st?.phase == DownloadJob.Phase.WAITING
        val stop = PendingIntent.getService(this, 2, Intent(this, DownloadService::class.java).setAction(ACT_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(getString(if (waiting) R.string.dl_note_wait else R.string.dl_note_t))
            // 빈센트 · 23 / 64MB
            .setContentText(st?.let { "${it.name} · ${Fetching.progress(it.done, it.total)}" })
            .setProgress(100, st?.let { Fetching.percent(it.done, it.total) } ?: 0, st == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(this))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.dl_pause), stop)
            .build()
    }

    companion object {
        const val ID = 7302
        const val ID_END = 7303
        const val CHANNEL = "downloads"
        const val ACT_STOP = "download.stop"

        fun channel(ctx: Context, nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.dl_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }

        fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 3,
            (ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
