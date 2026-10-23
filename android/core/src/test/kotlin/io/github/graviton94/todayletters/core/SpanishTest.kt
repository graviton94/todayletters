package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 스페인어 (고야, 2026-10-23): 언어 코드 · 따옴표 · 줄임말 · 음절 어림. */
class SpanishTest {
    @Test fun code() = assertEquals(Lang.ES, Lang.entries.first { it.code == "es" })

    @Test fun quote() = assertEquals("«hola»", Quote.wrap("hola", Lang.ES))

    @Test fun abbreviations() {
        val t = "Mi querido Sr. D. Martín: dé Vm. memorias a la Sra. Josefa. ¿Vendrás?"
        assertTrue(Abbrev.keeps(t, t.indexOf("Sr.") + 2))
        assertTrue(Abbrev.keeps(t, t.indexOf("Vm.") + 2))
        assertTrue(Abbrev.keeps(t, t.indexOf("Sra.") + 3))
        assertFalse(Abbrev.keeps(t, t.indexOf("Josefa.") + 6))
    }

    @Test fun syllables() {
        assertEquals(4, SpeakCheck.estimate("Madrid, año", Lang.ES))
        assertEquals(4, SpeakCheck.estimate("está aquí", Lang.ES))
    }
}
