package io.github.graviton94.todayletters.core

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 장 묶음 zip (scripts/generate.py pack_zip, remote.json 의 "pack": [주소, 길이, sha256]): 받은 zip 을 검사하고 풀어 제자리에 둔다.
 * 순서: [sha256] 로 합 검사 → [extract] 로 임시 폴더에 풀기 (목록에 있는 이름 · 크기만, 하나라도 어긋나면 임시 폴더를 지우고 실패)
 * → [install] 로 다 푼 뒤에만 제자리로 옮기기. 그래서 받다 만 · 깨진 zip 이 files/ 에 반쯤 풀린 파일을 남기지 않는다.
 */
object Packs {
    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * [zip] 을 [staging] 에 푼다. [expect] = 폰 안 경로 → 크기 (remote.json 의 "files"). 목록 밖 이름 · 겹친 이름 · 크기가 다른 것 ·
     * 빠진 것이 있으면 [staging] 을 지우고 예외. 이름에 ".." · 절대 경로가 있어도 실패 (폴더 밖으로 쓰지 않는다).
     */
    fun extract(zip: File, staging: File, expect: Map<String, Long>) {
        staging.deleteRecursively()
        try {
            val seen = HashSet<String>()
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    require(!name.startsWith("/") && name.split('/').none { it == ".." || it.isEmpty() }) { "bad name $name" }
                    val want = expect[name] ?: error("unexpected $name")
                    require(seen.add(name)) { "twice $name" }
                    val out = File(staging, name); out.parentFile?.mkdirs()
                    var n = 0L
                    out.outputStream().use { o ->
                        while (true) { val k = z.read(buf); if (k < 0) break; o.write(buf, 0, k); n += k; if (n > want) error("size $name") }
                    }
                    require(n == want) { "size $name" }
                }
            }
            require(seen.size == expect.size) { "missing ${(expect.keys - seen).take(3)}" }
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** [staging] 에 다 푼 [names] 를 [dest] 의 같은 경로로 옮긴다 (있던 파일은 바꾼다), 그 뒤 [staging] 을 지운다. */
    fun install(staging: File, dest: File, names: Collection<String>) {
        try {
            for (name in names) {
                val from = File(staging, name); val to = File(dest, name)
                to.parentFile?.mkdirs()
                if (to.exists()) to.delete()
                if (!from.renameTo(to)) { from.copyTo(to, overwrite = true); from.delete() }
            }
        } finally { staging.deleteRecursively() }
    }

    /** 묶음 zip 받는 동안의 진행 몫: zip [have] 바이트를 받았을 때 그 묶음이 채우는 파일 바이트 [weight] 가운데 얼마인가. */
    fun credit(have: Long, size: Long, weight: Long): Long = if (size <= 0) weight else have.coerceIn(0, size) * weight / size
}
