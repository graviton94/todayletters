package io.github.graviton94.todayletters.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** 낭독 한 덩어리: 시작 · 끝(밀리초) · 글자. */
data class Chunk(val start: Int, val end: Int, val text: String)

/**
 * 파일 찾기 (낭독 · 그림 · 곡): 앱 밖(R2)에서 받아 둔 파일(files/<경로>)이 있으면 그것을, 없으면 앱 안 assets/<경로> 를.
 * 그래서 화면 코드는 장이 앱 안에 있든 받아 온 것이든 같은 경로("audio/작품/장/편지/파일.m4a" · "plates/파일.webp")만 쓴다.
 * 둘 다 없으면 (아직 받지 않은 장) 그림은 빈 액자 · 소리는 조용히.
 */
object AssetFiles {
    /** 받기가 끝나거나 지울 때마다 하나씩 늘어난다. 그림을 읽는 화면이 이것을 열쇠로 다시 읽는다. */
    var generation by mutableIntStateOf(0)
        private set
    fun changed() { generation++ }

    fun local(ctx: Context, path: String) = File(ctx.filesDir, path)
    fun fd(ctx: Context, path: String): android.content.res.AssetFileDescriptor? {
        val f = local(ctx, path)
        if (f.isFile) return runCatching {
            android.content.res.AssetFileDescriptor(android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY), 0, f.length())
        }.getOrNull()
        return runCatching { ctx.assets.openFd(path) }.getOrNull()
    }
    fun text(ctx: Context, path: String): String? {
        val f = local(ctx, path)
        if (f.isFile) return runCatching { f.readText() }.getOrNull()
        return runCatching { ctx.assets.open(path).bufferedReader().readText() }.getOrNull()
    }
    /** 읽기 (받아 둔 것 먼저). 없으면 예외 (부르는 쪽이 runCatching). */
    fun open(ctx: Context, path: String): java.io.InputStream {
        val f = local(ctx, path)
        return if (f.isFile) f.inputStream() else ctx.assets.open(path)
    }
    /** 이 파일이 지금 기기에 있는가 (앱 안이든 받아 둔 것이든). 앱 안 목록은 폴더마다 한 번만 읽는다. */
    fun has(ctx: Context, path: String): Boolean {
        if (path.isBlank()) return false
        if (local(ctx, path).isFile) return true
        val dir = path.substringBeforeLast('/', ""); val name = path.substringAfterLast('/')
        val names = synchronized(listed) { listed.getOrPut(dir) { runCatching { ctx.assets.list(dir)?.toSet() }.getOrNull() ?: emptySet() } }
        return name in names
    }
    private val listed = HashMap<String, Set<String>>()
}

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
    companion object {
        @Volatile private var one: Narrator? = null
        /**
         * 앱에 하나뿐인 낭독 재생기: 화면(액티비티)이 다시 만들어져도 같은 것을 쓴다. 그래서 앱을 밀어 닫은 뒤 이어 읽던 낭독([Playback.handOff])을
         * 다시 연 앱의 소리가 자연스럽게 넘겨받는다 (두 소리가 겹치지 않음).
         */
        fun shared(ctx: Context): Narrator {
            val app = ctx.applicationContext ?: ctx
            return one?.takeIf { it.ctx === app } ?: synchronized(this) { one?.takeIf { it.ctx === app } ?: Narrator(app).also { one = it } }
        }
    }
    private var player: MediaPlayer? = null

    fun path(series: String, chapter: String, letter: String, file: String) = "audio/$series/$chapter/$letter/$file.m4a"

    fun has(asset: String) = AssetFiles.fd(ctx, asset)?.also { runCatching { it.close() } } != null

    /**
     * 이 작품의 기본 듣기 빠르기 ([io.github.graviton94.todayletters.core.Listen.speed], AppState 가 건다).
     * 빠르기를 따로 주지 않은 낭독 · 낱말 · 회화 소리는 모두 이것으로. 효과음(sounds/)은 늘 1.
     */
    var base: () -> Float = { 1f }

    /**
     * 0.7× 단추가 지금 소리를 낸 자리 (화면의 단추가 채워진 모양 · 다시 누르면 멈춤을 고른다).
     * 천천히가 아닌 소리가 시작되면 지워진다. 끝났는지는 [now] 로 본다.
     */
    var slowBy by mutableStateOf<Any?>(null)

    /**
     * 들은 시간 (core 의 [io.github.graviton94.todayletters.core.Listening]): 배우는 말의 소리를 실제로 재생한 만큼 (언어, 밀리초) 를 알린다.
     * 멈춘 동안은 세지 않는다. AppState 가 건다.
     */
    var heard: (String, Long) -> Unit = { _, _ -> }
    /** 소리 하나를 끝까지 들었을 때 (파일 경로). AppState 가 건다 (천천히 듣는 2주의 처음 한 번). */
    var ended: (String) -> Unit = {}
    private val clock = io.github.graviton94.todayletters.core.Listening.Clock()
    private fun tick() = android.os.SystemClock.elapsedRealtime()
    private fun count() { clock.stop(tick())?.let { (l, ms) -> runCatching { heard(l, ms) } } }

    /**
     * 누가 언제 소리를 낼 수 있는가 ([io.github.graviton94.todayletters.core.Voice]): 지금 화면 · 지금 소리의 주인 · 끝났을 때 부를 것.
     * 화면을 옮기면 [enter], 화면이 사라질 때는 [stop] (주인) 으로 제 소리만.
     */
    private val voice = io.github.graviton94.todayletters.core.Voice()
    /** 지금 화면 (AppState 가 [enter] 로 건다). 소리의 주인을 따로 주지 않으면 이 화면의 소리. */
    val scene: Any? get() = voice.scene

    /**
     * 화면을 옮겼다: 앞 화면의 소리를 멈추고 (끝까지 듣지 않은 것으로) 이제부터는 [scene] 의 소리만.
     * [keep] 이면 소리는 그대로 (밀어 닫은 뒤 이어 읽기). 효과음(sounds/, 화면을 넘기는 종이 소리 등)은 끝까지 둔다.
     */
    fun enter(scene: Any?, keep: Boolean = false) {
        routeScene = scene
        voice.enter(scene)
        if (!keep && now?.startsWith("sounds/") != true) stop()
    }
    private var routeScene: Any? = null

    /** 화면 안의 작은 단계 (질문 k · 마디 · 회화 차례 · 답장 턴 · 예열 카드): 화면 이름 + 단계. */
    data class Step(val scene: Any?, val key: Any?)

    /**
     * 화면 안의 작은 단계로 옮겼다 (시안 하루 흐름 ①): 이제부터는 이 단계의 소리만 받는다. 앞 단계의 늦은 자동 재생 · 이어 읽기
     * (그 단계 주인으로 부른 것) 는 받지 않는다. [stopNow] 면 지금 소리도 멈춘다. 돌려준 주인으로 이 단계의 소리를 낸다.
     */
    fun step(key: Any?, stopNow: Boolean = false): Any {
        val o = Step(routeScene, key)
        voice.enter(o)
        if (stopNow && now?.startsWith("sounds/") != true) stop()
        return o
    }

    /** [speed] 를 주지 않으면 기본 빠르기 ([base]). [onDone] 은 끝까지 들었든 멈췄든 한 번 (이어 읽으려면 [playThen]). */
    fun play(asset: String, speed: Float? = null, owner: Any? = voice.scene, onDone: () -> Unit = {}): Boolean =
        playThen(asset, speed, owner) { onDone() }

    /**
     * [play] 와 같고, 끝났을 때 끝까지 들었는가(true) · 멈췄거나 다른 소리로 바뀌었는가(false) 를 준다.
     * 다음 문장 · 다음 줄로 이어 읽는 쪽은 true 일 때만 잇는다. [owner] 가 지난 화면이면 소리 없이 false.
     */
    fun playThen(asset: String, speed: Float? = null, owner: Any? = voice.scene, onEnd: (completed: Boolean) -> Unit): Boolean {
        if (!voice.admits(owner)) { onEnd(false); return false }
        stop()
        if (speed != io.github.graviton94.todayletters.core.Listen.SLOW) slowBy = null
        val rate = speed ?: if (asset.startsWith("sounds/")) 1f else runCatching { base() }.getOrDefault(1f)
        val fd = AssetFiles.fd(ctx, asset) ?: run { onEnd(true); return false }
        voice.begin(owner, onEnd)
        // 시험만 ([fake]): 소리 없이 시작만 적고, 끝은 [fakeEnd] 가 (멈춤 · 다른 소리로 바뀜은 앱과 같다)
        fake?.let { runCatching { fd.close() }; it += asset; now = asset; fakeAt = 0; return true }
        // 소리 파일이 깨졌거나 재생기가 거절하면 조용히 넘어간다 (대화는 계속)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(media)
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setOnCompletionListener {
                    if (player === this) { count(); now = null; runCatching { ended(asset) }; voice.end(true) }
                }
                prepare()
                if (rate != 1f) playbackParams = playbackParams.setSpeed(rate)
                start()
            }
        }.getOrNull()
        runCatching { fd.close() }
        if (player == null) { voice.end(true); return false }
        now = asset
        clock.start(asset, tick())
        return true
    }

    /** 새 소리를 [owner] 의 것으로 지금 받을 수 있는가 (멈추는 중이 아니고, 지난 화면 · 단계가 아님). */
    fun admits(owner: Any?): Boolean = voice.admits(owner)

    /**
     * 시험만 (NavTest · 화면 사진): null 이 아니면 소리를 실제로 내지 않고 시작한 파일을 차례로 적는다. 소리는 [fakeEnd] 때 끝까지 들은 것으로 끝난다
     * (그 전에 다른 소리 · 멈춤이 오면 앱처럼 ‘끝까지 듣지 않음’). 앱에서는 늘 null.
     */
    var fake: MutableList<String>? = null
    /** 시험만: [fake] 소리의 재생 위치 ([position] 이 돌려준다, 소리를 시작하면 0). */
    var fakeAt: Int = 0
    /** 시험만: [fake] 로 내는 지금 소리를 끝까지 들은 것으로 끝낸다. */
    fun fakeEnd() { if (fake != null && now != null && player == null) { count(); now = null; voice.end(true) } }

    /** 지금 소리를 내는 파일 (멈춘 것 포함, 끝나거나 멈추면 null). 화면이 읽는 자리를 칠할 때 이것을 본다. */
    var now by mutableStateOf<String?>(null)
        private set

    /** 멈춘다. 기다리던 쪽은 ‘끝까지 듣지 않음’으로 한 번 불러서 멈춰 서지 않게 한다. */
    fun stop() = halt(completed = false)

    /** [owner] 가 낸 소리일 때만 멈춘다 (나가는 화면의 onDispose 가 새 화면의 소리를 끄지 않게). */
    fun stop(owner: Any?) { if (player != null && !voice.owns(owner)) return; stop() }

    private fun halt(completed: Boolean) {
        count()
        player?.runCatching { stop(); release() }
        player = null
        playing = null
        now = null
        paused = false
        voice.end(completed)
    }

    /** 지금 소리가 끝나면 (또는 멈추면) [onEnd] (기다리던 것을 바꾼다, 끝까지 들었는가와 함께). 지금 소리가 없으면 바로. */
    fun follow(onEnd: (completed: Boolean) -> Unit) { if (now == null) onEnd(true) else voice.follow(onEnd) }

    /** 잠깐 멈춤 · 이어서 (알림창 · 이어폰 단추). 멈춘 동안에는 ‘끝났을 때’를 부르지 않는다. */
    var paused = false
        private set
    fun pause() { player?.runCatching { if (isPlaying) { pause(); paused = true; clock.pause(tick()) } } }
    fun resume() { player?.runCatching { if (paused) { start(); paused = false; clock.resume(tick()) } } }
    /** 지금 파일의 처음으로. */
    fun restart() { player?.runCatching { seekTo(0) } }

    /** 지금 파일의 [ms] 자리로 (편지 방: 낱말부터 이어 듣기 · 긴 쉼 건너뛰기). 시험의 [fake] 소리면 [fakeAt] 을 옮긴다. */
    fun seekTo(ms: Int) {
        if (fake != null && player == null) { if (now != null) fakeAt = ms; return }
        player?.runCatching { seekTo(ms.coerceAtLeast(0)) }
    }

    /** 지금 소리의 빠르기를 바꾼다 (편지 방 빠르기 칩, 높낮이 유지). 다음 소리는 [playThen] 의 빠르기대로. */
    fun setRate(rate: Float) {
        player?.runCatching { val was = isPlaying; playbackParams = playbackParams.setSpeed(rate); if (!was) pause() }
    }
    /** 지금 파일을 재생 중인가 (멈춘 것도 포함). */
    val active get() = player != null

    /** 낭독의 한 토막만: [from]~[to] 밀리초 (따라 읽기의 한 마디). */
    fun playRange(asset: String, from: Int, to: Int, speed: Float? = null, owner: Any? = voice.scene, onDone: () -> Unit = {}): Boolean =
        playRangeThen(asset, from, to, speed, owner) { onDone() }

    /** [playRange] 를 끝까지 들었는가와 함께 ([playThen] 처럼). 토막 끝에 닿으면 끝까지 들은 것. */
    fun playRangeThen(asset: String, from: Int, to: Int, speed: Float? = null, owner: Any? = voice.scene, onEnd: (completed: Boolean) -> Unit): Boolean {
        if (!playThen(asset, speed, owner, onEnd)) return false
        val pl = player ?: return false
        runCatching { pl.seekTo(from) }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (player !== pl) return
                val at = runCatching { pl.currentPosition }.getOrDefault(to)
                if (at >= to) halt(completed = true) else h.postDelayed(this, 40)
            }
        }
        h.postDelayed(tick, 40)
        return true
    }

    /** 지금 파일의 길이 (밀리초). 재생 중이 아니면 -1. */
    fun duration(): Int = player?.runCatching { duration }?.getOrNull() ?: -1

    /** 재생 위치 (밀리초). 재생 중이 아니면 -1. */
    fun position(): Int {
        if (fake != null && player == null) return if (now != null) fakeAt else -1
        return player?.runCatching { if (isPlaying || paused) currentPosition else -1 }?.getOrNull() ?: -1
    }

    /** 낭독의 덩어리 시각: assets/....m<번호>_<언어>.json (narrate.py 가 씀). 없으면 빈 목록. */
    fun chunks(asset: String): List<Chunk> = runCatching {
        val o = org.json.JSONObject(AssetFiles.text(ctx, asset.removeSuffix(".m4a") + ".json")!!)
        val a = o.getJSONArray("chunks")
        (0 until a.length()).map { a.getJSONObject(it).let { c -> Chunk((c.getDouble("s") * 1000).toInt(), (c.getDouble("e") * 1000).toInt(), c.getString("text")) } }
    }.getOrDefault(emptyList())

    /**
     * 낱말 시간 (정렬 작업 align.yml 이 낭독마다 만든 `<소리>.words.json`, core 의 WordTimes · [WordTimesFiles]). s · e 는 소리 시작부터 밀리초.
     * 없거나 정렬이 약하면 ("ok": false) null: 부르는 쪽이 기기에서 어림한다 (core 의 WordSpots.estimate). 낱말 차례는 화면 글 그대로.
     */
    fun words(asset: String): List<io.github.graviton94.todayletters.core.WordAt>? =
        WordTimesFiles.load(ctx, asset)?.takeIf { it.ok }?.words?.map { io.github.graviton94.todayletters.core.WordAt(it.text, it.start, it.end) }?.takeIf { it.isNotEmpty() }

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
 * 편지 속 그 곡 (60초 발췌): assets/audio/<작품>/music/<파일>. 낭독([Narrator])과 따로 울리는 재생기 하나.
 * 재생 · 멈춤 · 10초 앞뒤 · 위치를 화면(듣기 시트 · 악보 속 산책)이 직접 다룬다. 파일이 없으면 조용히 false.
 */
