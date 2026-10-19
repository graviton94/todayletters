package io.github.graviton94.todayletters.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.graviton94.todayletters.core.Fetching
import io.github.graviton94.todayletters.core.Packs
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * 앱 밖(Cloudflare R2)에 두는 장의 낭독 · 그림 · 곡 받기.
 * 목록은 assets/letters/<작품>/remote.json (generate.py 가 씀): 장마다 파일 경로(assets 와 같은 경로) · 크기 · 합.
 * 받은 파일은 files/<경로> (files/audio/... · files/plates/... · files/gallery/...) 에 두고, [AssetFiles] 가 앱 안 파일보다 먼저 찾는다.
 * 계정 · 학습 기록 같은 것은 보내지 않는다: 공개 주소에서 파일을 받기만 한다.
 */
class Downloads(private val ctx: Context) {
    companion object {
        const val ALL = "*"
        /** 처음 묶음 (서문 + 무료 장, remote.json "first"): 앱 안 장이 없는 작품을 처음 열 때 한 번에 묻는다. */
        const val FIRST = "first"
        /** 기초 낱말 소리 묶음 (remote.json 의 장 "B", core Basics.CHAPTER): 장이 아니라서 따로 묻지 않고, 다른 장을 받을 때 함께 받는다. */
        const val BASICS = io.github.graviton94.todayletters.core.Basics.CHAPTER
        /** 이번 장의 소리 묶음 (remote.json 의 장 "S", core Sounds.CHAPTER): 기초 낱말 소리처럼 다른 장을 받을 때 함께 받는다. */
        const val SOUNDS = io.github.graviton94.todayletters.core.Sounds.CHAPTER
        /** 장이 아닌 묶음 (따로 묻지 않고 함께 받는다). */
        val EXTRAS = listOf(BASICS, SOUNDS)
    }
    /** 받을 파일 하나: 폰 안 경로 · 크기 · R2 주소 꼬리 (고친 파일은 rev/<판>/<경로>, 나머지는 경로 그대로). */
    data class Part(val path: String, val size: Long, val src: String = path)
    /** 장 묶음 zip 하나 (remote.json "pack": [R2 주소, 길이, sha256]): [parts] 를 담는다. 받기는 이것이 먼저. */
    data class Archive(val src: String, val size: Long, val sha256: String, val parts: List<Part>)
    data class Pack(val series: String, val chapter: String, val bytes: Long, val parts: List<Part>, val archives: List<Archive> = emptyList())

    // 작품마다 처음 물을 때 한 번 읽는다 (assets.list 에 기대지 않는다)
    private val index = HashMap<String, Pair<String, Map<String, Pack>>?>()
    private val firsts = HashMap<String, List<String>>()
    private fun load(sid: String): Pair<String, Map<String, Pack>>? = synchronized(index) {
        index.getOrPut(sid) {
            runCatching {
                val o = JSONObject(ctx.assets.open("letters/$sid/remote.json").bufferedReader().use { it.readText() })
                val chs = o.getJSONObject("chapters")
                firsts[sid] = o.optJSONArray("first")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                o.getString("base") to chs.keys().asSequence().associateWith { c ->
                    val co = chs.getJSONObject(c); val fa = co.getJSONArray("files")
                    val parts = (0 until fa.length()).map { i -> fa.getJSONArray(i).let { Part(it.getString(0), it.getLong(1), it.optString(2).ifEmpty { it.getString(0) }) } }
                    val zip = co.optJSONArray("pack")?.let { a -> Archive(a.getString(0), a.getLong(1), a.getString(2), parts) }
                    Pack(sid, c, co.getLong("bytes"), parts, listOfNotNull(zip))
                }
            }.getOrNull()
        }
    }

    /** 이 작품에서 앱 밖에 둔 모든 장을 한 묶음으로 (설정 › 저장 공간의 ‘모두 받기’). [open] 은 읽을 수 있는 장만 (잠긴 장은 받지 않는다). 없으면 null. */
    fun all(series: String, open: (String) -> Boolean = { true }): Pack? {
        val packs = load(series)?.second?.values?.filter { it.chapter in EXTRAS || open(it.chapter) }?.sortedBy { it.chapter }.orEmpty()
        if (packs.isEmpty()) return null
        val parts = packs.flatMap { it.parts }.distinctBy { it.path }
        return Pack(series, ALL, parts.sumOf { it.size }, parts, packs.flatMap { it.archives })
    }

    /** 처음 묶음의 장 id 들 (서문 P + 무료 장). 앱 안 장이 있는 작품은 빈 목록. */
    fun firstChapters(series: String): List<String> { load(series); return synchronized(index) { firsts[series].orEmpty() } }

    /** 처음 묶음: 서문 + 무료 장을 한 묶음으로 ([FIRST]). 없으면 null. */
    fun first(series: String): Pack? {
        val packs = firstChapters(series).mapNotNull { pack(series, it) }
        if (packs.isEmpty()) return null
        val parts = packs.flatMap { it.parts }.distinctBy { it.path }
        return Pack(series, FIRST, parts.sumOf { it.size }, parts, packs.flatMap { it.archives })
    }

