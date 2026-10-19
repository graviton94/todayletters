package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VocabTest {
    private fun tri(fr: String, en: String, ko: String) = Tri(mapOf(Lang.FR to fr, Lang.EN to en, Lang.KO to ko))
    private val lex = Lexicon(Lang.FR, listOf(
        LexEntry("maladie", forms = listOf("maladie"), pos = "n", gender = "f", ko = "병", en = "illness", ipa = "maladi", level = "B1"),
        LexEntry("jardin", forms = listOf("jardin", "jardins"), pos = "n", gender = "m", ko = "정원", en = "garden", ipa = "ʒaʁdɛ̃", level = "A2"),
        LexEntry("ton", id = "ton#2", forms = listOf("ton"), pos = "n", gender = "m", ko = "색조", en = "tone", level = "B1"),
        LexEntry("fleur", forms = listOf("fleurs"), pos = "n", gender = "f", ko = "꽃", en = "flower", level = "A2"),
        LexEntry("de", pos = "prep", ko = "~의", en = "of", level = "A1", function = true),
    ))
    private val chapterWords = listOf(
        Word(tri("jardin", "garden", "정원"), "n.m.", "ʒaʁ.dɛ̃", audio = "w1"),
        Word(tri("lampe", "lamp", "램프"), "n.f.", audio = "w2"),
    )
    private fun letter(words: List<Word> = chapterWords) = Letter("c1-l1", "1888", "Arles", "calm", emptyList(), words, null,
        Plate(tri("t", "t", "t"), "1888", "x", spots = listOf(
            Spot(0f, 0f, tri("a", "a", "a"), tri("n", "n", "n"), Word(tri("iris", "iris", "붓꽃")), 0),
            Spot(0f, 0f, tri("b", "b", "b"), tri("n", "n", "n"), Word(tri("Vincent", "Vincent", "빈센트")), 0),
            Spot(0f, 0f, tri("c", "c", "c"), tri("n", "n", "n"), Word(tri("ciel", "sky", "하늘")), 0),
        )))

    @Test fun dailyWordsReplaceChapterWordsAndKeepAudioWhenTheyMatch() {
        val daily = Daily(words = listOf("maladie", "jardin", "jardin", "ton#2", "missing"))
        val w = Vocab.deckWords(daily, chapterWords, lex, Lang.FR)
        assertEquals(listOf("maladie", "jardin", "ton"), w.map { it.text[Lang.FR] })
        assertEquals("", w[0].audio)            // 편지에 낭독이 없는 낱말
        assertEquals("w1", w[1].audio)          // 챕터 낱말과 같으면 그 낭독 · 번역을 그대로
        assertEquals("B1", w[0].level)
        assertEquals("ton#2", w[2].lex)
        assertEquals("n.f.", w[0].pos)
    }

    @Test fun chapterWordsAreTheFallback() {
        assertEquals(chapterWords, Vocab.deckWords(null, chapterWords, lex, Lang.FR))
        assertEquals(chapterWords, Vocab.deckWords(Daily(), chapterWords, lex, Lang.FR))
        assertEquals(chapterWords, Vocab.deckWords(Daily(words = listOf("maladie")), chapterWords, null, Lang.FR))
        assertEquals(chapterWords, Vocab.deckWords(Daily(words = listOf("nothing")), chapterWords, lex, Lang.FR))
        val l = Vocab.apply(letter(), null, lex, Lang.FR)
        assertEquals(chapterWords, l.words)
        assertEquals(chapterWords, l.source)
    }

    @Test fun applySetsPhrasesAndSpots() {
        val daily = Daily(words = listOf("maladie"),
            spots = listOf(DailySpot(0, "fleur", "iris"), DailySpot(1, null, "Vincent")),
            phrases = listOf(Phrase("tout de même", 2, "그래도", "all the same", "양보할 때", "Conceding")))
        val l = Vocab.apply(letter(), daily, lex, Lang.FR)
        assertEquals(listOf("maladie"), l.words.map { it.lex })
        assertEquals(chapterWords, l.source)
        assertEquals("tout de même", l.phrases.single().text[Lang.FR])
        assertTrue(l.phrases.single().phrase)
        assertEquals("Conceding", l.phrases.single().note!![Lang.EN])
        val spots = l.plate!!.spots
        assertEquals("fleur", spots[0].word!!.text[Lang.FR])
        assertEquals(false, spots[0].voiced)
        assertNull(spots[1].word)
        assertEquals("ciel", spots[2].word!!.text[Lang.FR])
        assertEquals(true, spots[2].voiced)
    }

    @Test fun germanNounsGetTheirArticle() {
        val e = LexEntry("Brief", forms = listOf("Brief", "Briefe"), pos = "n", gender = "m", ko = "편지", en = "letter")
        assertEquals("der Brief", Vocab.display(e, Lang.DE))
        val chapter = Word(Tri(mapOf(Lang.DE to "der Brief", Lang.EN to "letter", Lang.KO to "편지")), audio = "w1")
        assertTrue(Vocab.same(chapter, e, Lang.DE))
    }

    @Test fun tokensAlignOnWordBoundaries() {
        val text = "Mais il a fait l'été, aujourd'hui."
        val tokens = listOf(Token("Mais", "mais"), Token("il", "il"), Token("a", "avoir"), Token("fait", "faire"), Token("l'", "le"), Token("été", "été"), Token("aujourd'hui", "aujourd'hui"))
        val r = Vocab.align(text, tokens)
        assertEquals(listOf("Mais", "il", "a", "fait", "l'", "été", "aujourd'hui"), r.map { text.substring(it.first, it.last + 1) })
        assertTrue(Vocab.align("abc", listOf(Token("zz", "zz")))[0].isEmpty())
    }

    @Test fun cardKeysParse() {
        assertEquals(CardRef("vincent", "I", "c1-l1", index = 3), CardRef.parse("vincent:I:c1-l1:3"))
        assertEquals(CardRef("vincent", "I", "c1-l1", phrase = 1), CardRef.parse(CardRef.phrase("vincent", "I", "c1-l1", 1)))
        assertEquals(CardRef("mozart", "P", "p1", own = "ton#2"), CardRef.parse(CardRef.own("mozart", "P", "p1", "ton#2")))
        // 닻 카드: 꼬리 a<고정 이름> (목록 번호가 아님), 예전 번호 꼬리 a<n> 은 카드가 아니다
        assertEquals(CardRef("vincent", "I", "c1-l2", anchor = "114d634e", deck = "en"), CardRef.parse(CardRef.anchor("vincent", "I", "c1-l2", "114d634e", "en")))
        assertEquals("vincent:I:c1-l2:a114d634e", CardRef.anchor("vincent", "I", "c1-l2", "114d634e"))
        assertNull(CardRef.parse("vincent:I:c1-l2:a3"))
        assertNull(CardRef.parse("vincent:I:c1-l1"))
        assertNull(CardRef.parse("vincent:I:c1-l1:x"))
    }

    @Test fun newCardsAreCappedAndTheRestWaits() {
        val fresh = (1..20).map { "k$it" }
        val (now, later) = Intake.split(fresh, addedToday = 0)
        assertEquals(15, now.size)
        assertEquals(listOf("k16", "k17", "k18", "k19", "k20"), later)
        val (now2, later2) = Intake.split(later + listOf("k21"), addedToday = 13)
        assertEquals(listOf("k16", "k17"), now2)
        assertEquals(listOf("k18", "k19", "k20", "k21"), later2)
        assertEquals(emptyList(), Intake.split(listOf("a"), addedToday = 15).first)
        assertEquals(listOf("a"), Intake.split(listOf("a", "a"), addedToday = 0).first)
    }

    @Test fun cardKeysCarryTheLearnLanguageDeck() {
        assertEquals("vincent:I:c1-l1:3", CardRef.word("vincent", "I", "c1-l1", 3))
        assertEquals("vincent:I:c1-l1:en/3", CardRef.word("vincent", "I", "c1-l1", 3, "en"))
        assertEquals(CardRef("vincent", "I", "c1-l1", index = 3, deck = "en"), CardRef.parse("vincent:I:c1-l1:en/3"))
        assertEquals(CardRef("vincent", "I", "c1-l1", phrase = 0, deck = "ko"), CardRef.parse(CardRef.phrase("vincent", "I", "c1-l1", 0, "ko")))
        assertEquals(CardRef("mozart", "P", "p1", own = "ton#2", deck = "en"), CardRef.parse(CardRef.own("mozart", "P", "p1", "ton#2", "en")))
        assertEquals("", CardRef.deckOf("vincent:I:c1-l1:p1"))
        assertEquals("ko", CardRef.deckOf("vincent:I:c1-l1:ko/+정원"))
    }

    @Test fun englishAndKoreanDecks() {
        // 영어 사전: 표제어가 영어 줄, 뜻은 한국어
        val g = LexEntry("garden", forms = listOf("garden"), pos = "n", ko = "정원", en = "", level = "A2")
        val w = Vocab.word(g, Lang.EN)
        assertEquals("garden", w.text[Lang.EN])
        assertEquals("정원", Breaks.plain(w.text[Lang.KO]))
        // 챕터 낱말과 같으면 그 낭독 그대로, 원어 철자(modern)로는 맞추지 않는다
        val l = Vocab.apply(letter(), Daily(words = listOf("garden")), Lexicon(Lang.EN, listOf(g)), Lang.EN, Lang.FR)
        assertEquals("w1", l.words.single().audio)
        assertEquals("garden", l.words.single().lex)
        // 한국어 사전: 표제어가 한국어 줄, 뜻은 영어
        val k = LexEntry("정원", ko = "", en = "garden")
        assertEquals("정원", Breaks.plain(Vocab.word(k, Lang.KO).text[Lang.KO]))
        assertEquals("garden", Vocab.word(k, Lang.KO).text[Lang.EN])
        // 뜻풀이 언어: 앱 글자 언어, 배우는 말과 같으면 다른 하나
        assertEquals(Lang.KO, Vocab.glossLang(Lang.KO, Lang.EN))
        assertEquals(Lang.EN, Vocab.glossLang(Lang.KO, Lang.KO))
        assertEquals(Lang.KO, Vocab.glossLang(Lang.EN, Lang.EN))
        assertEquals(Lang.EN, Vocab.glossLang(Lang.EN, Lang.FR))
        assertEquals("garden", Vocab.meaning(k, Lang.KO))   // 한국어 뜻이 비면 영어
        assertEquals(VocabSet.deckFor(Lang.FR, Lang.FR), "")
        assertEquals(VocabSet.deckFor(Lang.KO, Lang.FR), "ko")
    }
}
