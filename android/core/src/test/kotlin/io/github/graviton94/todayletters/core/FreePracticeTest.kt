package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 자유 연습 (APK 20 피드백 5b): 한 번 누르면 연 장들의 카드에서 무작위로, 꼴도 무작위로 한 판. */
class FreePracticeTest {
    private fun card(chapter: String, letter: String, n: Int, box: Int = 0) = Card(CardRef.word("vincent", chapter, letter, n), box = box)
    private val cards = (1..6).flatMap { l -> (0..4).map { card("I", "l$l", it, box = it) } } +
        (1..3).flatMap { l -> (0..4).map { card("II", "m$l", it) } } +
        (0..4).map { card("III", "n1", it) } +
        listOf(Card(CardRef.word("mozart", "I", "k1", 0)))

    @Test fun onlyChaptersThatWereOpened() {
        val set = Memory.freeSet(cards, setOf("vincent:I", "vincent:II"), seed = 7, day = 100)
        assertEquals(Memory.DAILY, set.size)
        assertTrue(set.all { (c, _) -> CardRef.parse(c.key)!!.let { it.series == "vincent" && it.chapter in setOf("I", "II") } })
        assertEquals(set.size, set.map { it.first.key }.distinct().size)
    }

    @Test fun anyLetterAnyKindAndTheSameRoundForTheSameSeed() {
        val a = Memory.freeSet(cards, setOf("vincent:I", "vincent:II", "vincent:III"), seed = 42, day = 100)
        assertEquals(a, Memory.freeSet(cards, setOf("vincent:I", "vincent:II", "vincent:III"), seed = 42, day = 100))
        // 다른 판은 다른 카드 (낮은 칸 · 첫 편지부터가 아니라 섞어서)
        val many = (1L..20L).map { Memory.freeSet(cards, setOf("vincent:I", "vincent:II", "vincent:III"), seed = it, day = 100) }
        assertTrue(many.map { r -> r.map { it.first.key } }.distinct().size > 10)
        val letters = many.flatten().map { CardRef.parse(it.first.key)!!.letter }.toSet()
        assertEquals(10, letters.size)
        // 꼴도 넷 모두 나온다 (성장 단계와 상관없이)
        assertEquals(ReviewKind.entries.toSet(), many.flatten().map { it.second }.toSet())
        // 꼴은 카드와 날로 정해진다 (이어 풀어도 같은 꼴)
        a.forEach { (c, k) -> assertEquals(Memory.freeKind(c.key, 100), k) }
    }

    @Test fun smallPoolsAndNothingOpened() {
        assertEquals(5, Memory.freeSet(cards, setOf("vincent:III"), seed = 1, day = 1).size)
        assertTrue(Memory.freeSet(cards, emptySet(), seed = 1, day = 1).isEmpty())
    }
}
