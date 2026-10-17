package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 도움 사다리 ([Support]) · 발음 줄 ([Pron]) · 이해 확인 · 회화 걸음. */
class ScaffoldTest {
    private fun g(i: Int) = Reading.Grade.of(i)

    @Test fun checkLanguagesFollowTheBand() {
        assertEquals(Support.CheckLangs(Lang.KO, Lang.KO), Support.check(Reading.Band.A1, Lang.FR, Lang.KO))
        assertEquals(Support.CheckLangs(Lang.KO, Lang.KO), Support.check(Reading.Band.A2, Lang.FR, Lang.KO))
        assertEquals(Support.CheckLangs(Lang.FR, Lang.KO), Support.check(Reading.Band.B1, Lang.FR, Lang.KO))
        assertEquals(Support.CheckLangs(Lang.KO, Lang.KO), Support.check(Reading.Band.B2, Lang.KO, Lang.EN))
        assertTrue(Support.checkTranslationTappable(Reading.Band.A1))
        assertFalse(Support.checkTranslationTappable(Reading.Band.A2))
    }

    @Test fun talkHelpShrinksWithTheBand() {
        assertEquals(listOf(Support.Talk.SHADOW, Support.Talk.BLANK, Support.Talk.CHIPS, Support.Talk.MEANING), Reading.Band.entries.map(Support::talk))
        assertTrue(Support.talkLineShown(Support.Talk.SHADOW, 0))
        assertFalse(Support.talkLineShown(Support.Talk.CHIPS, 0)); assertTrue(Support.talkLineShown(Support.Talk.CHIPS, 1))
        assertFalse(Support.talkLineShown(Support.Talk.MEANING, 1)); assertTrue(Support.talkLineShown(Support.Talk.MEANING, 2))
        assertTrue(Support.talkFirstWord(Support.Talk.CHIPS, 0))
        assertFalse(Support.talkFirstWord(Support.Talk.MEANING, 0)); assertTrue(Support.talkFirstWord(Support.Talk.MEANING, 1))
    }

    @Test fun replySpeakAndReviewHint() {
        assertEquals(listOf(Support.Speak.SHOWN, Support.Speak.SHOWN, Support.Speak.FADED, Support.Speak.HIDDEN), Reading.Band.entries.map(Support::replySpeak))
        assertTrue(Support.reviewHintShown(Support.reviewHint(Reading.Band.A1), tapped = false, wrongs = 0, answered = false))
        assertFalse(Support.reviewHintShown(Support.reviewHint(Reading.Band.B1), tapped = false, wrongs = 1, answered = false))
        assertTrue(Support.reviewHintShown(Support.reviewHint(Reading.Band.B1), tapped = true, wrongs = 0, answered = false))
        assertFalse(Support.reviewHintShown(Support.reviewHint(Reading.Band.B2), tapped = true, wrongs = 1, answered = false))
        assertTrue(Support.reviewHintShown(Support.reviewHint(Reading.Band.B2), tapped = false, wrongs = 2, answered = false))
        assertTrue(Support.reviewHintShown(Support.reviewHint(Reading.Band.B2), tapped = false, wrongs = 0, answered = true))
    }

    @Test fun easierStepsDownOneBand() {
        assertEquals(g(5), Support.grade(g(8), easier = true))
        assertEquals(g(1), Support.grade(g(2), easier = true))
        assertEquals(g(8), Support.grade(g(8), easier = false))
        assertEquals(Ladder.help(g(8)), Support.reply(g(8)))
        assertEquals(Reading.foldAt(Reading.Band.B1), Support.fold(Reading.Band.B1))
    }

    @Test fun knownWordsAreBlankedFirstUnknownStayVisible() {
        val box = mapOf("as" to 4, "besoin" to 0, "froid" to 1)
        assertEquals("as", Support.pickBlank(listOf("froid", "as")) { box[it] ?: 0 })
        assertEquals("as besoin", Support.pickBlank(listOf("froid", "as besoin")) { box[it] ?: 0 })
        assertEquals("froid", Support.pickBlank(listOf("froid", "besoin"), { box[it] ?: 0 }))
        assertNull(Support.pickBlank(emptyList()) { 0 })
        assertEquals(listOf("besoin", "froid"), Support.keepVisible(listOf("as", "besoin", "froid")) { box[it] ?: 0 })
        assertTrue(Support.blankScore(3, 4) > Support.blankScore(3, 0))
        assertEquals(0, Support.blankScore(0, 5))
    }

