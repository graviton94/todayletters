package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterlinearTest {
    // 빈센트 편지 I 첫 문장 (c1-l1 m0 앞부분)
    private val text = "Mon cher Théo, durant le voyage j'ai pour le moins autant pensé à toi qu'au nouveau pays que je voyais."
    private val tokens = listOf(
        Token("Mon", "mon"), Token("cher", "cher"), Token("Théo", "Théo"), Token("durant", "durant"), Token("le", "le"), Token("voyage", "voyage"),
        Token("j'", "je"), Token("ai", "avoir"), Token("pour", "pour"), Token("le", "le"), Token("moins", "moins"), Token("autant", "autant"),
        Token("pensé", "penser"), Token("à", "à"), Token("toi", "toi"), Token("qu'", "que"), Token("au", "au"), Token("nouveau", "nouveau"),
        Token("pays", "pays"), Token("que", "que"), Token("je", "je"), Token("voyais", "voir"),
    )
    private val lex = listOf(
        LexEntry("mon", ko = "나의", en = "my", function = true), LexEntry("cher", ko = "사랑하는, 소중한", en = "dear"),
        LexEntry("Théo", pos = "name"), LexEntry("durant", ko = "~ 동안", en = "during"), LexEntry("le", ko = "그", en = "the", function = true),
        LexEntry("voyage", ko = "여행", en = "journey"), LexEntry("je", ko = "나", en = "I", function = true), LexEntry("avoir", ko = "가지다", en = "to have"),
        LexEntry("pour", ko = "~을 위해", en = "for", function = true), LexEntry("moins", ko = "덜", en = "less"), LexEntry("autant", ko = "그만큼", en = "as much"),
        LexEntry("penser", ko = "생각하다", en = "to think"), LexEntry("à", ko = "~에", en = "to", function = true), LexEntry("toi", ko = "너", en = "you", function = true),
        LexEntry("que", ko = "~보다", en = "than", function = true), LexEntry("au", ko = "~에", en = "to the", function = true),
        LexEntry("nouveau", ko = "새로운", en = "new"), LexEntry("pays", ko = "나라 (고장)", en = "country"), LexEntry("voir", ko = "보다", en = "to see"),
    ).associateBy { it.key }
    private val anchors = listOf(Anchor("j'ai", listOf("j", "ai"), "나는 ~했어", "I have", id = Anchors.id("vincent", listOf("j", "ai"))),
        Anchor("mon cher", listOf("mon", "cher"), "사랑하는", "my dear", id = Anchors.id("vincent", listOf("mon", "cher"))))

    private fun units(known: Set<String> = emptySet(), function: Boolean = true, anchorKnown: (Int) -> Boolean = { false }) =
        Interlinear.units(tokens, { lex[it] }, Lang.KO, { it in known }, function, listOf("pour le moins" to "적어도, 최소한"), anchors, anchorKnown)

    @Test fun chunksAreOneUnit() {
        val u = units()
        val phrase = u.single { it.kind == Interlinear.Kind.PHRASE }
        assertEquals(8 to 10, phrase.from to phrase.to)
        assertEquals("적어도", phrase.gloss)
        val jai = u.single { it.kind == Interlinear.Kind.ANCHOR && it.from == 6 }
        assertEquals(7, jai.to)
        assertEquals("나는 ~했어", jai.gloss)
        assertEquals("114d634e", jai.anchorId)
        // 덩어리 안의 토큰은 따로 풀지 않는다
        assertTrue(u.none { it.kind == Interlinear.Kind.WORD && it.from in 6..10 })
    }

    @Test fun knownWordsLoseTheirGloss() {
        val u = units(known = setOf("voyage", "penser"))
        assertNull(u.single { it.from == 5 }.gloss)
        assertNull(u.single { it.from == 12 }.gloss)
        assertEquals("새로운", u.single { it.from == 17 }.gloss)
        // 이름은 뜻 없음, 기능어는 입문에서만
        assertNull(u.single { it.from == 2 }.gloss)
        assertEquals("그", u.single { it.from == 4 }.gloss)
        assertNull(units(function = false).single { it.from == 4 }.gloss)
        // 첫 뜻만
        assertEquals("나라", u.single { it.from == 18 }.gloss)
    }

    @Test fun knownAnchorHasNoGloss() {
        val u = units(anchorKnown = { it == 0 })
        val jai = u.single { it.anchor == 0 }
        assertTrue(jai.known)
        assertNull(jai.gloss)
    }

    @Test fun missingMeaningIsEmptyNotInvented() {
        val u = Interlinear.units(listOf(Token("zut", "zut")), { null }, Lang.KO, { false }, true)
        assertEquals("", u.single().gloss)
    }

    @Test fun cellsKeepPunctuationAndJoinElisions() {
        val ranges = Vocab.align(text, tokens)
        val u = units()
        val cells = Interlinear.cells(text, ranges, u)
        val shown = cells.map { text.substring(it.range.first, it.range.last + 1) }
        assertTrue("Théo," in shown)
        assertTrue("j'ai" in shown)
        assertTrue("pour le moins" in shown)
        assertTrue("qu'au" in shown)
        assertTrue("voyais." in shown)
        // 글자를 빠짐없이 (띄어쓰기만 빼고) 덮는다
        assertEquals(text.replace(" ", ""), shown.joinToString("").replace(" ", ""))
    }

    /** 고정 이름은 덩어리 글자에서 (scripts/anchors.py anchor_id 와 같은 값): 목록 순서와 상관없다. */
    @Test fun anchorIdIsStableFromChunk() {
        assertEquals("114d634e", Anchors.id("vincent", listOf("j", "ai")))
        assertEquals("0f159d8b", Anchors.id("vincent_ko", listOf("수", "있어")))
        assertTrue(Anchors.id("vincent", listOf("j", "ai")) != Anchors.id("napoleon", listOf("j", "ai")))
        val swapped = Interlinear.units(tokens, { lex[it] }, Lang.KO, { false }, true, emptyList(), anchors.reversed())
        assertEquals("114d634e", swapped.single { it.kind == Interlinear.Kind.ANCHOR && it.from == 6 }.anchorId)
    }

    @Test fun anchorFindJoinsTokens() {
        assertEquals(listOf(1..2), Anchors.find(listOf("et", "j", "ai"), listOf("j", "ai")))
        assertEquals(listOf(0..0), Anchors.find(listOf("jai"), listOf("j", "ai")))
        assertTrue(Anchors.find(listOf("j", "avais"), listOf("j", "ai")).isEmpty())
        assertEquals(listOf("pour", "le", "moins"), Anchors.keyOf("pour le moins"))
    }

    @Test fun shortGloss() {
        assertEquals("사랑하는", Interlinear.short("사랑하는, 소중한"))
        assertEquals("~ 동안", Interlinear.short("~ 동안"))
        assertTrue(Interlinear.short("a very long meaning that goes on").length <= Interlinear.GLOSS_MAX)
    }

    @Test fun warmUpPicksEarliestUnknownWordsAndOneChunk() {
        val words = listOf(30 to false, 5 to false, 12 to true, 8 to false, 20 to false, Int.MAX_VALUE to false)
        val got = WarmUp.pick(words, phrases = listOf(3 to 9), anchors = listOf(4 to 7))
        assertEquals(listOf(WarmUp.Item(WarmUp.Kind.WORD, 1), WarmUp.Item(WarmUp.Kind.WORD, 3), WarmUp.Item(WarmUp.Kind.WORD, 4),
            WarmUp.Item(WarmUp.Kind.ANCHOR, 4)), got)
        // 닻이 없으면 짧은 표현, 그것도 없으면 낱말 넷
        assertEquals(WarmUp.Item(WarmUp.Kind.PHRASE, 0), WarmUp.pick(words, listOf(3 to 9), emptyList()).last())
        assertEquals(4, WarmUp.pick(words, listOf(9 to 9), emptyList()).count { it.kind == WarmUp.Kind.WORD })
        // 닻이 여럿이면 편지에 가장 먼저 나오는 것, 같은 자리면 목록 앞 (쓸모 순: j'ai 가 j'ai fait 보다 앞)
        assertEquals(WarmUp.Item(WarmUp.Kind.ANCHOR, 2), WarmUp.pick(words, emptyList(), listOf(5 to 40, 2 to 11, 9 to 30)).last())
        assertEquals(WarmUp.Item(WarmUp.Kind.ANCHOR, 1), WarmUp.pick(words, emptyList(), listOf(1 to 11, 14 to 11)).last())
        // 한 장뿐이면 예열 없이
        assertTrue(WarmUp.pick(listOf(1 to false), emptyList(), emptyList()).isEmpty())
    }

    @Test fun warmUpDefaultsByBand() {
        assertTrue(WarmUp.on(null, Reading.Band.A1))
        assertTrue(WarmUp.on(null, Reading.Band.A2))
        assertTrue(!WarmUp.on(null, Reading.Band.B1))
        assertTrue(WarmUp.on(true, Reading.Band.B2))
        assertTrue(!WarmUp.on(false, Reading.Band.A1))
    }
}