    /** 이 장을 열기 전에 물을 묶음: 처음 묶음에 든 장이면 처음 묶음, 아니면 그 장. 다 받았거나 앱 안이면 null. 기초 낱말 · 이번 장의 소리를 아직 안 받았으면 함께. */
    fun needed(series: String, chapter: String): Pack? =
        (if (chapter in firstChapters(series)) first(series) else pack(series, chapter))?.takeIf { !ready(it) }?.let { withBasics(it) }

    /** 기초 낱말 소리 (묶음 [BASICS]) · 이번 장의 소리 (묶음 [SOUNDS]) 를 아직 다 받지 않았으면 [p] 에 더한다. */
    private fun withBasics(p: Pack): Pack {
        val more = EXTRAS.mapNotNull { pack(p.series, it)?.takeIf { b -> !ready(b) } }
        if (more.isEmpty()) return p
        val parts = (p.parts + more.flatMap { it.parts }).distinctBy { it.path }
        return p.copy(bytes = parts.sumOf { it.size }, parts = parts, archives = p.archives + more.flatMap { it.archives })
    }

    /** 받은 파일이 놓이는 폴더들 (files/ 아래, assets 와 같은 이름). */
    private val roots = listOf("audio", "plates", "sketches", "gallery")

    /** 받아 둔 낭독 · 그림의 크기 (바이트). */
    fun stored(): Long = roots.sumOf { r -> File(ctx.filesDir, r).walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    /** 받아 둔 것을 모두 지운다 (앱 안에 든 맛보기 장은 그대로). */
    fun clear() { roots.forEach { File(ctx.filesDir, it).deleteRecursively() }; work.deleteRecursively(); AssetFiles.changed() }

    /** 묶음 zip 을 받고 푸는 곳 (files/.packs/<이름>.part · <이름>.x/): 다 풀고 검사한 뒤에만 제자리로 옮긴다. */
    private val work get() = File(ctx.filesDir, ".packs")
    private fun zipPart(a: Archive) = File(work, a.src.substringAfterLast('/') + ".part")
    /** 이 zip 이 채울 파일 바이트 (아직 없는 파일만): 진행 몫. */
    private fun weight(a: Archive) = a.parts.filter { file(it).length() != it.size }.sumOf { it.size }

    /** 이 장의 낭독 · 그림이 앱 밖에 있는가. 없으면 null (앱 안에 들어 있는 장). */
    fun pack(series: String, chapter: String): Pack? = load(series)?.second?.get(chapter)

    private fun file(p: Part) = File(ctx.filesDir, p.path)

    /** 이 장을 다 받아 두었는가. */
    fun ready(pack: Pack) = pack.parts.all { file(it).length() == it.size }

    /** 받다 만 파일 (files/<경로>.part) 의 크기: 다시 받을 때 여기서부터 잇는다. */
    private fun partial(p: Part): Long = File(file(p).path + ".part").length().takeIf { it in 1 until p.size } ?: 0L

    /** 아직 받을 양 (바이트, 받다 만 것은 뺀다). */
    fun remaining(pack: Pack): Long {
        val zipped = pack.archives.flatMap { a -> a.parts.map { it.path } }.toSet()
        val loose = pack.parts.filter { it.path !in zipped && file(it).length() != it.size }.sumOf { it.size - partial(it) }
        return loose + pack.archives.sumOf { a -> weight(a).let { w -> w - Packs.credit(zipPart(a).length(), a.size, w) } }
    }

    /** 지금 모바일 데이터로 연결돼 있는가 (와이파이가 아니면 안내에 적는다). */
    fun onMobileData(): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    /** 지금 인터넷에 연결돼 있는가 (끊겼으면 받기는 기다렸다가 잇는다). */
    fun online(): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /**
     * 받기. 장 묶음 zip ([Archive]) 이 있으면 그것 하나를 받고 (이어 받기 · sha256 검사), 임시 폴더에 풀어 크기를 본 뒤 제자리로 옮긴다
     * ([Packs]). R2 에 zip 이 없으면 (404 · 403) 그 묶음은 예전처럼 파일을 하나씩. 이미 받은 파일은 건너뛴다.
     * 받다 만 파일(.part)은 서버가 이어 받기(HTTP Range)를 받아 주면 그 뒤부터, 아니면 그 파일만 처음부터.
     * [progress] 는 (받은 바이트, 전체 바이트) — 파일 바이트로 센다 (zip 은 받은 비율만큼). 코루틴을 취소하거나 (멈추기) 인터넷이 끊겨
     * 실패해도 받다 만 파일은 남겨 둔다 (다음에 잇는다). 깨진 zip (합 · 크기가 다름) 은 지우고 실패. 실패하면 예외.
     */
    suspend fun fetch(pack: Pack, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val base = load(pack.series)?.first ?: error("no base")
        val total = pack.bytes
        val done = AtomicLong(total - remaining(pack))
        progress(done.get(), total)
        val lock = Mutex()
        val add: suspend (Long) -> Unit = { d -> if (d != 0L) { val now = done.addAndGet(d); lock.withLock { progress(now, total) } } }
        // 받은 만큼은 (멈추거나 실패해도) 화면이 다시 찾도록, 끝에 한 번 알린다
        try {
            val zipped = HashSet<String>()
            val loose = ArrayList<Part>()
            for (a in pack.archives) {
                a.parts.forEach { zipped += it.path }
                if (a.parts.all { file(it).length() == it.size }) continue
                if (!fetchArchive(base, a, add)) loose += a.parts
            }
            loose += pack.parts.filter { it.path !in zipped }
            fetchFiles(base, loose.distinctBy { it.path }.filter { file(it).length() != it.size }, add)
        } finally { AssetFiles.changed() }
    }

    /** zip 하나 받기 → 검사 → 풀기 → 옮기기. 서버에 zip 이 없으면 false (파일을 하나씩 받는다). */
    private suspend fun fetchArchive(base: String, a: Archive, add: suspend (Long) -> Unit): Boolean = coroutineScope {
        work.mkdirs()
        val tmp = zipPart(a)
        val w = weight(a)
        fun credit(b: Long) = Packs.credit(b, a.size, w)
        if (tmp.length() > a.size) { add(-credit(tmp.length())); tmp.delete() }
        var have = tmp.length()
        if (have < a.size) {
            val conn = (URL(base + a.src).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 20_000
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                val code = conn.responseCode
                // 아직 올리지 않은 zip: 받다 만 것은 버리고 파일을 하나씩
                if (code == 404 || code == 403) { add(-credit(have)); tmp.delete(); return@coroutineScope false }
                when (Fetching.resume(have, code)) {
                    Fetching.Resume.APPEND -> Unit
                    Fetching.Resume.RESTART -> { add(-credit(have)); have = 0 }
                    Fetching.Resume.DISCARD -> { add(-credit(have)); tmp.delete(); error("HTTP 416") }
                    Fetching.Resume.FAIL -> error("HTTP $code")
                }
                conn.inputStream.use { inp ->
                    java.io.FileOutputStream(tmp, have > 0).use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = inp.read(buf); if (n < 0) break
                            o.write(buf, 0, n)
                            val before = credit(have); have += n
                            add(credit(have) - before)
                        }
                    }
                }
            } finally { conn.disconnect() }
        }
        if (tmp.length() > a.size) { add(-credit(tmp.length())); tmp.delete(); error("size") }
        if (tmp.length() != a.size) error("short")
        val stage = File(work, a.src.substringAfterLast('/') + ".x")
        try {
            if (Packs.sha256(tmp) != a.sha256) error("sha256")
            Packs.extract(tmp, stage, a.parts.associate { it.path to it.size })
            Packs.install(stage, ctx.filesDir, a.parts.map { it.path })
        } catch (e: Throwable) {
            // 깨진 zip: 지우고 다음 번에 처음부터 (반쯤 푼 것은 Packs 가 지운다)
            stage.deleteRecursively(); tmp.delete(); add(-credit(a.size))
            throw e
        }
        tmp.delete()
        true
    }

    /** 파일을 하나씩 (zip 이 없는 묶음). 네 갈래로 나눠 받는다 (작은 파일이 많아서). */
    private suspend fun fetchFiles(base: String, todo: List<Part>, add: suspend (Long) -> Unit) = coroutineScope {
        todo.chunked(((todo.size + 3) / 4).coerceAtLeast(1)).map { group ->
            async {
                group.forEach { p ->
                    ensureActive()
                    val out = file(p); out.parentFile?.mkdirs()
                    val tmp = File(out.path + ".part")
                    // 다 받아 놓고 이름만 못 바꾼 것 / 너무 큰 것
                    if (tmp.length() == p.size && tmp.renameTo(out)) { add(p.size); return@forEach }
                    if (tmp.length() > p.size) tmp.delete()
                    var have = tmp.length()
                    val conn = (URL(base + p.src).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000; readTimeout = 20_000
                        if (have > 0) setRequestProperty("Range", "bytes=$have-")
                    }
                    try {
                        val code = conn.responseCode
                        when (Fetching.resume(have, code)) {
                            Fetching.Resume.APPEND -> Unit
                            // 서버가 이어 받기를 모른다: 이 파일만 처음부터
                            Fetching.Resume.RESTART -> { if (have > 0) add(-have); have = 0 }
                            // 받다 만 것이 서버 파일과 맞지 않는다: 버리고 다음 번에 처음부터
                            Fetching.Resume.DISCARD -> { tmp.delete(); add(-have); error("HTTP 416") }
                            Fetching.Resume.FAIL -> error("HTTP $code")
                        }
                        conn.inputStream.use { inp ->
                            java.io.FileOutputStream(tmp, have > 0).use { o ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    ensureActive()
                                    val n = inp.read(buf); if (n < 0) break
                                    o.write(buf, 0, n)
                                    add(n.toLong())
                                }
                            }
                        }
                        if (tmp.length() > p.size) { tmp.delete(); error("size") }
                        if (tmp.length() != p.size) error("short")
                        if (!tmp.renameTo(out)) error("rename")
                    } finally { conn.disconnect() }
                }
            }
        }.awaitAll()
    }
}
