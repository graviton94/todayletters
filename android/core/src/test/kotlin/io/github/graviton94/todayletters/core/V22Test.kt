package io.github.graviton94.todayletters.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v22 (APK #18 검토): 할 일 차례 · 힌트 받은 받아쓰기 · 언어마다 진도 · 문장 짝 · 소리 파일 이름 · 따옴표 · 낭독 칠하기 시각. */
class V22Test {
    @Test fun questsInFixedOrder() {
        assertEquals(listOf(Quest.LETTER, Quest.MEANING_RUN, Quest.DICTATION_RUN), Quests.today(20000))
        assertEquals(Quests.today(1), Quests.today(99999))
        var log = QuestLog()
        Quests.today(5).forEach { log = log.atLeast(5, it, it.goal) }
        assertTrue(log.payable(5))
        assertFalse(QuestLog().add(5, Quest.LETTER).payable(5))
    }

    @Test fun hintedDictationCountsAsRight() {
        // 힌트로 첫 글자를 보여 줬고 나머지만 썼다
        assertEquals(Memory.Mark.RIGHT, Memory.gradeHinted("kommen", "ommen", 1))
        assertEquals(Memory.Mark.RIGHT, Memory.gradeHinted("kommen", "kommen", 1))
        assertEquals(Memory.Mark.ACCENT, Memory.gradeHinted("pensé", "ense", 1))
        assertEquals(Memory.Mark.WRONG, Memory.gradeHinted("kommen", "omen", 1))
        // 힌트가 없으면 예전 채점 그대로
        assertEquals(Memory.Mark.WRONG, Memory.gradeHinted("kommen", "ommen", 0))
    }

    @Test fun progressKeysPerLanguage() {
        assertEquals("vincent", Spaces.of("vincent", Lang.FR, Lang.FR))
        assertEquals("vincent@en", Spaces.of("vincent", Lang.EN, Lang.FR))
        assertEquals("mozart@ko", Spaces.of("mozart", Lang.KO, Lang.DE))
        assertEquals("vincent" to "en", Spaces.parse("vincent@en"))
        assertEquals("vincent" to null, Spaces.parse("vincent"))
        assertEquals("vincent@en:I:c1-l2", Spaces.move("vincent:I:c1-l2", "vincent", "vincent@en"))
        assertEquals("vincent@ko", Spaces.move("vincent", "vincent", "vincent@ko"))
        assertNull(Spaces.move("vincents:I:x", "vincent", "vincent@en"))
        assertNull(Spaces.move("mozart:I:x", "vincent", "vincent@en"))
        // 언어마다 다른 열쇠: 한 언어의 진도가 다른 언어로 번지지 않는다
        val fr = "${Spaces.of("vincent", Lang.FR, Lang.FR)}:I:c1-l1"
        val en = "${Spaces.of("vincent", Lang.EN, Lang.FR)}:I:c1-l1"
        assertTrue(fr != en && !en.startsWith("vincent:"))
    }

    @Test fun vocabAudioNames() {
        assertEquals("_4c_eben", VocabAudio.slug("Leben"))
        assertEquals("c_f4_t_e9_", VocabAudio.slug("côté"))
        assertEquals("ton_23_2", VocabAudio.slug("ton#2"))
        assertEquals("_b204__b098_", VocabAudio.slug("누나"))
        assertEquals("avant-hier", VocabAudio.slug("avant-hier"))
        assertEquals("v_de__4c_eben", VocabAudio.word("Leben", Lang.DE))
        assertEquals("ph1_en", VocabAudio.phrase(0, Lang.EN))
    }

    @Test fun quotesFollowTheLanguage() {
        assertEquals("“I get no rest”", Quote.wrap("I get no rest", Lang.EN))
        assertEquals("„Lieber Vater“", Quote.wrap("Lieber Vater", Lang.DE))
        assertEquals("« mon cher »", Quote.wrap("mon cher", Lang.FR))
        assertEquals("“누나”", Quote.wrap("누나", Lang.KO))
    }

    @Test fun phraseCardKeepsSourceAndSound() {
        val w = Vocab.phraseWord(Phrase("I get no rest", 2, "쉴 틈이 없어", "I get no rest", "", ""), Lang.EN, 1)
        assertEquals(2, w.msg); assertEquals("ph2", w.audio); assertTrue(w.phrase)
    }

    @Test fun spokenWaitsForTheVoice() {
        // 마디 시작 3268 ms: 목소리는 그보다 늦게 나온다
        assertEquals(0, Spoken.at(listOf(200, 3268), 3300, Spoken.LEAD_MS))
        assertEquals(1, Spoken.at(listOf(200, 3268), 3800, Spoken.LEAD_MS))
        assertEquals(0, Spoken.at(listOf(200, 3268), 0, Spoken.LEAD_MS))
    }

    @Test fun sentencePairs() {
        val learn = "Mich freut es recht. Ich wünsche Dir tausend Gelegenheiten!"
        val same = Pairs.of(learn, "I rejoice. I wish you a thousand chances!")
        assertEquals(2, same.size)
        assertEquals("Mich freut es recht.", learn.substring(same[0].first)); assertEquals("I rejoice.", same[0].second)
        assertEquals("Ich wünsche Dir tausend Gelegenheiten!", learn.substring(same[1].first))
        // 번역 문장이 더 많으면 차례대로 나누어 붙는다 (빠지는 문장 없이)
        val more = Pairs.of(learn, "정말 기뻐. 누나한테. 즐거울 일이 천 번은 더 생기면 좋겠어!")
        assertEquals(2, more.size)
        assertEquals("정말 기뻐. 누나한테. 즐거울 일이 천 번은 더 생기면 좋겠어!", more.joinToString(" ") { it.second })
        // 번역이 더 적으면 배우는 말 문장을 묶는다
        val fewer = Pairs.of(learn, "모두 기뻐!")
        assertEquals(1, fewer.size); assertEquals(learn, learn.substring(fewer[0].first))
        // 한 문장이면 그대로 한 짝
        assertEquals(1, Pairs.of("Une seule phrase longue.", "One. Two.").size)
        assertEquals("", Pairs.of(learn, null)[0].second)
        assertEquals(listOf(0 until 20, 21 until learn.length), Pairs.sentences(learn))
        // 실제 편지 (모차르트 I-1 첫 줄): 한 문장 원문 · 두 문장 한국어 → 마디를 묶어 두 짝
        val de = "Mich freut es recht von ganzem Herzen, daß Du bei der Schlittenfahrt, von der Du mir schreibst, Dich so sehr ergötzt hast, " +
            "und ich wünsche Dir tausend Gelegenheiten zur Ergötzung, damit Du recht lustig Dein Leben zubringen mögtest."
        val ko = "누나가 편지에 쓴 썰매 타기가 그렇게 즐거웠다니 진심으로 기뻐. 누나한테 즐거울 일이 천 번은 더 생겨서, 평생 신나게 살면 좋겠어."
        val two = Pairs.of(de, ko)
        assertEquals(2, two.size)
        assertTrue(de.substring(two[0].first).endsWith("ergötzt hast,"))
        assertTrue(two[1].second.startsWith("누나한테"))
        // 도착 화면: 둘 다 한 문장이어도 마디끼리
        val en = "I rejoice in my heart that you were so well amused at the sledging party you write to me about, and I wish you a thousand opportunities of pleasure, so that you may pass your life merrily."
        val three = Pairs.of(de, en, clauses = true)
        assertEquals(3, three.size)
        assertTrue(de.substring(three[0].first).endsWith("ergötzt hast,"))
        assertTrue(three[2].second.startsWith("so that"))
        assertEquals(1, Pairs.of(de, en).size)
    }
}
