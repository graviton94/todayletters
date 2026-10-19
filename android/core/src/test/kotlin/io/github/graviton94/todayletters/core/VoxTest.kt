package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 말하기 · 소리 (2026-10-19): 음절 세기 · 또박또박 판정 · 어제 말 떠올리기 조각 · 내 이야기로 문장 · 이번 장의 소리. */
class VoxTest {
    private val rate = 16000

    /** 가짜 목소리: 200Hz 배음 셋, 크기는 [amp] (0 이면 침묵). */
    private fun voice(seconds: Double, amp: (Double) -> Double): FloatArray {
        val n = (seconds * rate).toInt(); var ph = 0.0
        return FloatArray(n) { k ->
            val t = k.toDouble() / rate
            ph += 2 * Math.PI * 200.0 / rate
            (amp(t) * (0.6 * Math.sin(ph) + 0.3 * Math.sin(2 * ph) + 0.1 * Math.sin(3 * ph))).toFloat()
        }
    }

    private fun syl(x: FloatArray) = Prosody.syllables(Prosody.analyze(x, rate))

    // ── 음절 세기 ──
    @Test fun fiveBurstsAreFiveSyllables() {
        // 150ms 소리 · 120ms 쉼 다섯 번
        val x = voice(1.5) { t -> if (t % 0.27 < 0.15 && t < 0.27 * 5) 0.5 else 0.0 }
        assertEquals(5, syl(x))
    }

    @Test fun oneLongBurstIsOne() {
        assertEquals(1, syl(voice(1.0) { t -> if (t in 0.1..0.9) 0.5 else 0.0 }))
    }

    @Test fun silenceIsZero() {
        assertEquals(0, syl(FloatArray(rate)))
        assertEquals(0, Prosody.syllables(Prosody.Track(FloatArray(0), FloatArray(0))))
    }

    @Test fun bumpsInOneVoicedStretch() {
        // 끊기지 않은 소리 안의 크기 봉우리 넷 (깊은 골)
        val x = voice(1.0) { t -> 0.08 + 0.42 * Math.pow(Math.sin(Math.PI * t * 4), 2.0) }
        assertEquals(4, syl(x))
    }

    @Test fun closeBurstsMergeUnder100ms() {
        // 20ms 쉼으로 떨어진 짧은 두 소리 (봉우리 사이 100ms 아래) 는 하나
        val x = voice(0.5) { t -> if (t in 0.10..0.16 || t in 0.18..0.24) 0.5 else 0.0 }
        assertEquals(1, syl(x))
    }

    // ── 판정 ──
    @Test fun judgeRule() {
        assertTrue(SpeakCheck.close(10, 10))
        assertTrue(SpeakCheck.close(14, 10)); assertTrue(SpeakCheck.close(6, 10))
        assertFalse(SpeakCheck.close(15, 10)); assertFalse(SpeakCheck.close(5, 10))
        // 짧은 말은 한 음절까지
        assertTrue(SpeakCheck.close(2, 1)); assertFalse(SpeakCheck.close(3, 1))
        // 원래를 모르면 따지지 않음
        assertTrue(SpeakCheck.close(3, 0))
        // 침묵은 언제나 아님 (또박또박 한 줄도 아님)
        assertEquals(SpeakCheck.Verdict(false, false), SpeakCheck.judge(false, 5, 5, 3))
        assertEquals(SpeakCheck.Verdict(true, false), SpeakCheck.judge(true, 5, 6, 0))
        assertEquals(SpeakCheck.Verdict(true, false), SpeakCheck.judge(true, 1, null, 0))
        // 음절이 어긋나면 첫 번째는 다시 (한 줄), 두 번째 실패는 통과 (막지 않음)
        assertEquals(SpeakCheck.Verdict(false, true), SpeakCheck.judge(true, 2, 10, 0))
        assertEquals(SpeakCheck.Verdict(true, false), SpeakCheck.judge(true, 2, 10, 1))
    }

    @Test fun estimateFromText() {
        assertEquals(6, SpeakCheck.estimate("나도 많이 배워!", Lang.KO))
        assertEquals(7, SpeakCheck.estimate("Moi aussi, j'apprends beaucoup !", Lang.FR))   // moi · au · ssi · ja · pprends · beau · coup
        // toutes · idée: 소리 나지 않는 e
        assertEquals(1, SpeakCheck.estimate("toutes", Lang.FR))
        assertEquals(2, SpeakCheck.estimate("idée", Lang.FR))
        assertEquals(1, SpeakCheck.estimate("je", Lang.FR))
        assertEquals(3, SpeakCheck.estimate("Ich habe", Lang.DE))
        assertEquals(2, SpeakCheck.estimate("Arbeit", Lang.DE))
        assertEquals(1, SpeakCheck.estimate("make", Lang.EN))
        assertEquals(2, SpeakCheck.estimate("table", Lang.EN))
        assertEquals(6, SpeakCheck.estimate("Me too, I'm learning lots!", Lang.EN))
    }

