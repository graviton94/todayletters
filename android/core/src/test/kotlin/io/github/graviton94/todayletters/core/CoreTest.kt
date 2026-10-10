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
            listOf(Route.Library, Route.Inbox, Route.Series("vincent"), Route.Chapter("vincent", 2), letter),
            Nav.trail(letter),
        )
        val shortcut = letter.copy(from = Route.Inbox)
        assertEquals(listOf(Route.Library, Route.Inbox, shortcut), Nav.trail(shortcut))
        assertEquals(letter, Nav.up(Route.Play(letter, ReplyMode.CONSTELLATION)))
        assertNull(Nav.up(Route.Library))
        assertEquals(Route.Library, Nav.up(Route.Enter("vincent")))
        assertEquals(listOf(Route.Library, Route.Gallery, Route.Exhibition), Nav.trail(Route.Exhibition))
        assertFalse(Nav.inSeries(Route.Overall))
        assertTrue(Nav.inSeries(Route.Milestones))
    }

    @Test fun settingsAreReachedFromTheRightPlaces() {
        assertTrue(Nav.showsAppSettings(Route.Library))
        assertFalse(Nav.showsAppSettings(Route.Letter("vincent", 1, 1)))
        assertTrue(Nav.showsSeriesSettings(Route.Series("vincent")))
        assertTrue(Nav.showsSeriesSettings(Route.RoomInfo(Route.Letter("vincent", 1, 1))))
        assertTrue(Nav.asksBeforeLeaving(Route.Play(Route.Letter("vincent", 1, 1), ReplyMode.ALOUD)))
    }

    @Test fun launchOrder() {
        assertEquals(listOf(Stage.OPENING, Stage.ONBOARDING, Stage.MAIN), Launch.plan(firstRun = true, firstOfDay = true))
        assertEquals(listOf(Stage.OPENING, Stage.MAIN), Launch.plan(firstRun = false, firstOfDay = true))
        assertEquals(listOf(Stage.MAIN), Launch.plan(firstRun = false, firstOfDay = false))
        assertEquals(listOf(Stage.MAIN), Launch.plan(firstRun = false, firstOfDay = true, deepLink = true))
        assertEquals(listOf(Stage.ONBOARDING, Stage.MAIN), Launch.plan(firstRun = true, firstOfDay = true, deepLink = true))
    }

    @Test fun koreanBreaksOnlyBetweenWords() {
        val j = Breaks.JOIN
        assertEquals("도${j}착${j}해${j}요${j}.", Breaks.keepAll("도착해요."))
        assertEquals("하${j}루${j}에 한 통", Breaks.keepAll("하루에 한 통"))
        assertEquals("1${j}통 · 「${j}남${j}쪽${j}으${j}로${j}」", Breaks.keepAll("1통 · 「남쪽으로」"))
        assertEquals("Mon cher Théo,", Breaks.keepAll("Mon cher Théo,"))
        assertEquals("도착해요 오늘", Breaks.plain(Breaks.keepAll("도착해요 오늘")))
    }

    @Test fun onlyTheNextStarJoins() {
        val answer = listOf("Moi", "aussi,", "j'ai", "pensé", "à", "toi.")
        assertTrue(Exercises.isNext(answer, 0, "Moi"))
        assertFalse(Exercises.isNext(answer, 1, "pensé"))
        assertTrue(Exercises.isNext(answer, 2, "j'ai"))
        assertFalse(Exercises.isNext(answer, 6, "toi."))
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
        assertEquals(1, Arrivals.openable(total = 5, started = 0, openedToday = 0, perDay = 1))
        // 오늘 열고 아직 다 읽지 않은 편지는 그대로 열려 있다 (사라지지 않음)
        assertEquals(1, Arrivals.openable(total = 5, started = 1, openedToday = 1, perDay = 1))
        assertEquals(1, Arrivals.waiting(total = 5, done = 0, started = 1, openedToday = 1, perDay = 1))
        assertEquals(0, Arrivals.waiting(total = 5, done = 1, started = 1, openedToday = 1, perDay = 1))
        assertEquals(2, Arrivals.waiting(total = 5, done = 1, started = 1, openedToday = 0, perDay = 2))
        assertEquals(5, Arrivals.openable(total = 5, started = 5, openedToday = 0, perDay = 3))
        assertEquals(0, Arrivals.daysUntil(index = 0, openable = 1, perDay = 1))
        assertEquals(1, Arrivals.daysUntil(index = 1, openable = 1, perDay = 1))
        assertEquals(3, Arrivals.daysUntil(index = 3, openable = 1, perDay = 1))
        assertEquals(2, Arrivals.daysUntil(index = 4, openable = 1, perDay = 2))
        assertEquals(3, Streak.after(lastDay = 10, count = 2, today = 11))
        assertEquals(1, Streak.after(lastDay = 10, count = 5, today = 13))
        assertEquals(2, Streak.after(lastDay = 11, count = 2, today = 11))
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
        assertFalse(p.done)
        p = p.reply(ReplyMode.MATCH).complete(modes)
        assertTrue(p.done)
        assertTrue(LetterProgress().complete(setOf(ReplyMode.DICTATION)).done)
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

    /** F7: ‘내 것’이 된 낱말도 계속 다시 나오고, 간격은 늘어나되 흔들리며, 하루 몫에서 밀리지 않는다. */
    @Test fun memoryKeepsComingBack() {
        var c = Card("lumineux", box = 4, due = 200)
        c = Memory.after(c, correct = true, today = 200)          // 내 것 도착: 35일 ±20%
        assertEquals(Memory.TOP, c.box); assertEquals(0, c.keep)
        assertTrue(c.due - 200 in 28..42)
        val gaps = mutableListOf<Long>()
        var day = c.due
        repeat(5) { c = Memory.after(c, correct = true, today = day); gaps += c.due - day; day = c.due }
        assertEquals(Memory.TOP, c.box)
        assertTrue(gaps[0] in 48L..72L && gaps[1] in 96L..144L && gaps[2] in 192L..288L && gaps[4] in 192L..288L)   // 60 · 120 · 240 · 240 …
        // 틀리면 두 단계 내려가 내일, ‘잊을 뻔’ 표시, 다시 올라가면 35일부터
        c = Memory.after(c, correct = false, today = day)
        assertEquals(3, c.box); assertTrue(c.lapsed); assertEquals(0, c.keep); assertEquals(day + 1, c.due)
        // 같은 낱말 · 같은 날이면 흔들림도 같다
        assertEquals(Memory.jitter(60, "x", 10), Memory.jitter(60, "x", 10))
        // 새 낱말이 많아도 ‘내 것’ 확인 두 장은 자리를 지킨다
        val many = (1..30).map { Card("n$it", box = 0, due = 100) } + (1..5).map { Card("t$it", box = Memory.TOP, due = 90) }
        val today = Memory.dueToday(many, 100)
        assertEquals(Memory.DAILY, today.size)
        assertEquals(Memory.KEEP_SLOTS, today.count { it.box == Memory.TOP })
        // 갑자기 한 문제: 2주 넘게 안 본 ‘내 것’ 가운데 하나, 같은 날이면 같은 것
        val pool = listOf(Card("a", box = Memory.TOP, due = 400, last = 100), Card("b", box = Memory.TOP, due = 400, last = 299), Card("c", box = 2, due = 400, last = 10))
        assertEquals("a", Memory.surprise(pool, 300)?.key)
        assertEquals(null, Memory.surprise(pool.drop(1), 300))
        val kept = Memory.surpriseAnswered(pool[0], true, 300)
        assertEquals(400, kept.due); assertEquals(300, kept.last)
    }

    @Test fun memoryMovesCardsUpAndBack() {
        var c = Memory.added("w", today = 100)
        assertEquals(101, c.due)
        c = Memory.after(c, correct = true, today = 101)
        assertEquals(1, c.box); assertEquals(102, c.due)
        c = Memory.after(c, correct = true, today = 102)
        assertEquals(2, c.box); assertEquals(105, c.due)
        c = Memory.after(c, correct = true, today = 105)
        assertEquals(3, c.box); assertEquals(112, c.due)
        // 틀리면 두 단계만 내려간다
        c = Memory.after(c, correct = false, today = 112)
        assertEquals(1, c.box); assertEquals(113, c.due)
        val cards = (1..20).map { Card("k$it", box = it % 6, due = 100) }
        assertEquals(Memory.DAILY, Memory.dueToday(cards, 100).size)
        assertEquals(0, Memory.dueToday(cards, 100).first().box)
    }

    @Test fun dictationIsGentle() {
        assertEquals(Memory.Mark.RIGHT, Memory.grade("neige", " Neige "))
        assertEquals(Memory.Mark.ACCENT, Memory.grade("pensé", "pense"))
        assertEquals(Memory.Mark.WRONG, Memory.grade("neige", "neije"))
    }

    @Test fun arrivalDayAndMissed() {
        // 하루 1통: 10일에 열었으면 다음 편지는 11일 도착
        assertEquals(11L, Arrivals.arrivedOn(10, 1, 1))
        // 하루 2통인데 1통만 열었으면 같은 날 하나 더
        assertEquals(10L, Arrivals.arrivedOn(10, 1, 2))
        assertEquals(null, Arrivals.arrivedOn(-1, 0, 1))
        assertTrue(Arrivals.missed(11, 13, 1))
        assertFalse(Arrivals.missed(13, 13, 1))
        assertFalse(Arrivals.missed(11, 13, 0))
    }

    @Test fun growthCurve() {
        assertEquals(ReviewKind.MEANING, Memory.kindFor(Card("a", box = 0)))
        assertEquals(ReviewKind.BLANK, Memory.kindFor(Card("a", box = 1)))
        assertEquals(ReviewKind.DICTATION, Memory.kindFor(Card("a", box = 2)))
        assertEquals(ReviewKind.SPEAK, Memory.kindFor(Card("a", box = 3)))
        val cards = mapOf("a" to Card("a", box = 2), "b" to Card("b", box = 1), "c" to Card("c", box = 5))
        assertEquals(66, Growth.readAlone(listOf("a", "b", "c"), cards))
        assertFalse(Growth.letterAlone(listOf("a", "b"), cards))
        assertTrue(Growth.letterAlone(listOf("a", "c"), cards))
        assertEquals(listOf(25, 50), Growth.crossed(20, 55))
        assertEquals(75, Growth.nextMilestone(50))
    }

    @Test fun rewardsAndCaps() {
        var w = Wallet()
        var g: Int
        w = Rewards.earn(w, Earn.REVIEW_RIGHT, 10, times = 10).let { (a, b) -> g = b; a }
        assertEquals(10, g)
        w = Rewards.earn(w, Earn.REVIEW_RIGHT, 10, times = 5).let { (a, b) -> g = b; a }
        assertEquals(2, g)   // 하루 상한 12
        w = Rewards.earn(w, Earn.REVIEW_RIGHT, 11).let { (a, b) -> g = b; a }
        assertEquals(1, g)   // 다음 날은 새로
        assertEquals(13, w.balance)
        assertEquals(null, Rewards.spend(w, 50))
        assertEquals(3, Rewards.spend(w, 10)!!.balance)
    }

    @Test fun achievementsUnlock() {
        val s = Stats(lettersDone = 5, bestStreak = 7, wordsKnown = 30, readAlone = 52, lettersSealed = 1)
        val ids = Achievements.newly(Achievements.series, s, setOf("vincent:alone_1"), "vincent").map { it.id }
        assertEquals(listOf("streak_7", "read_50"), ids)
        val all = Achievements.newly(Achievements.overall, Stats(seriesStarted = 2, langsSpoken = 1), emptySet()).map { it.id }
        assertEquals(listOf("all_series_2"), all)
        assertEquals("all_series_2", Achievements.key(Achievements.overall.first(), "vincent"))
    }

    @Test fun streakRests() {
        assertEquals(4 to 1, Streak.afterWithRest(lastDay = 10, count = 3, today = 12, rests = 1))
        assertEquals(1 to 0, Streak.afterWithRest(lastDay = 10, count = 3, today = 12, rests = 0))
        assertEquals(1 to 0, Streak.afterWithRest(lastDay = 10, count = 3, today = 14, rests = 2))
        assertEquals(3, Streak.shown(lastDay = 10, count = 3, today = 12, rests = 1))
        assertEquals(0, Streak.shown(lastDay = 10, count = 3, today = 12, rests = 0))
    }

    @Test fun galleryDraws() {
        fun p(id: String, t: Tier, th: Set<String> = emptySet()) = Piece(id, "", Tri(mapOf(Lang.EN to id)), "", "", t, th)
        val pool = listOf(p("l", Tier.LETTER), p("s1", Tier.SKETCH), p("s2", Tier.SKETCH), p("d", Tier.DRAWING, setOf("people")), p("m", Tier.MASTER))
        repeat(50) { seed -> assertTrue(Draws.pick(pool, emptySet(), seed.toLong())!!.id != "l") }
        assertEquals("m", Draws.pick(pool, setOf("s1"), 3, minTier = Tier.MASTER)!!.id)
        assertEquals(null, Draws.pick(pool, setOf("s1", "s2", "d", "m"), 1))
        assertEquals(Draws.pick(pool, emptySet(), 42), Draws.pick(pool, emptySet(), 42))
        val e = Exhibition("x", Tri(mapOf(Lang.EN to "x")), "people")
        assertEquals(listOf("d"), Draws.exhibitionPieces(pool, e).map { it.id })
        assertEquals(e, Draws.exhibition(listOf(e), 9))
    }

    @Test fun questsAndRecital() {
        val q = Quests.today(20000)
        assertEquals(3, q.size); assertEquals(3, q.toSet().size)
        assertEquals(q, Quests.today(20000))
        assertEquals(6, Recital.weekday(3))      // 1970-01-04 은 일요일
        assertTrue(Recital.isSunday(3))
        assertEquals(-3, Recital.weekStart(3))   // 그 주 월요일 1969-12-29
    }

    /** 가짜 목소리: 시간에 따른 음높이 [pitch] 와 크기 [amp] 로 배음 셋을 합친 소리. */
    private fun voice(seconds: Double, rate: Int, pitch: (Double) -> Double, amp: (Double) -> Double): FloatArray {
        val n = (seconds * rate).toInt(); var ph = 0.0
        return FloatArray(n) { k ->
            val t = k.toDouble() / rate
            ph += 2 * Math.PI * pitch(t) / rate
            (amp(t) * (0.6 * Math.sin(ph) + 0.3 * Math.sin(2 * ph) + 0.1 * Math.sin(3 * ph))).toFloat()
        }
    }

    @Test fun prosodyPitch() {
        val rate = 16000
        val x = voice(0.5, rate, { 200.0 }, { 0.5 })
        val f = Prosody.yin(x, 2000, 640, rate)
        assertTrue(kotlin.math.abs(f - 200f) < 4f, "got $f")
    }

    @Test fun prosodyScore() {
        val rate = 16000
        // 오르다 내려가는 문장, 가운데 쉼
        fun melody(scale: Double) = { t: Double -> val u = t / scale; if (u < 0.6) 150 + 120 * u else 222 - 100 * (u - 0.6) }
        fun env(scale: Double) = { t: Double -> val u = t / scale; if (u > 0.55 && u < 0.7) 0.0 else 0.5 }
        val ref = Prosody.analyze(voice(1.3, rate, melody(1.0), env(1.0)), rate)
        // 같은 가락을 한 옥타브 위에서, 10% 느리게
        val same = Prosody.analyze(voice(1.43, rate, { t -> 2 * melody(1.1)(t) }, env(1.1)), rate)
        // 높낮이 없이 한 음으로
        val flat = Prosody.analyze(voice(1.3, rate, { 180.0 }, env(1.0)), rate)
        val good = Prosody.score(ref, same); val bad = Prosody.score(ref, flat)
        assertTrue(good.passed && good.intonation >= 80, "good $good")
        assertTrue(bad.intonation < 60 && bad.intonation < good.intonation, "bad $bad")
    }
}