class Excerpt(private val ctx: Context) {
    private var player: MediaPlayer? = null
    /** 지금 걸어 둔 파일 (멈춘 것 포함). */
    var loaded: String? = null
        private set
    private var done: (() -> Unit)? = null

    /** [asset] 을 처음부터. 끝까지 들으면 [onDone]. */
    fun play(asset: String, onDone: () -> Unit = {}): Boolean {
        stop()
        val fd = AssetFiles.fd(ctx, asset) ?: return false
        done = onDone
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setOnCompletionListener { val d = done; done = null; d?.invoke() }
                prepare(); start()
            }
        }.getOrNull()
        runCatching { fd.close() }
        if (player != null) loaded = asset
        return player != null
    }

    val playing get() = player?.runCatching { isPlaying }?.getOrNull() == true
    fun pause() { player?.runCatching { if (isPlaying) pause() } }
    fun resume() { player?.runCatching { if (!isPlaying) start() } }
    /** [ms] 만큼 앞뒤로 (끝을 넘지 않게). */
    fun skip(ms: Int) { player?.runCatching { seekTo((currentPosition + ms).coerceIn(0, (duration - 200).coerceAtLeast(0))) } }
    fun position(): Int = player?.runCatching { currentPosition }?.getOrNull() ?: 0
    fun duration(): Int = player?.runCatching { duration }?.getOrNull()?.takeIf { it > 0 } ?: 60_000

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null; loaded = null; done = null
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
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) { out.write(buf, 0, n); onFrame?.let { f -> live(buf, n, f) } }
            }
            runCatching { rec.stop() }; rec.release()
            val pcm = out.toByteArray()
            if (pcm.size >= Wav.RATE * 2 * 3 / 10) { Wav.write(to, pcm); saved = true }
        }.also { it.start() }
        true
    }.getOrDefault(false)

    /**
     * 녹음하는 동안 프레임마다 (크기 dB, 음높이 Hz · 목소리 없으면 0) 를 화면 쪽(메인 스레드)으로 알려 준다.
     * 따라 읽기의 실시간 파형 · 음높이 선. 계산은 기기 안에서만.
     */
    @Volatile var onFrame: ((Float, Float) -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private fun live(buf: ByteArray, n: Int, f: (Float, Float) -> Unit) {
        val sb = java.nio.ByteBuffer.wrap(buf, 0, n).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val x = FloatArray(sb.remaining()) { sb.get(it) / 32768f }
        if (x.isEmpty()) return
        var e = 0.0; for (v in x) e += v * v
        val db = (20 * kotlin.math.log10(kotlin.math.sqrt(e / x.size).coerceAtLeast(1e-6))).toFloat()
        val win = Wav.RATE * 40 / 1000
        val hz = if (db > -45f && x.size >= win) io.github.graviton94.todayletters.core.Prosody.yin(x, 0, win, Wav.RATE) else 0f
        main.post { f(db, hz) }
    }

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
    val fd = AssetFiles.fd(ctx, asset)!!
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

/**
 * 맞음 · 틀림 효과음 (v21 1단계 F1): 앱이 직접 만든 짧은 소리 둘 (assets/sounds/right.wav · wrong.wav, 합성음이라 저작권 없음).
 * 낭독(Narrator)과 따로 울려서 문장 소리를 끊지 않는다. 휴대폰이 무음이면 소리가 나지 않는다 (미디어 볼륨).
 */
class Effects(ctx: Context) {
    private val pool = android.media.SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val right = runCatching { ctx.assets.openFd("sounds/right.wav").use { pool.load(it, 1) } }.getOrDefault(0)
    private val wrong = runCatching { ctx.assets.openFd("sounds/wrong.wav").use { pool.load(it, 1) } }.getOrDefault(0)
    fun play(ok: Boolean) { val id = if (ok) right else wrong; if (id != 0) pool.play(id, 0.8f, 0.8f, 1, 0, 1f) }
    /** 자료가 걸릴 때 (v21 3단계 D2): 등급마다 다른 소리 (sounds/tier_<등급>.wav, 역시 합성음). */
    private val tiers = listOf("sketch", "drawing", "painting", "master").associateWith { n ->
        runCatching { ctx.assets.openFd("sounds/tier_$n.wav").use { pool.load(it, 1) } }.getOrDefault(0)
    }
    fun tier(name: String) { tiers[name]?.takeIf { it != 0 }?.let { pool.play(it, 0.9f, 0.9f, 1, 0, 1f) } }
    /** 새 편지 표시가 내려앉을 때: 종이 소리 (sounds/page.m4a, 입구와 같은 것). 낭독을 끊지 않게 여기서. */
    private val page = runCatching { ctx.assets.openFd("sounds/page.m4a").use { pool.load(it, 1) } }.getOrDefault(0)
    fun page() { if (page != 0) pool.play(page, 0.7f, 0.7f, 1, 0, 1f) }
}