    // ── 어제 말 떠올리기 ──
    @Test fun previousStudyDay() {
        assertEquals(9L, Recall.previousDay(listOf(3L, 9L, 10L), 10L))
        assertNull(Recall.previousDay(listOf(10L), 10L))
    }

    @Test fun chunksOnPunctuationThenWordGroups() {
        assertEquals(listOf("Moi aussi,", "j'apprends beaucoup !"), Recall.chunks("Moi aussi, j'apprends beaucoup !"))
        assertEquals(listOf("Oui,", "ça va.", "Et toi ?"), Recall.chunks("Oui, ça va. Et toi ?"))
        // 문장부호가 없으면 낱말 묶음으로 2~4
        val c = Recall.chunks("Je vais à Paris demain avec mon frère")
        assertTrue(c.size in 2..4, "$c")
        assertEquals("Je vais à Paris demain avec mon frère", c.joinToString(" "))
        assertEquals(listOf("Merci"), Recall.chunks("Merci"))
        assertEquals(2, Recall.chunks("나도 배워!").size)
    }

    @Test fun quizTapsInOrder() {
        val st = Recall.start("Moi aussi, j'apprends beaucoup !", listOf("Oui, ça va. Et toi ?"), seed = 7)
        assertEquals(3, st.tiles.size)
        val d = st.tiles.first { t -> st.chunks.none { it == t } }
        var s = st
        st.chunks.forEach { c -> s = s.tap(s.tiles.indices.first { s.tiles[it] == c && it !in s.used }) }
        assertTrue(s.right)
        assertEquals("Moi aussi, j'apprends beaucoup !", s.built)
        // 틀린 조각을 누르면 그 자리에서 틀림
        val w = st.tap(st.tiles.indexOf(d))
        assertTrue(w.wrong && w.over && !w.right)
        // 차례가 아닌 맞는 조각도 틀림
        val second = st.tap(st.tiles.indexOf(st.chunks[1]))
        assertTrue(second.wrong)
    }

    @Test fun wrongLineComesBackOnce() {
        val (q1, a1) = Recall.after(listOf(0, 1), emptySet(), right = false)
        assertEquals(listOf(1, 0), q1)
        val (q2, a2) = Recall.after(q1, a1, right = true)
        assertEquals(listOf(0), q2)
        val (q3, _) = Recall.after(q2, a2, right = false)
        assertEquals(emptyList(), q3)
    }

    // ── 내 이야기로 ──
    private val talk = Talk(
        Tri(mapOf(Lang.EN to "")),
        listOf(
            TalkLine(false, Tri(mapOf(Lang.FR to "Le travail avance.", Lang.EN to "Work's going well.", Lang.KO to "작업이 잘돼."))),
            TalkLine(true, Tri(mapOf(Lang.FR to "Moi aussi, j'apprends beaucoup !", Lang.EN to "Me too, I'm learning lots!", Lang.KO to Breaks.keepAll("나도 많이 배워!")))),
        ),
        mine = TalkMine(1, mapOf(Lang.FR to "beaucoup", Lang.EN to "lots", Lang.KO to "많이"),
            listOf(mapOf(Lang.FR to "le français", Lang.EN to "French", Lang.KO to "프랑스어를"),
                mapOf(Lang.FR to "à peindre", Lang.EN to "to paint", Lang.KO to "그림 그리기를"),
                mapOf(Lang.FR to "à cuisiner", Lang.EN to "to cook"))),
    )

    @Test fun mineSentences() {
        assertEquals("Moi aussi, j'apprends le français !", Mine.sentence(talk, 0, Lang.FR))
        assertEquals("Me too, I'm learning French!", Mine.sentence(talk, 0, Lang.EN))
        assertEquals("나도 프랑스어를 배워!", Mine.sentence(talk, 0, Lang.KO))
        assertEquals("Moi aussi, j'apprends ＿＿＿ !", Mine.blanked(talk, Lang.FR))
        // 그 말에 보기가 없으면 그 보기는 뺀다
        assertNull(Mine.sentence(talk, 2, Lang.KO))
        assertEquals(listOf(0, 1), Mine.options(talk, Lang.KO))
        assertEquals(listOf(0, 1, 2), Mine.options(talk, Lang.FR))
        assertTrue(Mine.available(talk, Lang.FR))
        val tri = assertNotNull(Mine.tri(talk, 1, listOf(Lang.FR, Lang.EN, Lang.KO)))
        assertEquals("Me too, I'm learning to paint!", tri[Lang.EN])
        // 자리가 줄에 없거나 내 줄이 아니면 null · mine 이 없으면 하지 않음
        assertNull(Mine.sentence(talk.copy(mine = talk.mine!!.copy(slot = mapOf(Lang.FR to "rien"))), 0, Lang.FR))
        assertNull(Mine.sentence(talk.copy(mine = talk.mine!!.copy(line = 0)), 0, Lang.FR))
        assertFalse(Mine.available(talk.copy(mine = null), Lang.FR))
    }

