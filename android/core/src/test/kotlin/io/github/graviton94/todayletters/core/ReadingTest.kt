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
        assertEquals(Reading.Grade(Reading.Band.A1, 3), Reading.grade(known = 45, recent = 0))
        // 낱말은 A2 만큼이어도 비율이 60% 아래면 A1 · 3단에 머문다
        assertEquals(Reading.Grade(Reading.Band.A1, 3), Reading.grade(known = 100, recent = 59))
        assertEquals(Reading.Grade(Reading.Band.A2, 2), Reading.grade(known = 100, recent = 62))
        assertEquals(Reading.Grade(Reading.Band.B2, 3), Reading.grade(known = 999, recent = 90))
        // 닿았던 등급 아래로는 내려가지 않는다
        assertEquals(7, Reading.grade(known = 0, recent = 0, floor = 7).index)
        assertEquals(Reading.Grade(Reading.Band.B1, 1), Reading.Grade.of(7))
        assertEquals(12, Reading.Grade.of(99).index)
    }

    @Test fun nextStepNeeds() {
        assertEquals(15, Reading.wordsToNext(Reading.Grade.of(4), 75))
        assertNull(Reading.wordsToNext(Reading.Grade.of(12), 999))
        assertEquals(68, Reading.coverageToNext(Reading.Grade.of(6)))
        assertNull(Reading.coverageToNext(Reading.Grade.of(5)))
    }

    @Test fun translationStepsBackByBand() {
        val known = setOf("je", "suis", "content", "ici")
        val all = listOf("je", "suis", "content")
        val most = listOf("je", "suis", "ici", "content", "je", "suis", "ici", "content", "je", "suis", "ici", "content", "je", "suis", "ici", "content", "je", "suis", "ici", "neige")
        assertFalse(Reading.folds(Reading.Band.A1, all) { it in known })
        assertTrue(Reading.folds(Reading.Band.A2, all) { it in known })
        assertFalse(Reading.folds(Reading.Band.A2, most) { it in known })   // 95% 는 A2 에서 접지 않는다
        assertTrue(Reading.folds(Reading.Band.B1, most) { it in known })
        // 두 낱말 인사말은 접지 않는다
        assertFalse(Reading.folds(Reading.Band.B2, listOf("je", "suis")) { it in known })
    }
}
