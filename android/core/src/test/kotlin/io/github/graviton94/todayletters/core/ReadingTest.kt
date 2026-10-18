package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingTest {
    @Test fun functionWordsNamesAndNumbersCountAsKnown() {
        assertTrue(Reading.free(LexEntry("le", pos = "art", function = true)))
        assertTrue(Reading.free(LexEntry("Arles", pos = "name")))
        assertTrue(Reading.free(LexEntry("cara", pos = "foreign:it")))
        assertFalse(Reading.free(LexEntry("neige", pos = "n")))
        assertFalse(Reading.free(null))
    }

    @Test fun coverageCountsEveryToken() {
        val known = setOf("le", "neige")
        // 같은 낱말이 두 번이면 두 번 센다
        assertEquals(75, Reading.pct(listOf("le", "neige", "le", "tomber")) { it in known })
        assertEquals(0, Reading.pct(emptyList()) { true })
        assertEquals(70, Reading.average(listOf(10, 60, 70, 80, 60, 80)))
        assertEquals(0, Reading.average(emptyList()))
    }

    @Test fun gradeNeedsWordsAndCoverage() {
        assertEquals(Reading.Grade(Reading.Band.A1, 1), Reading.grade(known = 0, recent = 0))
        assertEquals(Reading.Grade(Reading.Band.A1, 2), Reading.grade(known = 44, recent = 0))
        assertEquals(Reading.Grade(Reading.Band.A1, 3), Reading.grade(known = 45, recent = 0))
        // 낱말은 A2 만큼이어도 비율이 65% 아래면 A1 · 3단에 머문다
        assertEquals(Reading.Grade(Reading.Band.A1, 3), Reading.grade(known = 130, recent = 64))
        assertEquals(Reading.Grade(Reading.Band.A2, 2), Reading.grade(known = 100, recent = 65))
        // B1 은 78%, B2 는 85%
        assertEquals(Reading.Grade(Reading.Band.A2, 3), Reading.grade(known = 400, recent = 77))
        assertEquals(Reading.Grade(Reading.Band.B1, 3), Reading.grade(known = 400, recent = 84))
        assertEquals(Reading.Grade(Reading.Band.B2, 3), Reading.grade(known = 400, recent = 85))
        assertEquals(Reading.Grade(Reading.Band.B2, 2), Reading.grade(known = 399, recent = 90))
        // 닿았던 등급 아래로는 내려가지 않는다 (예전 문턱으로 오른 사람도 그대로)
        assertEquals(7, Reading.grade(known = 0, recent = 0, floor = 7).index)
        assertEquals(Reading.Grade(Reading.Band.B1, 1), Reading.Grade.of(7))
        assertEquals(12, Reading.Grade.of(99).index)
    }

    @Test fun thresholdsMatchGradeMockup() {
        assertEquals(listOf(0, 20, 45, 75, 100, 130, 170, 215, 260, 310, 360, 400), Reading.words.toList())
        assertEquals(listOf(0, 65, 78, 85), Reading.coverage.toList())
    }

    @Test fun nextStepNeeds() {
        assertEquals(25, Reading.wordsToNext(Reading.Grade.of(4), 75))
        assertNull(Reading.wordsToNext(Reading.Grade.of(12), 999))
        assertEquals(78, Reading.coverageToNext(Reading.Grade.of(6)))
        assertNull(Reading.coverageToNext(Reading.Grade.of(5)))
    }

    @Test fun gradeCountsWriteStageButReadingKeepsRecall() {
        val recall = Card("a", box = Growth.Stage.RECALL.ordinal)
        val write = Card("b", box = Growth.Stage.WRITE.ordinal)
        assertTrue(Growth.known(recall))
        assertFalse(Growth.knownForGrade(recall))
        assertTrue(Growth.knownForGrade(write))
        assertFalse(Growth.knownForGrade(null))
    }
}