    @Test fun mineCardKey() {
        val k = CardRef.mine("vincent", "I", "c1-l2", 2, "en")
        assertEquals("vincent:I:c1-l2:en/n2", k)
        val r = assertNotNull(CardRef.parse(k))
        assertEquals(2, r.mine); assertEquals("en", r.deck); assertFalse(r.reply)
        assertEquals(-1, CardRef.parse("vincent:I:c1-l2:3")!!.mine)
    }

    // ── 이번 장의 소리 ──
    private val soundsJson = """
        {"lang": "fr", "checked": false, "chapters": {
          "I": {"name": {"ko": "u 와 ou", "en": "u and ou"}, "tip": {"ko": "입술을 둥글게", "en": "Round your lips"},
                "pairs": [["tu", "tout"], ["vu", "vous"], ["rue", "roue"], ["dessus", "dessous"]],
                "mean": {"tu": {"ko": "너", "en": "you"}, "tout": {"ko": "모두", "en": "all"}}},
          "II": {"pairs": [["x"]]}
        }}
    """.trimIndent()

    @Test fun soundsDeck() {
        val d = Sounds.parse(soundsJson)
        assertEquals(Lang.FR, d.lang); assertFalse(d.checked)
        val c = assertNotNull(d.chapters["I"])
        assertEquals(4, c.pairs.size)
        assertEquals("tu" to "tout", c.pairs[0])
        assertEquals("너", c.meaning("tu", Lang.KO)); assertEquals("", c.meaning("vu", Lang.KO))
        assertEquals("Round your lips", c.tipIn(Lang.EN)); assertEquals("u 와 ou", c.nameIn(Lang.KO))
        // 쌍이 없는 장은 뺀다 · 없는 파일 · 깨진 파일은 빈 덱
        assertNull(d.chapters["II"])
        assertEquals(SoundDeck.EMPTY, Sounds.parse(null))
        assertEquals(SoundDeck.EMPTY, Sounds.parse("{nope"))
        assertEquals("s_fr_tu", Sounds.audio(Lang.FR, "tu"))
        assertEquals("s_de_" + VocabAudio.slug("Bett"), Sounds.audio(Lang.DE, "Bett"))
    }

    @Test fun soundsOncePerChapter() {
        val d = Sounds.parse(soundsJson)
        assertTrue(Sounds.due(d, "I", 0, emptySet()))
        assertFalse(Sounds.due(d, "I", 1, emptySet()))      // 첫 편지만
        assertFalse(Sounds.due(d, "I", 0, setOf("I")))      // 이미 본 장
        assertFalse(Sounds.due(d, "II", 0, emptySet()))     // 덱에 없는 장
        assertFalse(Sounds.due(d, "P", 0, emptySet()))      // 서문 · 맺음은 아님
    }

    @Test fun soundsRounds() {
        val r = Sounds.rounds(4, 42L)
        assertEquals(4, r.size)
        assertEquals(setOf(0, 1, 2, 3), r.map { it.pair }.toSet())
        assertEquals(r, Sounds.rounds(4, 42L))
        assertEquals(4, Sounds.rounds(2, 1L).size)
        assertTrue(Sounds.rounds(0, 1L).isEmpty())
        assertTrue(Sounds.passed(3)); assertTrue(Sounds.passed(4)); assertFalse(Sounds.passed(2))
    }

    // ── 비슷하게 읽으면 ──
    @Test fun sayLikeLookup() {
        val items = SayLike.parse("""{"lang": "fr", "auto": true, "items": {"Oui, ça va. Et toi ?": "위, 싸 바. 에 뚜아?", "Oui": "위", "ça": "싸", "va": "바", "Et": "에", "toi": "뚜아"}}""")
        assertEquals("위, 싸 바. 에 뚜아?", SayLike.lookup(items, "Oui, ça va. Et toi ?"))
        // 글이 없으면 낱말마다 (따라 읽기 마디) · 문장부호 앞 띄어쓰기 없이
        assertEquals("싸 바. 에 뚜아?", SayLike.lookup(items, "ça va. Et toi ?"))
        assertEquals("뚜아", SayLike.lookup(items, "Toi"))
        assertNull(SayLike.lookup(items, "ça va bien"))
        assertNull(SayLike.lookup(emptyMap(), "Oui"))
        assertTrue(SayLike.parse("{nope").isEmpty())
        assertTrue(SayLike.shows(UiLang.KO, Lang.FR)); assertFalse(SayLike.shows(UiLang.EN, Lang.FR))
        assertTrue(SayLike.shows(UiLang.EN, Lang.KO)); assertFalse(SayLike.shows(UiLang.KO, Lang.KO))
    }
}
