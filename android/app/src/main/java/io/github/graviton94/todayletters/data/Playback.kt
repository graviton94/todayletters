package io.github.graviton94.todayletters.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.graviton94.todayletters.R
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 편지 낭독을 이어서 들려주는 동안의 상태. 편지 방이 낭독을 시작하면 [start], 끝나거나 방을 나가면 [end].
 * 그동안 [NarrationService] 가 알림창 · 잠금 화면 · 이어폰 단추에 재생/멈춤 · 이전/다음 문장을 걸어 두어서,
 * 앱을 벗어나도 낭독이 이어지고 어디서든 멈출 수 있다. 짧은 소리(낱말 발음 등)는 여기에 오지 않는다.
 *
 * 최근 앱에서 앱을 밀어 닫으면 화면(편지 방)은 사라지지만 낭독은 이어진다: 방이 사라지기 직전에 남은 문장을 [handOff] 로 넘기고,
 * 그 뒤로는 여기(앱 프로세스)가 이 편지 끝까지 읽는다. 알림의 ✕ 로 끈다.
 */
object Playback {
    /** 알림에 보일 것: 제목(날짜 · 장소), 부제(누가 누구에게 · 장), 그림(assets 경로), 몇 번째 문장 / 몇 문장. */
    data class Now(val title: String, val subtitle: String, val art: String?, val index: Int, val count: Int)

    /** 단추가 눌렸을 때 편지 방이 할 일. */
    interface Controls {
        fun next()
        fun prev()
        fun stop()
    }

    var now by mutableStateOf<Now?>(null)
        private set
    /** 사용자가 (또는 전화 · 다른 앱 소리 때문에) 멈춰 둔 상태. 편지 방은 다음 문장으로 가기 전에 풀리기를 기다린다. */
    var paused by mutableStateOf(false)
        private set
    /** 화면 없이 (앱을 밀어 닫은 뒤) 이 편지 끝까지 여기서 읽는 중. */
    var detached = false
        private set
    private var controls: Controls? = null
    private var narrator: Narrator? = null
    private var listener: (() -> Unit)? = null
    /** 다시 재생하기 전에 소리 차례(오디오 포커스)를 다시 받는다 (다른 앱이 음악을 틀어 멈췄던 경우). 못 받으면 false. 서비스가 건다. */
    internal var focus: (() -> Boolean)? = null
    private val main = kotlinx.coroutines.MainScope()
    private var tail: kotlinx.coroutines.Job? = null

