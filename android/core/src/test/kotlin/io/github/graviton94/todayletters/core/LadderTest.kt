package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LadderTest {
    @Test fun helpShrinksAsTheLevelRises() {
        assertEquals(Ladder.Help.LINK, Ladder.help(Reading.Grade.of(1)))
        assertEquals(Ladder.Help.CHOOSE, Ladder.help(Reading.Grade.of(2)))
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

    @Test fun replyCardKeyCarriesTheTurn() {
        // 턴 0 은 예전 열쇠 그대로 ("r"), 그 뒤 턴은 "r1" · "r2"
        assertEquals(CardRef.reply("vincent", "I", "c1-l1"), CardRef.reply("vincent", "I", "c1-l1", turn = 0))
        val k = CardRef.reply("vincent", "III", "c3-l1", "ko", turn = 1)
        assertEquals("vincent:III:c3-l1:ko/r1", k)
        val ref = CardRef.parse(k)!!
        assertTrue(ref.reply); assertEquals(1, ref.turn); assertFalse(ref.style)
        assertEquals(0, CardRef.parse("vincent:I:c1-l1:r")!!.turn)
        val st = CardRef.parse(CardRef.style("vincent", "III", "c3-l1", "ko", turn = 2))!!
        assertTrue(st.style); assertFalse(st.reply); assertEquals(2, st.turn); assertEquals("ko", st.deck)
        // 표현 · 낱말 열쇠는 그대로
        assertEquals(3, CardRef.parse(CardRef.phrase("vincent", "I", "c1-l1", 3))!!.phrase)
        assertEquals(12, CardRef.parse(CardRef.word("vincent", "I", "c1-l1", 12))!!.index)
    }

    private fun tri(fr: String, en: String, ko: String) = Tri(mapOf(Lang.FR to fr, Lang.EN to en, Lang.KO to ko))
    private val turns = listOf(
        ReplyTurn(0, "III.0", tri("J'ai lu ta lettre deux fois, et j'ai pensé à tes nuits de travail.", "I read your letter twice, and I thought about your nights of work.",
            "형 편지를 두 번 읽었어요. 밤새 그렸다는 얘기에 형 생각을 많이 했어요."),
            koLow = "형 편지를 두 번 읽었어. 밤새 그렸다는 얘기에 형 생각을 많이 했어.",
            koHigh = "형님 편지를 두 번 읽었습니다. 밤새 그리셨다는 말씀에 형님 생각을 많이 했습니다.",
            focus = mapOf(Lang.FR to listOf("ai lu", "pensé"), Lang.EN to listOf("read", "thought"), Lang.KO to listOf("읽었어요")),
            wrong = mapOf(Lang.FR to listOf(listOf("lis", "lirai"), listOf("pense", "penser")), Lang.EN to listOf(listOf("reads", "reading"), listOf("think", "thinks")),
                Lang.KO to listOf(listOf("읽어요", "읽을게요")))),
        ReplyTurn(2, "II.1", tri("Tu as choisi des couleurs très fortes.", "You chose very strong colours.", "형이 아주 강한 색을 골랐네요.")),
    )
    private val letter = Letter("c3-l1", "1888", "Arles", "calm",
        listOf(Message(tri("a", "a", "a")), Message(tri("b", "b", "b")), Message(tri("c", "c", "c"))), emptyList(), null, null, replies = turns)

    @Test fun turnsPlayInOrderAndOldLettersGetOne() {
        assertEquals(2, Exercises.turns(letter, Lang.FR).size)
        assertEquals("J'ai lu ta lettre deux fois, et j'ai pensé à tes nuits de travail.", Exercises.replyFor(letter, Lang.FR, 0).first)
        assertEquals("Tu as choisi des couleurs très fortes.", Exercises.replyFor(letter, Lang.FR, 1).first)
        assertEquals("형이 아주 강한 색을 골랐네요.", Exercises.replyFor(letter, Lang.KO, 1).first)
        // 턴 번호가 넘치면 마지막 턴
        assertEquals("Tu as choisi des couleurs très fortes.", Exercises.replyFor(letter, Lang.FR, 9).first)
        assertTrue(turns[0].styled); assertFalse(turns[1].styled)
        // 저자 답장이 없는 편지: 짧은 메시지 하나가 한 턴 (인용 · 문법 없음)
        val old = letter.copy(replies = emptyList(), messages = listOf(Message(tri("Mon cher Théo, je pense à toi.", "x", "x"))))
        val t = Exercises.turns(old, Lang.FR)
        assertEquals(1, t.size); assertEquals(-1, t[0].quote); assertEquals("", t[0].grammar)
    }

    @Test fun grammarIsFoundAcrossChapters() {
        val g3 = Grammar("III.0", mapOf(Lang.FR to GrammarLine(Tri(mapOf(Lang.KO to "복합과거", Lang.EN to "Passé composé")), Tri(mapOf(Lang.KO to "지난 일", Lang.EN to "the past")))))
        val g2 = Grammar("II.1", emptyMap())
        val chapters = listOf(Chapter("vincent", "II", tri("", "", ""), true, emptyList(), listOf(g2)), Chapter("vincent", "III", tri("", "", ""), false, listOf(letter), listOf(g3)))
        assertEquals(g3, chapters.grammar("III.0"))
        assertEquals(g2, chapters.grammar("II.1"))   // 앞 장 문법 복습
        assertEquals(null, chapters.grammar(""))
        assertEquals("Passé composé", chapters.grammar("III.0")!!.line(Lang.FR)!!.name[Lang.EN])
        assertEquals(null, g3.line(Lang.DE))
    }

    @Test fun focusChunksBecomeTheBlanks() {
        val fr = turns[0].text[Lang.FR]
        val score: (String) -> Int = { if (it.length > 3) it.length else 0 }
        // 빈칸 하나: 첫 덩어리 · 빈칸 둘 (B1): 두 덩어리 다
        assertEquals(listOf("ai lu"), Ladder.holes(fr, turns[0].focus[Lang.FR]!!, 1, score).map { fr.substring(it) })
        assertEquals(listOf("ai lu", "pensé"), Ladder.holes(fr, turns[0].focus[Lang.FR]!!, 2, score).map { fr.substring(it) })
        // 한국어: 덩어리가 하나뿐이면 나머지는 낱말 점수로 (겹치지 않게)
        val ko = turns[0].text[Lang.KO]
        val h = Ladder.holes(ko, turns[0].focus[Lang.KO]!!, 2, score).map { ko.substring(it) }
        assertEquals(2, h.size); assertTrue("읽었어요" in h)
        // 덩어리가 없거나 문장에 없으면 예전처럼 낱말 점수로
        assertEquals(Ladder.holes(fr, emptyList(), 1, score), Ladder.holes(fr, listOf("nulle part"), 1, score))
        assertEquals(listOf("travail"), Ladder.holes(fr, emptyList(), 1, score).map { fr.substring(it) })
        // 낱말 경계: "lu" 는 "lumière" 안이 아니라 낱말로 찾는다
        assertEquals("lu", "La lumière, je l'ai lu.".let { s -> s.substring(Ladder.find(s, "lu")!!) })
        assertEquals(20, Ladder.find("La lumière, je l'ai lu.", "lu")!!.first)
    }

    @Test fun layoutKeepsTheBlankWithItsWord() {
        val fr = "J'ai lu ta lettre."
        val holes = listOf(Ladder.find(fr, "ai lu")!!)
        val units = Ladder.layout(fr, holes)
        // "J'" 와 빈칸 "ai lu" 는 한 묶음 (붙어서), 그다음 "ta" · "lettre."
        assertEquals(listOf(Ladder.Piece("J'"), Ladder.Piece("ai lu", 0)), units[0])
        assertEquals(listOf(listOf(Ladder.Piece("ta")), listOf(Ladder.Piece("lettre."))), units.drop(1))
        assertEquals(listOf(listOf(Ladder.Piece("a")), listOf(Ladder.Piece("b"))), Ladder.layout(" a  b ", emptyList()))
    }

    @Test fun focusDecoysAreOtherFormsOfTheSameWord() {
        val lex = mapOf("lu" to listOf("lire", "lis", "lu"), "pensé" to listOf("penser", "pense", "pensé"))
        val near = Ladder.nearForms("ai lu") { lex[it].orEmpty() }
        assertEquals(listOf("lire", "lis"), near)   // 두 글자 낱말 (lu) 혼자는 미끼로 너무 짧다
        val c = Ladder.chips("pensé", Ladder.nearForms("pensé") { lex[it].orEmpty() }, listOf("maison"), seed = 1)
        assertEquals(setOf("pensé", "penser", "pense"), c.toSet())
    }

    @Test fun authoredWrongOptionsComeFirst() {
        val fr = turns[0].text[Lang.FR]
        val focus = turns[0].focus[Lang.FR]!!
        val wrong = turns[0].wrong[Lang.FR]!!
        val score: (String) -> Int = { if (it.length > 3) it.length else 0 }
        // 빈칸 하나: 정답 + 그 덩어리의 틀린 보기 둘
        val one = Ladder.holes(fr, focus, 1, score)
        val a1 = one.map { fr.substring(it) }
        val w1 = Ladder.wrongsFor(fr, one, focus, wrong)
        assertEquals(listOf(listOf("lis", "lirai")), w1)
        val c1 = Ladder.authored(a1, w1, 0, two = false, seed = 3)!!
        assertEquals(setOf("ai lu", "lis", "lirai"), c1.toSet()); assertEquals(3, c1.size)
        assertEquals(c1, Ladder.authored(a1, w1, 0, two = false, seed = 3))
        // 빈칸 둘 (B1): 두 정답 + 두 덩어리의 틀린 보기, 겹치면 하나로
        val two = Ladder.holes(fr, focus, 2, score)
        val a2 = two.map { fr.substring(it) }
        val w2 = Ladder.wrongsFor(fr, two, focus, wrong)
        assertEquals(setOf("ai lu", "pensé", "lis", "lirai", "pense", "penser"), Ladder.authored(a2, w2, 1, two = true, seed = 1)!!.toSet())
        assertEquals(setOf("ai lu", "pensé", "lis", "penser"), Ladder.authored(a2, listOf(listOf("lis", "pensé"), listOf("Lis", "penser")), 0, two = true, seed = 1)!!.toSet())
        // 적힌 보기가 없으면 null (사전 꼴 미끼로): wrong 이 없는 턴 · 덩어리가 아닌 빈칸
        assertEquals(null, Ladder.authored(a1, Ladder.wrongsFor(fr, one, focus, emptyList()), 0, two = false, seed = 3))
        val loose = Ladder.holes(fr, emptyList(), 1, score)
        assertEquals(listOf<List<String>?>(null), Ladder.wrongsFor(fr, loose, focus, wrong))
        // 한국어: 덩어리 하나 + 낱말 점수 빈칸 하나 → 덩어리 빈칸만 적힌 보기
        val ko = turns[0].text[Lang.KO]
        val kh = Ladder.holes(ko, turns[0].focus[Lang.KO]!!, 1, score)
        assertEquals(setOf("읽었어요", "읽어요", "읽을게요"),
            Ladder.authored(kh.map { ko.substring(it) }, Ladder.wrongsFor(ko, kh, turns[0].focus[Lang.KO]!!, turns[0].wrong[Lang.KO]!!), 0, false, 2)!!.toSet())
    }

    @Test fun styleOptionsHoldTheTargetTheOtherAndAMix() {
        val t = turns[0]
        val o = Ladder.styleOptions(t.koLow, t.koHigh, 5)
        assertEquals(3, o.size)
        assertTrue(t.koLow in o); assertTrue(t.koHigh in o)
        assertTrue("형 편지를 두 번 읽었어. 밤새 그리셨다는 말씀에 형님 생각을 많이 했습니다." in o)
        assertEquals(o, Ladder.styleOptions(t.koLow, t.koHigh, 5))
        // 한 문장이면 둘만
        assertEquals(2, Ladder.styleOptions("고마워.", "고맙습니다.", 1).size)
        // 날마다 번갈아 묻는다
        assertTrue(Ladder.askLow("k", 0) != Ladder.askLow("k", 1))
    }
}
