package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LadderTest {
    @Test fun helpShrinksAsTheLevelRises() {
        assertEquals(Ladder.Help.LINK, Ladder.help(Reading.Grade.of(1)))
        assertEquals(Ladder.Help.LINK, Ladder.help(Reading.Grade.of(2)))
        assertEquals(Ladder.Help.CHOOSE, Ladder.help(Reading.Grade.of(3)))
        assertEquals(Ladder.Help.CHOOSE, Ladder.help(Reading.Grade.of(6)))
        assertEquals(Ladder.Help.CHOOSE_TWO, Ladder.help(Reading.Grade.of(7)))
        assertEquals(Ladder.Help.WRITE, Ladder.help(Reading.Grade.of(12)))
        assertEquals(listOf(0, 1, 2, 2), Ladder.Help.entries.map(Ladder::blanks))
    }

    @Test fun tokensKeepPunctuationAroundTheBlank() {
        val t = Ladder.tokens("Moi aussi, j'ai pensé à toi.")
        assertEquals(6, t.size)
        assertEquals("aussi", Ladder.core(t[1]))
        assertEquals("" to ",", Ladder.around(t[1]))
        assertEquals("«" to "»!", Ladder.around("«Oui»!"))
        assertEquals("", Ladder.core("—"))
    }

    @Test fun pickPrefersScoredWordsAndAvoidsTheFirst() {
        val t = Ladder.tokens("Envoie-moi un dessin du pont.")
        val score = mapOf("dessin" to 5, "pont" to 4, "Envoie-moi" to 9)
        // 첫 낱말은 다른 후보가 있으면 피한다, 고른 자리는 문장 차례로
        assertEquals(listOf(2), Ladder.pick(t, 1) { score[it] ?: 0 })
        assertEquals(listOf(2, 4), Ladder.pick(t, 2) { score[it] ?: 0 })
        assertEquals(emptyList(), Ladder.pick(t, 0) { 1 })
        // 다른 후보가 없으면 첫 낱말도 된다
        assertEquals(listOf(0), Ladder.pick(t, 1) { if (it == "Envoie-moi") 1 else 0 })
    }

    @Test fun chipsHoldTheAnswerAndDistinctDecoys() {
        val c = Ladder.chips("trouvé", near = listOf("trouver", "Trouvé"), pool = listOf("maison", "jaune", "maison"), seed = 3)
        assertEquals(3, c.size)
        assertTrue("trouvé" in c)
        assertTrue("trouver" in c)                      // 같은 낱말의 다른 꼴이 먼저
        assertEquals(c.size, c.map { it.lowercase() }.distinct().size)
        assertEquals(c, Ladder.chips("trouvé", listOf("trouver", "Trouvé"), listOf("maison", "jaune", "maison"), 3))
    }

    @Test fun writingIsGradedLeniently() {
        assertTrue(Ladder.right("pensé", "pense"))       // 악상만 틀림
        assertTrue(Ladder.right("Toi", "toi."))
        assertFalse(Ladder.right("pont", "port"))
        assertEquals("pe", Ladder.hint("pensé", 2))
        assertEquals("pon", Ladder.hint("pont", 9))      // 정답 전체는 주지 않는다
    }

    @Test fun replyCardKey() {
        val k = CardRef.reply("vincent", "I", "c1-l1", "en")
        assertEquals("vincent:I:c1-l1:en/r", k)
        val ref = CardRef.parse(k)!!
        assertTrue(ref.reply)
        assertEquals("en", ref.deck)
        assertFalse(CardRef.parse(CardRef.word("vincent", "I", "c1-l1", 3))!!.reply)
    }
}