    @Test fun lessenedWhenBandOrReplyHelpChanges() {
        assertTrue(Support.lessened(g(3), g(4)))   // A1 → A2
        assertTrue(Support.lessened(g(1), g(2)))   // 잇기 → 빈칸
        assertFalse(Support.lessened(g(4), g(5)))  // A2 1단 → 2단
        assertFalse(Support.lessened(g(5), g(4)))
    }

    @Test fun pronunciationLinePerLearnLanguage() {
        val w = Word(Tri(mapOf(Lang.FR to "voyage", Lang.EN to "journey", Lang.KO to "읽어요")), ipa = "vwajaʒ", ipaEn = "ˈdʒɜːr.ni",
            romKo = "ilgeoyo", sayKo = "일거요", pronNote = Tri(mapOf(Lang.KO to "받침이 넘어가요 (연음)", Lang.EN to "Linking")))
        assertEquals("[vwajaʒ]", Pron.line(w, Lang.FR, ownIpa = true))
        assertEquals("", Pron.line(w, Lang.FR, ownIpa = false))
        assertEquals("[ˈdʒɜːr.ni]", Pron.line(w, Lang.EN, ownIpa = false))
        assertEquals("[일거요] · ilgeoyo", Pron.line(w, Lang.KO, ownIpa = false))
        assertEquals("받침이 넘어가요 (연음)", Pron.note(w, Lang.KO, Lang.KO))
        assertEquals("", Pron.note(w, Lang.EN, Lang.KO))
        // 영어 사전의 낱말: ipa 가 곧 영어 발음
        val e = LexEntry("journey", ipa = "dʒˈɜːni", level = "A2")
        assertEquals("[dʒˈɜːni]", Pron.line(Vocab.word(e, Lang.EN), Lang.EN, ownIpa = false))
        val k = LexEntry("읽다", ipa = "익따", romKo = "ikda", pronNote = Tri(mapOf(Lang.KO to "겹받침")))
        assertEquals("[익따] · ikda", Pron.line(Vocab.word(k, Lang.KO), Lang.KO, ownIpa = false))
    }

    @Test fun checkAndTalkStepsOnlyWithData() {
        val modes = setOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION)
        val plain = Letter("l", "", "", "calm", emptyList(), emptyList(), null, null)
        assertEquals(listOf(ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION), Plays.forLetter(modes, true, plain))
        val tri = Tri(mapOf(Lang.EN to "x"))
        val full = plain.copy(checks = listOf(Check(tri, List(4) { tri }, 1, listOf(0))), talk = Talk(tri, listOf(TalkLine(false, tri), TalkLine(true, tri))))
        assertEquals(listOf(ReplyMode.CHECK, ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.TALK, ReplyMode.CONSTELLATION), Plays.forLetter(modes, true, full))
        assertEquals(listOf(ReplyMode.CHECK, ReplyMode.MATCH, ReplyMode.TALK, ReplyMode.CONSTELLATION), Plays.forLetter(modes, false, full))
        assertEquals("t2_fr", Talk(tri, emptyList(), audio = "t").audioName(1, Lang.FR))
        assertNull(Talk(tri, emptyList()).audioName(0, Lang.FR))
        // 언어마다 다른 소리 앞부분 ("audio": {"*": "t", "ko": "tk"}): 고친 언어만 새 이름, 나머지는 "*"
        val perLang = Talk(tri, emptyList(), audio = "t", audioBy = mapOf(Lang.KO to "tk"))
        assertEquals("tk3_ko", perLang.audioName(2, Lang.KO))
        assertEquals("t3_en", perLang.audioName(2, Lang.EN))
        assertEquals("t1_fr", perLang.audioName(0, Lang.FR))
        assertEquals("tk1_ko", Talk(tri, emptyList(), audioBy = mapOf(Lang.KO to "tk")).audioName(0, Lang.KO))
        assertNull(Talk(tri, emptyList(), audioBy = mapOf(Lang.KO to "tk")).audioName(0, Lang.EN))
        // 끝난 편지에 새 걸음이 생겨도 ‘지금’ 으로 보이지 않는다
        val done = LetterProgress(shown = 1, replied = modes, done = true)
        assertFalse(Journey.Step.NOW in Journey.letterDots(1, 1, Plays.forLetter(modes, true, full), done))
    }
}
