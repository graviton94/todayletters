package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val vincent = Series("vincent", Lang.FR)
    private val wolfgang = Series("wolfgang", Lang.DE)

    @Test fun learnChoicesAreOriginalEnglishKoreanMinusRead() {
        assertEquals(listOf(Lang.FR, Lang.EN), Langs.learnChoices(vincent, Lang.KO))
        assertEquals(listOf(Lang.FR, Lang.KO), Langs.learnChoices(vincent, Lang.EN))
        assertEquals(listOf(Lang.EN, Lang.KO), Langs.learnChoices(vincent, Lang.FR))
        assertEquals(Lang.DE, Langs.defaultLearn(wolfgang, Lang.KO))
    }

    @Test fun eachSeriesKeepsItsOwnLearnLanguage() {
        val app = AppSettings(read = Lang.KO)
        val all = mapOf(vincent to Langs.start(vincent, app), wolfgang to SeriesSettings(learn = Lang.EN))
        assertEquals(Lang.FR, all.getValue(vincent).learn)
        assertEquals(Lang.EN, all.getValue(wolfgang).learn)
    }

    @Test fun changingReadLanguageMovesClashingSeriesOnly() {
        val all = mapOf(vincent to SeriesSettings(learn = Lang.EN), wolfgang to SeriesSettings(learn = Lang.DE))
        val (next, moved) = Langs.afterReadChange(AppSettings(read = Lang.EN), all)
        assertEquals(Lang.FR, next.getValue(vincent).learn)
        assertEquals(Lang.DE, next.getValue(wolfgang).learn)
        assertEquals(listOf("vincent"), moved)
    }

    @Test fun uiLanguageIsEnglishOrKoreanAndSeparateFromRead() {
        assertEquals(AppSettings(ui = UiLang.KO, read = Lang.KO), Langs.firstRun("ko-KR"))
        assertEquals(AppSettings(ui = UiLang.EN, read = Lang.EN), Langs.firstRun("fr-FR"))
        // 프랑스어 작은 줄로 읽으며 영어를 배우는 사람도, 앱 글자는 영어 · 한국어 중 하나
        val app = AppSettings(ui = UiLang.EN, read = Lang.FR)
        assertEquals(listOf(Lang.EN, Lang.KO), Langs.learnChoices(vincent, app.read))
    }

    @Test fun reducedMotionMeansInstantText() {
        val v = RoomView.of(AppSettings(pace = TypingPace.CALM), SeriesSettings(learn = Lang.FR), reducedMotion = true)
        assertEquals(TypingPace.INSTANT, v.pace)
    }

    @Test fun backFollowsTheTreeEvenFromADeepLink() {
        val letter = Route.Letter("vincent", 2, 13)
        assertEquals(
            listOf(Route.Inbox, Route.Library, Route.Series("vincent"), Route.Chapter("vincent", 2), letter),
            Nav.trail(letter),
        )
        val shortcut = letter.copy(from = Route.Inbox)
        assertEquals(listOf(Route.Inbox, shortcut), Nav.trail(shortcut))
        assertEquals(letter, Nav.up(Route.Play(letter, ReplyMode.CONSTELLATION)))
        assertNull(Nav.up(Route.Inbox))
    }

    @Test fun settingsAreReachedFromTheRightPlaces() {
        assertTrue(Nav.showsAppSettings(Route.Library))
        assertFalse(Nav.showsAppSettings(Route.Letter("vincent", 1, 1)))
        assertTrue(Nav.showsSeriesSettings(Route.Series("vincent")))
        assertTrue(Nav.showsSeriesSettings(Route.RoomInfo(Route.Letter("vincent", 1, 1))))
        assertTrue(Nav.asksBeforeLeaving(Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)))
    }

    @Test fun launchOrder() {
        assertEquals(listOf(Stage.INTRO_ENVELOPE, Stage.ONBOARDING, Stage.MAIN), Launch.plan(true, true, false, false))
        assertEquals(listOf(Stage.INTRO_ENVELOPE, Stage.MAIN), Launch.plan(false, true, false, false))
        assertEquals(listOf(Stage.INTRO_SHORT, Stage.MAIN), Launch.plan(false, false, false, false))
        assertEquals(listOf(Stage.MAIN), Launch.plan(false, true, true, false))
        assertEquals(listOf(Stage.MAIN), Launch.plan(false, true, false, true))
    }

    private fun tri(fr: String, en: String = fr, ko: String = fr) = Tri(mapOf(Lang.FR to fr, Lang.EN to en, Lang.KO to ko))
    private val letter = Letter(
        id = "c1-l1", date = "1888-02-21", place = "Arles", mood = "bright",
        messages = listOf(
            Message(tri("Mon cher Théo,")),
            Message(tri("Mais la vue des étoiles me fait toujours rêver,", "But the sight of the stars always makes me dream,")),
        ),
        words = listOf(Word(tri("étoile", "star", "별"))), note = null, plate = null,
    )

    @Test fun arrivalsLetPerDayNewLetters() {
        assertEquals(1, Arrivals.openable(total = 5, done = 0, openedToday = 0, perDay = 1))
        assertEquals(1, Arrivals.openable(total = 5, done = 1, openedToday = 1, perDay = 1))
        assertEquals(0, Arrivals.waiting(total = 5, done = 1, openedToday = 1, perDay = 1))
        assertEquals(2, Arrivals.waiting(total = 5, done = 1, openedToday = 0, perDay = 2))
        assertEquals(5, Arrivals.openable(total = 5, done = 5, openedToday = 0, perDay = 3))
    }

    @Test fun constellationShufflesButKeepsTheAnswer() {
        val (answer, pieces) = Exercises.constellation("La vue des étoiles me fait rêver", seed = 7)
        assertEquals(listOf("La", "vue", "des", "étoiles", "me", "fait", "rêver"), answer)
        assertEquals(answer.sorted(), pieces.sorted())
        assertFalse(answer == pieces)
        assertTrue(Exercises.isRight(answer, answer))
    }

    @Test fun replyFallsBackToAShortMessage() {
        val (text, _) = Exercises.replyFor(letter, Lang.FR)
        assertEquals("Mais la vue des étoiles me fait toujours rêver,", text)
        val authored = letter.copy(reply = tri("La vue des étoiles te fait rêver ?"))
        assertEquals("La vue des étoiles te fait rêver ?", Exercises.replyFor(authored, Lang.FR).first)
        assertEquals(listOf("étoile" to "별"), Exercises.pairs(letter, Lang.FR, Lang.KO))
    }

    @Test fun letterIsDoneWhenChosenRepliesAreDone() {
        val modes = setOf(ReplyMode.MATCH, ReplyMode.CONSTELLATION, ReplyMode.ALOUD)
        var p = LetterProgress().reveal(2).reveal(2).reveal(2)
        assertEquals(2, p.shown)
        assertTrue(p.allShown(2))
        p = p.reply(ReplyMode.CONSTELLATION).complete(modes)
        assertFalse(p.done)
        p = p.reply(ReplyMode.ALOUD).complete(modes)
        assertTrue(p.done)
        assertTrue(LetterProgress().complete(setOf(ReplyMode.MATCH)).done)
    }

    @Test fun helpReplaysOnlyThatScreen() {
        val c = Coach()
        assertTrue(c.due("room", calm = true))
        assertFalse(c.due("room", calm = false))
        c.done("room"); c.done("library")
        assertFalse(c.due("room", calm = true))
        c.again("room")
        assertTrue(c.due("room", calm = true))
        assertFalse(c.due("library", calm = true))
        c.reset()
        assertTrue(c.due("library", calm = true))
    }
}
