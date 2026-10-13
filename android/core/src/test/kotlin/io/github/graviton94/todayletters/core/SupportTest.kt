package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 의견 보내기 본문 · 오류 기록 줄이기, 미리 받기 진행 글자 · 다시 받기 간격 · 이어 받기. */
class SupportTest {
    @Test fun bodyWithoutDeviceInfoIsJustTheText() {
        assertEquals("화면이 멈춰요", Feedback.body("  화면이 멈춰요 \n", null, "boom", "오류 기록"))
    }

    @Test fun bodyWithDeviceInfoAndCrash() {
        val b = Feedback.body("글", "Android 15 · Pixel 8 · 앱 1.21 · 배우는 말 fr · 오류 1건", "java.lang.IllegalStateException\n at a.b", "오류 기록")
        assertTrue(b.startsWith("글\n\n— — —\nAndroid 15 · Pixel 8"))
        assertTrue(b.contains("오류 기록\njava.lang.IllegalStateException"))
        // 오류 기록이 없으면 머리도 없다
        assertFalse(Feedback.body("글", "Android 15", null, "오류 기록").contains("오류 기록"))
    }

    @Test fun crashIsTrimmedAtALine() {
        val log = (1..400).joinToString("\n") { "    at io.github.example.Frame$it(Frame.kt:$it)" }
        val t = Feedback.trimCrash(log, 1000)
        assertTrue(t.length <= 1002)
        assertTrue(t.endsWith("\n…"))
        assertTrue(t.removeSuffix("\n…").lines().all { it.endsWith(")") })
        assertEquals("short", Feedback.trimCrash("  short \n"))
    }

    @Test fun crashReportHasTheEssentials() {
        val r = Feedback.crashReport("2026-10-13T09:00", "1.21 (121)", "15", "Pixel 8", "main", "java.lang.RuntimeException: x\n\tat A.b(A.kt:1)\n")
        assertEquals(listOf("time: 2026-10-13T09:00", "app: 1.21 (121)", "android: 15 · Pixel 8", "thread: main", "java.lang.RuntimeException: x", "\tat A.b(A.kt:1)"), r.lines())
    }

    @Test fun subjectNamesKindAndVersion() {
        assertEquals("[언어 미술관] 오류 · 1.21", Feedback.subject("언어 미술관", "오류", "1.21"))
    }

    @Test fun progressLabels() {
        assertEquals("23 / 64MB", Fetching.progress(23_400_000, 63_200_000))
        assertEquals("0 / 64MB", Fetching.progress(-5, 64_000_000))
        assertEquals("64 / 64MB", Fetching.progress(99_000_000, 64_000_000))
        assertEquals("120 / 820KB", Fetching.progress(120_000, 820_000))
        assertEquals(35, Fetching.percent(23_000_000, 64_000_000))
        assertEquals(100, Fetching.percent(0, 0))
        assertEquals("5.1MB", Fetching.size(5_100_000))
        assertEquals("820KB", Fetching.size(820_000))
    }

    @Test fun backoffGrowsThenCaps() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L), (0..7).map(Fetching::backoff))
        assertEquals(2_000L, Fetching.backoff(-3))
    }

    @Test fun resumeFromPartialFile() {
        assertEquals(Fetching.Resume.APPEND, Fetching.resume(1000, 206))
        assertEquals(Fetching.Resume.RESTART, Fetching.resume(1000, 200))   // 서버가 이어 받기를 모르면 처음부터
        assertEquals(Fetching.Resume.RESTART, Fetching.resume(0, 200))
        assertEquals(Fetching.Resume.DISCARD, Fetching.resume(1000, 416))
        assertEquals(Fetching.Resume.FAIL, Fetching.resume(0, 206))
        assertEquals(Fetching.Resume.FAIL, Fetching.resume(1000, 503))
    }
}
