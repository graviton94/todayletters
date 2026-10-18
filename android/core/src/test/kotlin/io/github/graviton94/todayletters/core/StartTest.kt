package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartTest {
    @Test fun levelMapsToStartGradeAndSlowDays() {
        assertEquals(listOf(1, 1, 4), Start.Level.entries.map(Start::grade))
        assertEquals(listOf(14, 7, 0), Start.Level.entries.map(Start::slowDays))
        Start.Level.entries.forEach { assertEquals(it, Start.level(Start.grade(it), Start.slowDays(it))) }
        assertEquals(Reading.Band.A2, Reading.Grade.of(Start.grade(Start.Level.SLOW_READ)).band)
    }

    @Test fun amountOnlyChangesReviewCards() {
        assertEquals(Memory.DAILY, Start.daily(Start.Amount.ONE))
        assertEquals(Start.MORE_REVIEW, Start.daily(Start.Amount.MORE))
        assertEquals(2, Start.Amount.entries.size)   // 편지 반 통은 없다
        val cards = (1..30).map { Card("k$it", due = 0) }
        assertEquals(Memory.DAILY, Memory.dueToday(cards, 5).size)
        assertEquals(Start.MORE_REVIEW, Memory.dueToday(cards, 5, Start.daily(Start.Amount.MORE)).size)
    }

    @Test fun asksOnlyNewUsers() {
        assertTrue(Start.asks(answered = false, legacy = false, startedInLang = false))
        assertFalse(Start.asks(answered = true, legacy = false, startedInLang = false))
        assertFalse(Start.asks(answered = false, legacy = true, startedInLang = false))
        assertFalse(Start.asks(answered = false, legacy = false, startedInLang = true))
    }

    @Test fun whySuggestsNextSeries() {
        val ids = listOf("vincent", "mozart", "napoleon")
        assertEquals(listOf("vincent", "mozart", "napoleon"), Start.suggest(Start.Why.ARTS, listOf("mozart", "napoleon", "vincent")))
        assertEquals("napoleon", Start.suggest(Start.Why.WORK, ids).first())
        assertEquals(ids, Start.suggest(Start.Why.CURIOUS, ids))
        assertEquals(listOf("vincent", "basho"), Start.suggest(Start.Why.TRAVEL, listOf("basho", "vincent")))
    }

    @Test fun slowPeriodDays() {
        assertEquals(1, Slow.day(100, 14, 100))
        assertEquals(14, Slow.day(100, 14, 113))
        assertNull(Slow.day(100, 14, 114))       // 저절로 끝
        assertNull(Slow.day(100, 14, 99))
        assertNull(Slow.day(-1, 14, 100))
        assertNull(Slow.day(100, 0, 100))
        assertEquals(7, Slow.day(100, 7, 106))
        assertNull(Slow.day(100, 7, 107))
    }

    @Test fun slowNoteOnceAfterFirstLetterAndOnDayThree() {
        assertEquals(Slow.Note.FIRST, Slow.note(1, firstDone = true, shownFirst = false, shownDay3 = false))
        assertNull(Slow.note(2, firstDone = false, shownFirst = true, shownDay3 = false))
        assertEquals(Slow.Note.DAY3, Slow.note(3, firstDone = false, shownFirst = true, shownDay3 = false))
        assertNull(Slow.note(3, firstDone = false, shownFirst = true, shownDay3 = true))
        assertNull(Slow.note(4, firstDone = false, shownFirst = true, shownDay3 = false))
        assertNull(Slow.note(null, firstDone = true, shownFirst = false, shownDay3 = false))
        assertEquals(1, Slow.minutes(1))
        assertEquals(2, Slow.minutes(61_000))
    }

    @Test fun listeningLanguageFromFileName() {
        assertEquals("fr", Listening.langOf("audio/vincent/I/c1-l1/m3_fr.m4a"))
        assertEquals("ko", Listening.langOf("audio/vincent/I/c1-l1/tk2_ko.m4a"))
        assertEquals("de", Listening.langOf("audio/mozart/II/c2-l1/w4_de.m4a"))
        assertEquals("ko", Listening.langOf("audio/vincent/P/v_ko__c1__b1e2_.m4a"))
        assertNull(Listening.langOf("sounds/page.m4a"))
        assertNull(Listening.langOf("audio/mozart/music/k525.m4a"))
        assertNull(Listening.langOf("audio/vincent/I/c1-l1/m3.m4a"))
    }

    @Test fun listeningClockCountsOnlyPlayback() {
        val c = Listening.Clock()
        assertNull(c.start("audio/v/I/l/m1_fr.m4a", 1_000))
        c.pause(3_000)            // 2초
        c.resume(10_000)          // 멈춘 7초는 세지 않음
        assertEquals("fr" to 3_000L, c.stop(11_000))
        assertNull(c.stop(12_000))
        // 다음 소리가 시작되면 앞의 것을 돌려준다
        c.start("audio/v/I/l/w1_en.m4a", 0)
        assertEquals("en" to 500L, c.start("sounds/page.m4a", 500))
        assertNull(c.stop(9_000))  // 효과음은 세지 않음
    }

    @Test fun listeningWeekAndFormat() {
        // 1970-01-05 (epoch day 4) 는 월요일
        assertEquals(4L, Listening.weekStart(4))
        assertEquals(4L, Listening.weekStart(10))
        assertEquals(11L, Listening.weekStart(11))
        assertEquals(3 to 20, Listening.hm((3 * 60 + 20) * 60_000L + 59_000))
        assertEquals(48, Listening.min(48 * 60_000L + 10))
    }
}
