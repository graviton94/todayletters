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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * 앱 밖(Cloudflare R2)에 두는 장의 낭독 받기.
 * 목록은 assets/letters/<작품>/remote.json (generate.py 가 씀): 장마다 파일 경로 · 크기 · 합.
 * 받은 파일은 files/audio/... 에 두고, [AudioFiles] 가 앱 안 파일보다 먼저 찾는다.
 * 계정 · 학습 기록 같은 것은 보내지 않는다: 공개 주소에서 파일을 받기만 한다.
 */
class Downloads(private val ctx: Context) {
    data class Part(val path: String, val size: Long)
    data class Pack(val series: String, val chapter: String, val bytes: Long, val parts: List<Part>)

    // 작품마다 처음 물을 때 한 번 읽는다 (assets.list 에 기대지 않는다)
    private val index = HashMap<String, Pair<String, Map<String, Pack>>?>()
    private fun load(sid: String): Pair<String, Map<String, Pack>>? = synchronized(index) {
        index.getOrPut(sid) {
            runCatching {
                val o = JSONObject(ctx.assets.open("letters/$sid/remote.json").bufferedReader().use { it.readText() })
                val chs = o.getJSONObject("chapters")
                o.getString("base") to chs.keys().asSequence().associateWith { c ->
                    val co = chs.getJSONObject(c); val fa = co.getJSONArray("files")
                    Pack(sid, c, co.getLong("bytes"), (0 until fa.length()).map { i -> fa.getJSONArray(i).let { Part(it.getString(0), it.getLong(1)) } })
                }
            }.getOrNull()
        }
    }

    /** 이 장의 낭독이 앱 밖에 있는가. 없으면 null (앱 안에 들어 있는 장). */
    fun pack(series: String, chapter: String): Pack? = load(series)?.second?.get(chapter)

    private fun file(p: Part) = File(ctx.filesDir, "audio/${p.path}")

    /** 이 장을 다 받아 두었는가. */
    fun ready(pack: Pack) = pack.parts.all { file(it).length() == it.size }

    /** 아직 받을 양 (바이트). */
    fun remaining(pack: Pack) = pack.parts.filter { file(it).length() != it.size }.sumOf { it.size }

    /** 지금 모바일 데이터로 연결돼 있는가 (와이파이가 아니면 안내에 적는다). */
    fun onMobileData(): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    /**
     * 받기. 이미 받은 파일은 건너뛴다. [progress] 는 (받은 바이트, 전체 바이트).
     * 코루틴을 취소하면 (뒤로 · 그만 받기) 받다 만 파일은 지우고 멈춘다. 실패하면 예외.
     */
    suspend fun fetch(pack: Pack, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val base = load(pack.series)?.first ?: error("no base")
        val todo = pack.parts.filter { file(it).length() != it.size }
        val total = pack.bytes
        val done = AtomicLong(total - todo.sumOf { it.size })
        progress(done.get(), total)
        val lock = Mutex()
        coroutineScope {
            // 네 갈래로 나눠 받는다 (작은 파일이 많아서)
            todo.chunked(((todo.size + 3) / 4).coerceAtLeast(1)).map { group ->
                async {
                    group.forEach { p ->
                        ensureActive()
                        val out = file(p); out.parentFile?.mkdirs()
                        val tmp = File(out.path + ".part")
                        val conn = (URL(base + p.path).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
                        try {
                            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                            conn.inputStream.use { inp ->
                                tmp.outputStream().use { o ->
                                    val buf = ByteArray(16 * 1024)
                                    while (true) {
                                        ensureActive()
                                        val n = inp.read(buf); if (n < 0) break
                                        o.write(buf, 0, n)
                                        val now = done.addAndGet(n.toLong())
                                        lock.withLock { progress(now, total) }
                                    }
                                }
                            }
                            if (tmp.length() != p.size) error("size")
                            tmp.renameTo(out)
                        } catch (e: Throwable) {
                            tmp.delete(); throw e
                        } finally { conn.disconnect() }
                    }
                }
            }.awaitAll()
        }
    }
}
