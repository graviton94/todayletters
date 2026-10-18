package io.github.graviton94.todayletters.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PacksTest {
    private fun tmp() = kotlin.io.path.createTempDirectory("packs").toFile()

    /** generate.py pack_zip 과 같은 꼴: 압축 없음 (stored). */
    private fun zip(f: File, entries: List<Pair<String, ByteArray>>) = ZipOutputStream(f.outputStream()).use { z ->
        for ((name, data) in entries) {
            val e = ZipEntry(name).apply { method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = size; crc = CRC32().apply { update(data) }.value }
            z.putNextEntry(e); z.write(data); z.closeEntry()
        }
    }

    private val a = "audio/mozart/II/c2-l1/m1_de.m4a" to ByteArray(300) { it.toByte() }
    private val b = "plates/m-c2-l1.webp" to "webp".toByteArray()

    @Test fun hashExtractAndInstall() {
        val d = tmp(); val f = File(d, "p.zip"); zip(f, listOf(a, b))
        assertEquals(64, Packs.sha256(f).length)
        assertEquals(Packs.sha256(f), Packs.sha256(File(d, "p.zip")))
        val stage = File(d, "x"); val files = File(d, "files")
        Packs.extract(f, stage, mapOf(a.first to 300L, b.first to 4L))
        File(files, b.first).apply { parentFile.mkdirs(); writeText("old") }
        Packs.install(stage, files, listOf(a.first, b.first))
        assertEquals(300L, File(files, a.first).length())
        assertEquals("webp", File(files, b.first).readText())
        assertFalse(stage.exists())
    }

    @Test fun badPacksLeaveNothing() {
        val d = tmp(); val f = File(d, "p.zip"); zip(f, listOf(a, b))
        val stage = File(d, "x")
        fun fails(expect: Map<String, Long>) {
            assertTrue(runCatching { Packs.extract(f, stage, expect) }.isFailure)
            assertFalse(stage.exists())
        }
        fails(mapOf(a.first to 299L, b.first to 4L))          // 크기가 다름
        fails(mapOf(a.first to 300L))                          // 목록 밖 이름
        fails(mapOf(a.first to 300L, b.first to 4L, "audio/x.m4a" to 1L))  // 빠진 것
        val evil = File(d, "e.zip"); zip(evil, listOf("../escape.txt" to "x".toByteArray()))
        assertTrue(runCatching { Packs.extract(evil, stage, mapOf("../escape.txt" to 1L)) }.isFailure)
        assertFalse(File(d, "escape.txt").exists())
        // 잘린 zip
        val cut = File(d, "c.zip"); cut.writeBytes(f.readBytes().copyOf(200))
        assertTrue(runCatching { Packs.extract(cut, stage, mapOf(a.first to 300L, b.first to 4L)) }.isFailure)
        assertFalse(stage.exists())
    }

    @Test fun creditIsProportional() {
        assertEquals(0L, Packs.credit(0, 1000, 900))
        assertEquals(450L, Packs.credit(500, 1000, 900))
        assertEquals(900L, Packs.credit(1000, 1000, 900))
        assertEquals(900L, Packs.credit(5000, 1000, 900))
    }
}
