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

    /** 낭독의 한 토막만: [from]~[to] 밀리초 (따라 읽기의 한 마디). */
    fun playRange(asset: String, from: Int, to: Int, onDone: () -> Unit = {}): Boolean {
        if (!play(asset, onDone = onDone)) return false
        val pl = player ?: return false
        runCatching { pl.seekTo(from) }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (player !== pl) return
                val at = runCatching { pl.currentPosition }.getOrDefault(to)
                if (at >= to) stop() else h.postDelayed(this, 40)
            }
        }
        h.postDelayed(tick, 40)
        return true
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

/**
 * 따라 읽기 녹음: 기기 안에만 둔다 (사용자가 목소리 엽서로 보낼 때만 밖으로).
 * 마디 녹음은 AAC(ADTS)라서 파일을 차례로 이어 붙이면 그대로 한 편의 낭독이 된다.
 */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var target: File = File(ctx.cacheDir, "aloud.m4a")
    val file get() = target

    fun start(to: File = File(ctx.cacheDir, "aloud.m4a"), adts: Boolean = false): Boolean = runCatching {
        stop()
        target = to.also { it.parentFile?.mkdirs() }
        rec = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(if (adts) MediaRecorder.OutputFormat.AAC_ADTS else MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(32000)
            setAudioEncodingBitRate(48000)
            setOutputFile(target.absolutePath)
            prepare(); start()
        }
    }.isSuccess

    /** 녹음을 끝낸다. 너무 짧아 녹음이 안 됐으면 파일을 지우고 false (마이크는 늘 놓아 준다). */
    fun stop(): Boolean {
        val r = rec ?: return false
        rec = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok) runCatching { file.delete() }
        return ok
    }

    fun play(what: File = file, onDone: () -> Unit = {}) {
        runCatching { player?.release() }
        player = null
        if (!what.exists()) return onDone()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(media)
                setDataSource(what.absolutePath)
                setOnCompletionListener { onDone() }
                prepare(); start()
            }
        }.getOrNull()
        if (player == null) { if (what == file) runCatching { file.delete() }; onDone() }
    }

    fun stopPlaying() { runCatching { player?.stop(); player?.release() }; player = null }

    fun release() { stop(); player?.release(); player = null }
}

/** 따라 읽기 녹음과 분석에 쓰는 원음 (16kHz 모노 16비트 WAV). 마디 파일을 이어 붙이면 그대로 한 편의 낭독이 된다. */
object Wav {
    const val RATE = 16000

    fun write(to: File, pcm: ByteArray) {
        to.parentFile?.mkdirs()
        val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }.array()
        to.outputStream().use { it.write(h); it.write(pcm) }
    }

    /** WAV 의 소리 부분 (머리 44바이트 뒤). */
    fun pcm(file: File): ByteArray = file.readBytes().let { if (it.size > 44) it.copyOfRange(44, it.size) else ByteArray(0) }

    fun samples(pcm: ByteArray): FloatArray {
        val sb = java.nio.ByteBuffer.wrap(pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(sb.remaining()) { sb.get(it) / 32768f }
    }

    /** 여러 마디를 한 편으로. */
    fun join(parts: List<File>, to: File) {
        val all = java.io.ByteArrayOutputStream()
        parts.forEach { all.write(pcm(it)) }
        write(to, all.toByteArray())
    }
}

/** 마이크 → 원음. 멈추면 WAV 로 쓴다. 0.3초보다 짧으면 버린다. */
class PcmRecorder {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var target: File? = null
    private var saved = false

    @android.annotation.SuppressLint("MissingPermission")   // 부르는 쪽이 권한을 먼저 확인한다
    fun start(to: File): Boolean = runCatching {
        stop()
        val min = android.media.AudioRecord.getMinBufferSize(Wav.RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val rec = android.media.AudioRecord(MediaRecorder.AudioSource.MIC, Wav.RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, min * 4)
        if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        target = to; saved = false; running = true
        rec.startRecording()
        thread = Thread {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(min)
            while (running) { val n = rec.read(buf, 0, buf.size); if (n > 0) out.write(buf, 0, n) }
            runCatching { rec.stop() }; rec.release()
            val pcm = out.toByteArray()
            if (pcm.size >= Wav.RATE * 2 * 3 / 10) { Wav.write(to, pcm); saved = true }
        }.also { it.start() }
        true
    }.getOrDefault(false)

    /** 녹음을 끝낸다. 쓸 만한 녹음이 남았으면 true. */
    fun stop(): Boolean {
        if (!running) return false
        running = false
        thread?.join(3000); thread = null
        return saved
    }
}

/**
 * 낭독 파일(m4a)의 한 토막을 원음으로: [fromMs]~[toMs] 를 16kHz 모노로. 기기의 디코더를 쓴다. 실패하면 null.
 * 무거운 일이라 화면 스레드가 아닌 곳에서 부른다.
 */
fun decodeRange(ctx: Context, asset: String, fromMs: Int, toMs: Int): FloatArray? = runCatching {
    val fd = ctx.assets.openFd(asset)
    val ex = android.media.MediaExtractor().apply { setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
    fd.close()
    val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)!!.startsWith("audio/") }
    val fmt = ex.getTrackFormat(track)
    ex.selectTrack(track)
    ex.seekTo(fromMs * 1000L, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    val codec = android.media.MediaCodec.createDecoderByType(fmt.getString(android.media.MediaFormat.KEY_MIME)!!)
    codec.configure(fmt, null, null, 0); codec.start()
    var rate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
    var ch = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
    val mono = ArrayList<Float>()
    val info = android.media.MediaCodec.BufferInfo()
    var inDone = false; var outDone = false; var guard = 0
    while (!outDone && guard++ < 20_000) {
        if (!inDone) codec.dequeueInputBuffer(10_000).takeIf { it >= 0 }?.let { i ->
            val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
            if (n < 0 || ex.sampleTime > toMs * 1000L + 200_000) { codec.queueInputBuffer(i, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
        }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        if (o == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            codec.outputFormat.let { rate = it.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE); ch = it.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) }
        } else if (o >= 0) {
            val buf = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val frames = buf.remaining() / ch
            for (k in 0 until frames) {
                val t = info.presentationTimeUs / 1000.0 + k * 1000.0 / rate
                if (t < fromMs || t > toMs) continue
                var v = 0f; for (c in 0 until ch) v += buf.get(k * ch + c) / 32768f
                mono += v / ch
            }
            codec.releaseOutputBuffer(o, false)
            if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
        }
    }
    codec.stop(); codec.release(); ex.release()
    // 16kHz 로 (직선 보간)
    val n = (mono.size.toLong() * Wav.RATE / rate).toInt()
    FloatArray(n) { k -> val x = k.toDouble() * rate / Wav.RATE; val a = x.toInt().coerceAtMost(mono.size - 1); val b = (a + 1).coerceAtMost(mono.size - 1); (mono[a] + (mono[b] - mono[a]) * (x - a)).toFloat() }
}.getOrNull()
