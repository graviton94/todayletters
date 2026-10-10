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

/**
 * 편지 낭독을 이어서 들려주는 동안의 상태. 편지 방이 낭독을 시작하면 [start], 끝나거나 방을 나가면 [end].
 * 그동안 [NarrationService] 가 알림창 · 잠금 화면 · 이어폰 단추에 재생/멈춤 · 이전/다음 문장을 걸어 두어서,
 * 앱을 벗어나도 낭독이 이어지고 어디서든 멈출 수 있다. 짧은 소리(낱말 발음 등)는 여기에 오지 않는다.
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
    private var controls: Controls? = null
    private var narrator: Narrator? = null
    private var listener: (() -> Unit)? = null

    fun start(ctx: Context, narrator: Narrator, now: Now, controls: Controls) {
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
        now = null; controls = null; paused = false
        runCatching { ctx.stopService(Intent(ctx, NarrationService::class.java)) }
    }

    fun pause() { if (now == null) return; paused = true; narrator?.pause(); listener?.invoke() }
    fun play() { if (now == null) return; paused = false; narrator?.resume(); listener?.invoke() }
    fun toggle() = if (paused) play() else pause()
    fun next() { if (paused) play(); controls?.next() }
    fun prev() { if (paused) play(); controls?.prev() }
    fun stop() { controls?.stop() }

    internal fun listen(l: (() -> Unit)?) { listener = l }
}

/**
 * 낭독 재생 서비스 (미디어 재생 포그라운드 서비스). 알림 · 미디어 세션 · 오디오 포커스 · 이어폰 뽑힘을 맡는다.
 * 소리 자체는 앱의 [Narrator] 가 내고, 여기서는 [Playback] 을 통해 멈춤 · 넘김만 전한다.
 */
class NarrationService : Service() {
    private lateinit var session: MediaSessionCompat
    private val am by lazy { getSystemService(AudioManager::class.java) }
    private var focus: AudioFocusRequest? = null
    private var resumeOnGain = false
    private var art: Pair<String, Bitmap?>? = null

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) Playback.pause() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "narration").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Playback.play()
                override fun onPause() = Playback.pause()
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
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        if (!Playback.paused) { resumeOnGain = true; Playback.pause() }
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; Playback.pause() }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) { resumeOnGain = false; Playback.play() }
                }
            }.build()
        focus = req
        am.requestAudioFocus(req)
        Playback.listen { refresh() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> Playback.toggle()
            ACT_NEXT -> Playback.next()
            ACT_PREV -> Playback.prev()
            ACT_STOP -> Playback.stop()
        }
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
        if (Playback.now == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Playback.listen(null)
        focus?.let { am.abandonAudioFocusRequest(it) }
        runCatching { unregisterReceiver(noisy) }
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private fun refresh() {
        if (Playback.now == null) return
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
    }

    private fun bitmap(path: String?): Bitmap? {
        if (path == null) return null
        art?.let { if (it.first == path) return it.second }
        val b = runCatching {
            assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }
        }.getOrNull()
        art = path to b
        return b
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
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
