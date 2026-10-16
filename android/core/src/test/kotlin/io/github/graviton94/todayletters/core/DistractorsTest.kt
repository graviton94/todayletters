package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 뜻 고르기의 틀린 보기 (같은 갈래 · 같은 작품 · 같은 뜻 빼기) 와 문장 나누기의 줄임말. */
class DistractorsTest {
    private fun w(t: String, pos: String = "n", key: String = t, g: String = "mozart") = Distractors.Item(t, Distractors.Kind.WORD, pos, key, g)
    private fun ph(t: String, g: String = "mozart") = Distractors.Item(t, Distractors.Kind.PHRASE, "", t, g)
    private fun rep(t: String, g: String = "mozart") = Distractors.Item(t, Distractors.Kind.SENTENCE, "", "", g)

    @Test fun wordGetsOnlyWordMeanings() {
        val pool = listOf(
            rep("새 아리아가 또 하나 있어요! 정말 자랑스러워요."), ph("다시 보았다"), ph("편지를 쓰다"),
            w("집"), w("편지"), w("아버지"), w("기쁨"), w("하늘"),
        )
        val got = Distractors.pick(w("누나"), pool, 3, 1)
        assertEquals(3, got.size)
        assertTrue(got.none { it.contains("아리아") || it == "다시 보았다" || it == "편지를 쓰다" }, got.toString())
    }

    @Test fun phraseGetsPhrasesAndReplyGetsSentences() {
        val pool = listOf(w("집"), w("편지"), ph("다시 보았다"), ph("편지를 쓰다"), ph("마음에 들다"), rep("오늘은 날씨가 좋아요."), rep("곧 다시 쓸게요."))
        assertEquals(setOf("다시 보았다", "편지를 쓰다", "마음에 들다"), Distractors.pick(ph("잘 지내다"), pool, 3, 2).toSet())
        assertEquals(setOf("오늘은 날씨가 좋아요.", "곧 다시 쓸게요."), Distractors.pick(rep("정말 기뻐요!"), pool, 3, 2).toSet())
    }

    @Test fun neverSameSenseSameWordEmptyOrOtherWork() {
        val pool = listOf(
            w("loved, dear", key = "dear"),     // 다른 편지의 같은 낱말
            w("beloved; precious", key = "beloved"),  // 정답 뜻 하나와 겹침 (precious)
            w(""), w("   "), w("house"), w("House"), w("letter"),
            w("sky", g = "vincent"),            // 다른 작품
            w("joy"),
        )
        val got = Distractors.pick(w("precious, dear", "adj", key = "dear"), pool, 3, 3)
        assertEquals(setOf("letter", "joy"), got.filter { it != "house" && it != "House" }.toSet())
        assertEquals(1, got.count { it.equals("house", ignoreCase = true) })
        assertFalse(got.any { it.isBlank() || it == "sky" })
    }

    @Test fun prefersSamePosAndSimilarLength() {
        val pool = listOf(w("to write", "v"), w("to sing", "v"), w("to travel", "v"), w("house"), w("a very long noun meaning something else entirely"))
        assertEquals(setOf("to write", "to sing", "to travel"), Distractors.pick(w("to see", "v"), pool, 3, 4).toSet())
        // 같은 씨앗이면 같은 보기
        assertEquals(Distractors.pick(w("to see", "v"), pool, 3, 9), Distractors.pick(w("to see", "v"), pool, 3, 9))
    }

    // ── 줄임말: 문장 끝이 아니다 ──
    private fun split(t: String) = Pairs.sentences(t).map { t.substring(it) }
    private fun one(n: Int, t: String) = split(t).let { assertEquals(n, it.size, it.toString()) }

    @Test fun abbreviationsDoNotEndSentences() {
        assertEquals(listOf("I have just sketched St. Peter with his keys.", "And St. Paul too."), split("I have just sketched St. Peter with his keys. And St. Paul too."))
        one(1, "Mr. Tersteeg and Mrs. Roos met Dr. Gachet.")
        one(1, "J'ai vu M. Roulin et Mme Ginoux à St. Rémy.")
        one(1, "Je suis allé chez M. Tanguy.")
        one(1, "Der Impressario aber der Sig. Cassiglioni hat mich erst gefragt.")
        one(1, "Ich habe dem Hr. Cannabich geschrieben.")
        one(1, "des heil. Petrus Fuß zu S. Pietro zu küssen.")
        one(1, "Letter No. 5 came on 15 Oct. 1888.")
        one(1, "I met J. Roulin, the postman.")
        one(1, "Heute ist der 3. Juni.")
        one(1, "den 16. bin ich angekommen.")
        one(1, "Fruit, bread etc. and wine.")
    }

    @Test fun realSentenceEndsStillSplit() {
        one(3, "You love trees in blossom? So do I. Me too.")
        one(2, "So do I. Then we went home.")
        one(2, "He sent me 100 francs instead of 50. Then I left.")
        one(2, "I was born in 1853. Then we moved.")
        one(2, "Ich bin im Bett. Das ist gut.")
        one(2, "Il fait beau. Le ciel est bleu.")
        one(2, "It was Theo. He came.")
    }
}