    fun start(ctx: Context, narrator: Narrator, now: Now, controls: Controls) {
        // 밀어 닫은 뒤 이어 읽던 것이 있으면 새 방이 가져간다
        if (detached) { tail?.cancel(); tail = null; detached = false; this.narrator?.takeIf { it !== narrator }?.stop() }
        this.narrator = narrator; this.controls = controls; this.now = now; paused = false
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, NarrationService::class.java)) }
        listener?.invoke()
    }

    fun update(index: Int) {
        val n = now ?: return
        if (n.index != index) { now = n.copy(index = index); listener?.invoke() }
    }

    fun end(ctx: Context) {
        if (now == null) return
        val wasDetached = detached
        now = null; controls = null; paused = false; detached = false
        tail?.cancel(); tail = null
        if (wasDetached) narrator?.stop()
        runCatching { ctx.stopService(Intent(ctx, NarrationService::class.java)) }
    }

    fun pause() { if (now == null) return; paused = true; narrator?.pause(); listener?.invoke() }
    fun play() {
        if (now == null) return
        if (focus?.invoke() == false) return
        paused = false; narrator?.resume(); listener?.invoke()
    }
    fun toggle() = if (paused) play() else pause()
    fun next() { if (paused) play(); controls?.next() }
    fun prev() { if (paused) play(); controls?.prev() }
    fun stop() { controls?.stop() }

    internal fun listen(l: (() -> Unit)?) { listener = l }

    /**
     * 화면이 사라질 때 (최근 앱에서 밀어 닫음): [files] 의 [at] 번째 문장부터 끝까지 여기서 이어 읽는다.
     * 지금 문장이 소리 나는 중이면 끊지 않고 끝나기를 기다린 뒤 다음으로. [heard] 는 들은 문장 (끝까지 들으면 -1).
     * 다른 소리가 이 재생기를 가져가면 (앱을 다시 열어 낱말을 들음 등) 거기서 멈춘다.
     */
    fun handOff(ctx: Context, files: List<String>, at: Int, speed: Float?, heard: ((Int) -> Unit)?) {
        val n = narrator ?: return
        if (now == null || detached || at !in files.indices) return
        val app = ctx.applicationContext
        detached = true
        val jump = intArrayOf(0)
        controls = object : Controls {
            override fun next() { jump[0] = 1; n.stop() }
            override fun prev() { jump[0] = if (n.position() > 1500) 2 else -1; n.stop() }
            override fun stop() { end(app) }
        }
        // 지금 문장: 소리 나는 중이면 (멈춤 포함) 끝나기를 기다린다
        val current = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val playing = n.now == files[at]
        if (playing) n.follow { ok -> current.complete(ok) }
        tail = main.launch {
            // 지금 문장이 끝까지 들렸는가 (멈춤 · 다른 소리면 false: 단추로 넘긴 것이 아니면 거기서 그만)
            var completed: Boolean
            var k = at
            var first = playing
            var finished = false
            while (true) {
                if (k >= files.size) { finished = true; break }
                if (!first) {
                    snapshotFlow { paused }.first { !it }
                    update(k); heard?.invoke(k)
                    jump[0] = 0
                    completed = suspendCancellableCoroutine<Boolean> { c ->
                        n.playThen(files[k], speed, owner = null) { ok -> if (c.isActive) c.resume(ok) }
                    }
                } else completed = current.await()
                first = false
                // 다른 소리가 재생기를 가져갔다 · 앱에서 멈췄다 → 여기서 그만 (그 소리는 그대로, 앞 문장으로 되돌아가 다시 읽지 않는다)
                if (n.now != null) break
                k = io.github.graviton94.todayletters.core.Voice.next(k, completed, jump[0]) ?: break
                jump[0] = 0
            }
            if (finished) heard?.invoke(-1)
            tail = null
            if (detached) { detached = false; end(app) }
        }
    }
}

/**
 * 낭독 재생 서비스 (미디어 재생 포그라운드 서비스). 알림 · 미디어 세션 · 오디오 포커스 · 이어폰 뽑힘을 맡는다.
 * 소리 자체는 앱의 [Narrator] 가 내고, 여기서는 [Playback] 을 통해 멈춤 · 넘김만 전한다.
 */
class NarrationService : Service() {
    private lateinit var session: MediaSessionCompat
    private val am by lazy { getSystemService(AudioManager::class.java) }
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var focus: AudioFocusRequest? = null
    /** 전화 · 알림음 · 길 안내처럼 잠깐 소리 차례를 빼앗겨 멈춘 것: 돌려받으면 저절로 이어서. */
    private var resumeOnGain = false
    /** 지금 포그라운드(알림을 밀어 지울 수 없음)인가. 사용자가 멈춰 두면 내려와서 알림은 남기되 지울 수 있게 한다. */
    private var foreground = false
    private var art: Pair<String, Bitmap?>? = null

