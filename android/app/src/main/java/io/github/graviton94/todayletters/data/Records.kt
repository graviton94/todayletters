package io.github.graviton94.todayletters.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.graviton94.todayletters.core.Backup
import java.io.File
import java.time.LocalDate

/**
 * 기록 보관의 파일 일 (설정 › 기록 보관). 무엇을 넣고 빼는지 · 파일 꼴은 core [Backup].
 * 파일은 사용자가 고른 곳 (시스템 저장 창) 이나 공유 창으로만 나간다. 앱이 따로 올리는 곳은 없다.
 */
object Records {
    /** 읽을 파일의 한도 (진도 파일은 수백 KB 정도): 넘으면 이 앱의 기록이 아니다. */
    private const val MAX_BYTES = 8L * 1024 * 1024

    fun made(day: Long): String = LocalDate.ofEpochDay(day).toString()

    fun export(store: Store, day: Long): String = Backup.encode(store.snapshot(), made(day))

    fun summary(store: Store, day: Long): Backup.Summary = Backup.summary(store.snapshot().filterKeys(Backup::keep), made(day))

    /** 시스템 저장 창이 준 곳에 쓴다. */
    fun write(ctx: Context, uri: Uri, text: String): Boolean = runCatching {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.isSuccess

    /** 고른 파일을 읽는다. 이 앱의 기록 파일이 아니면 null. */
    fun read(ctx: Context, uri: Uri): Backup.Records? = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val bytes = input.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) null else Backup.decode(String(bytes, Charsets.UTF_8))
        }
    }.getOrNull()

    /** 공유 창으로 보낼 파일: cache/records/<이름> 에 쓰고 FileProvider 주소로 (res/xml/file_paths.xml). */
    fun shareIntent(ctx: Context, text: String, name: String, title: String): Intent? = runCatching {
        val dir = File(ctx.cacheDir, "records").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name).apply { writeText(text, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (total < limit) {
            val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
            if (n < 0) break
            out.write(buf, 0, n); total += n
        }
        return out.toByteArray()
    }
}
