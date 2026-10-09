package io.github.graviton94.todayletters.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** 낭독 한 덩어리: 시작 · 끝(밀리초) · 글자. */
data class Chunk(val start: Int, val end: Int, val text: String)

/** 앱의 모든 소리는 미디어 볼륨으로 (사용자가 녹음하는 마이크 입력만 예외). */
private val media: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * 낭독 재생: assets/audio/<작품>/<챕터>/<편지 id>/m<번호>_<언어>.m4a (낱말은 w<번호>_<언어>.m4a).
 * 파일이 없으면 조용히 넘어간다 (낭독이 아직 없는 편지도 읽을 수 있게).
 */
class Narrator(private val ctx: Context) {
    private var player: MediaPlayer? = null

    fun path(series: String, chapter: String, letter: String, file: String) = "audio/$series/$chapter/$letter/$file.m4a"

    fun has(asset: String) = runCatching { ctx.assets.openFd(asset).close() }.isSuccess

    fun play(asset: String, speed: Float = 1f, onDone: () -> Unit = {}): Boolean {
        stop()
        val fd = runCatching { ctx.assets.openFd(asset) }.getOrNull() ?: run { onDone(); return false }
        pending = onDone
        // 소리 파일이 깨졌거나 재생기가 거절하면 조용히 넘어간다 (대화는 계속)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(media)
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setOnCompletionListener { finish() }
                prepare()
                if (speed != 1f) playbackParams = playbackParams.setSpeed(speed)
                start()
            }
        }.getOrNull()
        runCatching { fd.close() }
        if (player == null) { finish(); return false }
        return true
    }

    /** 끝났을 때 부를 것. 멈춰도(다른 소리로 바뀌어도) 한 번은 불러서, 기다리던 쪽이 멈춰 서지 않게 한다. */
    private var pending: (() -> Unit)? = null
    private fun finish() { val d = pending; pending = null; d?.invoke() }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        playing = null
        finish()
    }

    /** 재생 위치 (밀리초). 재생 중이 아니면 -1. */
    fun position(): Int = player?.runCatching { if (isPlaying) currentPosition else -1 }?.getOrNull() ?: -1

    /** 낭독의 덩어리 시각: assets/....m<번호>_<언어>.json (narrate.py 가 씀). 없으면 빈 목록. */
    fun chunks(asset: String): List<Chunk> = runCatching {
        val o = org.json.JSONObject(ctx.assets.open(asset.removeSuffix(".m4a") + ".json").bufferedReader().readText())
        val a = o.getJSONArray("chunks")
        (0 until a.length()).map { a.getJSONObject(it).let { c -> Chunk((c.getDouble("s") * 1000).toInt(), (c.getDouble("e") * 1000).toInt(), c.getString("text")) } }
    }.getOrDefault(emptyList())

    /** 지금 재생 중인 파일 (화면의 듣기 버튼이 재생 · 멈춤 모양을 고른다). */
    var playing: String? = null
        private set

    /** 같은 파일을 다시 누르면 멈추고, 아니면 그 파일을 재생한다. */
    fun toggle(asset: String, onDone: () -> Unit = {}) {
        if (playing == asset) { stop(); onDone(); return }
        if (play(asset) { playing = null; onDone() }) playing = asset
    }
}

/** 따라 읽기 녹음: 앱 캐시에만 두고, 밖으로 보내지 않는다. */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var player: MediaPlayer? = null
    val file get() = File(ctx.cacheDir, "aloud.m4a")

    fun start(): Boolean = runCatching {
        stop()
        rec = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(32000)
            setAudioEncodingBitRate(48000)
            setOutputFile(file.absolutePath)
            prepare(); start()
        }
    }.isSuccess

    fun stop() {
        rec?.runCatching { stop(); release() }
        rec = null
    }

    fun play(onDone: () -> Unit = {}) {
        player?.release()
        if (!file.exists()) return onDone()
        player = MediaPlayer().apply {
            setAudioAttributes(media)
            setDataSource(file.absolutePath)
            setOnCompletionListener { onDone() }
            prepare(); start()
        }
    }

    fun release() { stop(); player?.release(); player = null }
}
