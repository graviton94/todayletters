package io.github.graviton94.todayletters.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTimesTest {
    private val doc = """{"v":1,"ok":true,"words":[{"t":"Mon","s":610,"e":800,"q":0.91},{"t":"cher","s":840,"e":1050,"q":0.88},
        {"t":"Théo,","s":1080,"e":1500,"q":0.7},{"t":"—","s":1500,"e":1500},{"t":"durant","s":2400,"e":2800,"q":0.6}]}"""

    @Test fun parsesWordsScoresAndSilentTokens() {
        val wt = WordTimes.parse(doc)!!
        assertTrue(wt.ok)
        assertEquals(listOf("Mon", "cher", "Théo,", "—", "durant"), wt.words.map { it.text })
        assertEquals(840, wt.words[1].start); assertEquals(1050, wt.words[1].end)
        assertEquals(0.88f, wt.words[1].score!!, 1e-6f)
        assertNull(wt.words[3].score); assertFalse(wt.words[3].voiced)
    }

    @Test fun weakFlagAndBadInput() {
        assertFalse(WordTimes.parse("""{"v":1,"ok":false,"words":[{"t":"a","s":0,"e":10,"q":0.01}]}""")!!.ok)
        assertNull(WordTimes.parse("""{"v":2,"ok":true,"words":[]}"""))
        assertNull(WordTimes.parse("not json"))
        assertNull(WordTimes.parse("""{"v":1,"words":[{"t":"a"}]}"""))
    }

    @Test fun currentWordSkipsSilentTokens() {
        val wt = WordTimes.parse(doc)!!
        assertEquals(-1, wt.at(100))
        assertEquals(0, wt.at(700))
        assertEquals(2, wt.at(1600))   // "—" 는 소리가 없어 고르지 않는다
        assertEquals(4, wt.at(9000))
        assertEquals(610..1500, wt.span(0, 3))
        assertNull(wt.span(3, 3))
    }

    @Test fun pathAndTokensMatchTheApp() {
        assertEquals("audio/vincent/I/c1-l1/m1_fr.words.json", WordTimes.pathFor("audio/vincent/I/c1-l1/m1_fr.m4a"))
        assertTrue(WordTimes.optional("audio/vincent/II/c2-l1/tb1_ko.words.json"))
        assertFalse(WordTimes.optional("audio/vincent/II/c2-l1/m1_ko.json"))
        assertEquals(listOf("오는", "길에", "나는"), WordTimes.tokens(Breaks.keepAll("오는  길에 나는")))
        assertTrue(WordTimes.parseFor(doc, "Mon cher Théo, — durant") != null)
        assertNull(WordTimes.parseFor(doc, "Mon cher Théo, durant"))
    }

    @Test fun piecesCutAtLongestBreath() {
        fun w(t: String, s: Int, e: Int) = WordTimes.Word(t, s, e, 0.9f)
        val wt = WordTimes(listOf(w("I", 0, 100), w("thought", 120, 400), w("of", 420, 500), w("you,", 520, 800),
            w("and", 1400, 1500), w("of", 1520, 1600), w("the", 1620, 1700), w("new", 1720, 1900), w("country.", 1920, 2400)), true)
        assertEquals(listOf(0..3, 4..8), wt.pieces(5))
        assertEquals(listOf(0..8), wt.pieces(9))
        assertTrue(wt.pieces(3).all { it.last - it.first + 1 <= 3 })
        assertEquals((0..8).toList(), wt.pieces(3).flatMap { it.toList() })
    }
}