    private val noisy = object : BroadcastReceiver() {
        // 이어폰이 뽑힘: 멈춤 (알림은 남김, 저절로 다시 틀지 않음)
        override fun onReceive(c: Context?, i: Intent?) { if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) { resumeOnGain = false; Playback.pause() } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "narration").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumeOnGain = false; Playback.play() }
                override fun onPause() { resumeOnGain = false; Playback.pause() }
                override fun onSkipToNext() = Playback.next()
                override fun onSkipToPrevious() = Playback.prev()
                override fun onStop() = Playback.stop()
            })
            setSessionActivity(openApp())
            isActive = true
        }
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            // 말소리라 줄여서 겹쳐 듣기보다 잠깐 멈추는 게 낫다 (돌려받으면 이어서)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    // 잠깐 (전화 · 알림음 · 길 안내 음성): 멈췄다가 끝나면 저절로 이어서
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        if (!Playback.paused) { resumeOnGain = true; Playback.pause() }
                    // 다른 앱이 음악 · 영상을 틀었다: 멈춤, 알림과 세션은 남김. ▶ 를 누르면 그 문장부터 (소리 차례를 다시 받고)
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; Playback.pause() }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) { resumeOnGain = false; Playback.play() }
                }
            }.build()
        focus = req
        am.requestAudioFocus(req)
        Playback.focus = { am.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_FAILED }
        Playback.listen { refresh() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> { resumeOnGain = false; Playback.toggle() }
            ACT_NEXT -> Playback.next()
            ACT_PREV -> Playback.prev()
            ACT_STOP -> Playback.stop()
        }
        if (Playback.now == null) {
            // 낭독이 끝났거나 (앱이 다시 켜져) 기억이 없다: 알림을 걷고 끝낸다.
            // startForegroundService 로 켜졌다면 약속대로 한 번은 앞에 서야 한다 (못 서도 조용히).
            if (intent?.action == null) runCatching { goForeground(notification()) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            nm.cancel(ID)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == null) goForeground(notification()) else refresh()
        return START_NOT_STICKY
    }

    /** 최근 앱에서 앱을 밀어 닫음: 낭독은 계속 (편지 방이 남은 문장을 [Playback.handOff] 로 넘겼다). ✕ 로 끈다. 낭독이 없으면 끝. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Playback.now == null) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Playback.listen(null)
        Playback.focus = null
        focus?.let { am.abandonAudioFocusRequest(it) }
        runCatching { unregisterReceiver(noisy) }
        session.isActive = false
        session.release()
        // 멈춰 둔 채 (포그라운드에서 내려온) 알림은 서비스가 끝나도 남는다: 낭독도 끝났으면 걷는다
        if (Playback.now == null) runCatching { nm.cancel(ID) }
        super.onDestroy()
    }

    private fun goForeground(n: Notification) {
        foreground = runCatching {
            ServiceCompat.startForeground(this, ID, n, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            true
        }.getOrElse { runCatching { nm.notify(ID, n) }; false }
    }

    /**
     * 알림을 지금 상태로. 재생 중이면 포그라운드 (지울 수 없는 알림). 사용자가 멈췄거나 다른 앱이 소리를 가져가 멈추면
     * 포그라운드에서 내려와 알림만 남긴다 (지울 수 있고, ▶ 로 그 문장부터). 잠깐 멈춤 (전화 등) 은 그대로 앞에 둔다.
     */
    private fun refresh() {
        if (Playback.now == null) return
        val n = notification()
        if (Playback.paused && !resumeOnGain) {
            if (foreground) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH); foreground = false }
            runCatching { nm.notify(ID, n) }
        } else if (!foreground) goForeground(n)
        else runCatching { nm.notify(ID, n) }
    }

    private fun bitmap(path: String?): Bitmap? {
        if (path == null) return null
        art?.let { if (it.first == path) return it.second }
        val b = runCatching {
            AssetFiles.open(this, path).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }
        }.getOrNull()
        art = path to b
        return b
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.narration_channel), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        val now = Playback.now
        val playing = !Playback.paused
        val pic = bitmap(now?.art)
        session.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, now?.title.orEmpty())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, now?.subtitle.orEmpty())
            .apply { if (pic != null) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, pic) }
            .build())
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP)
            .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notice)
            .setContentTitle(now?.title)
            .setContentText(now?.subtitle)
            .setLargeIcon(pic)
            .setContentIntent(openApp())
            .setDeleteIntent(action(ACT_STOP))
            .setOngoing(playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 미디어 알림: 바로 보이게 (안드로이드 12+ 는 포그라운드 알림을 10초까지 미룰 수 있다 — 짧은 한 문장 낭독은 그 사이에 끝난다)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.narration_prev), action(ACT_PREV))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (playing) R.string.narration_pause else R.string.narration_play), action(ACT_TOGGLE))
            .addAction(android.R.drawable.ic_media_next, getString(R.string.narration_next), action(ACT_NEXT))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun action(a: String) = PendingIntent.getService(this, a.hashCode(), Intent(this, NarrationService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(this, 1,
        (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val ID = 7301
        const val CHANNEL = "narration"
        const val ACT_TOGGLE = "narration.toggle"
        const val ACT_NEXT = "narration.next"
        const val ACT_PREV = "narration.prev"
        const val ACT_STOP = "narration.stop"
    }
}
