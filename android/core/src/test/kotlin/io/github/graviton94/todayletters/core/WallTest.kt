package io.github.graviton94.todayletters.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 벽 라벨 · 화면 사이 단추 · 옮겨 가는 움직임 · 낱말 줄 문장부호 (시안 rw2). */
class WallTest {
    @Test fun eyebrowInTheSeriesLanguage() {
        assertEquals("Lettre I", Wall.letterNo(Lang.FR, 1))
        assertEquals("Brief II", Wall.letterNo(Lang.DE, 2))
        assertEquals("Carta XIV", Wall.letterNo(Lang.ES, 14))
        assertEquals("Letter XLIX", Wall.letterNo(Lang.EN, 49))
        assertEquals("Nouvelle médaille", Wall.medal(Lang.FR))
        assertEquals("Niveau", Wall.level(Lang.FR))
    }

    @Test fun metaUsesOnlyWhatTheDataHas() {
        assertEquals("Vincent van Gogh · 1887 · Paris", Wall.meta("Vincent van Gogh", "1887 (Paris)"))
        assertEquals("Vincent van Gogh · 1888", Wall.meta("Vincent van Gogh", "1888"))
        assertEquals("1888", Wall.meta("", "1888"))
        assertEquals("", Wall.meta(" ", ""))
        assertEquals("", Wall.place("1888"))
    }

    @Test fun rankSplitsTheRomanNumeral() {
        assertEquals("해설사" to "III", Wall.rank("해설사 III"))
        assertEquals("Assistant Curator" to "II", Wall.rank("Assistant Curator II"))
        assertEquals("관장" to "XIV", Wall.rank("관장 XIV"))
        assertEquals("Docent" to "", Wall.rank("Docent"))
    }

    @Test fun stepButtonNamesTheNextStep() {
        val m = listOf(ReplyMode.CHECK, ReplyMode.MATCH, ReplyMode.ALOUD, ReplyMode.CONSTELLATION)
        assertEquals(Next.AfterStep.Step(ReplyMode.MATCH, 1, 4), Next.afterStep(m, setOf(ReplyMode.CHECK), emptySet(), straight = true, backToLetter = true))
        // 건너뛴 걸음도 마친 수에 들고, 그다음 걸음으로
        assertEquals(Next.AfterStep.Step(ReplyMode.ALOUD, 2, 4), Next.afterStep(m, setOf(ReplyMode.CHECK), setOf(ReplyMode.MATCH), straight = true, backToLetter = true))
        // 다시 하기 · 복습 탭에서 들어온 걸음은 들어온 곳으로
        assertEquals(Next.AfterStep.Back(true), Next.afterStep(m, setOf(ReplyMode.CHECK), emptySet(), straight = false, backToLetter = true))
        assertEquals(Next.AfterStep.Back(false), Next.afterStep(m, m.toSet(), emptySet(), straight = true, backToLetter = false))
    }

    @Test fun letterButtonCountsReviewAndBasics() {
        val due = listOf("vincent:I:c1-l1:w0", "vincent:I:c1-l1:w1", "vincent:I:c1-l2:w3", CardRef.basic("vincent", "ab12cd34"), CardRef.basic("vincent", "ff00ee11", "en"))
        assertEquals(3 to 2, Next.review(due))
        assertEquals(Next.AfterLetter(false, 3, 2), Next.afterLetter(false, due, reviewDone = false))
        assertEquals(Next.AfterLetter(false, 0, 0), Next.afterLetter(false, due, reviewDone = true))
        assertEquals(Next.AfterLetter(true, 0, 0), Next.afterLetter(true, due, reviewDone = false))
        assertTrue(Next.basic(CardRef.basic("goya", "12345678")))
        assertFalse(Next.basic("vincent:I:c1-l1:w0"))
    }

    @Test fun levelButtonGoesToTheEntranceOnlyWhenADecorationOpened() {
        assertTrue(Next.afterLevel(14, 15))
        assertFalse(Next.afterLevel(15, 16))
        assertTrue(Next.afterLevel(3, 11))
    }

    @Test fun flightLerpsAndSettles() {
        val a = FlightMath.Box(100f, 400f, 112f, 140f); val b = FlightMath.Box(22f, 60f, 40f, 50f)
        assertEquals(a, FlightMath.lerp(a, b, 0f))
        assertEquals(b, FlightMath.lerp(a, b, 1f))
        assertEquals(61f, FlightMath.lerp(a, b, 0.5f).x, 1e-4f)
        assertEquals(0f, FlightMath.ease(0f), 1e-3f)
        assertEquals(1f, FlightMath.ease(1f), 1e-3f)
        // 느린 끝: 절반 시간이면 이미 절반을 훨씬 넘는다, 그리고 늘 앞으로만
        assertTrue(FlightMath.ease(0.5f) > 0.75f)
        var last = -1f
        for (k in 0..20) { val v = FlightMath.ease(k / 20f); assertTrue(v >= last - 1e-4f); last = v }
        assertEquals(1f, FlightMath.progress(0, still = true), 0f)
        assertFalse(FlightMath.over(200, landedAt = 0, still = false))
        assertTrue(FlightMath.over(FlightMath.MS.toLong() + 1, landedAt = 0, still = false))
        assertTrue(FlightMath.over(FlightMath.WAIT_MS.toLong(), landedAt = null, still = false))
        assertTrue(FlightMath.over(0, landedAt = null, still = true))
    }

    @Test fun spacedPunctuationAttachesToThePreviousWord() {
        val w = listOf(WordAt("Vraiment", 0, 400), WordAt("?", 400, 400), WordAt("Le", 900, 1000), WordAt("ciel", 1000, 1300), WordAt("!", 1300, 1300))
        val d = WordSpots.display(w)
        assertEquals(listOf("Vraiment ?", "Le", "ciel !"), d.map { it.t })
        assertEquals(0 to 1, d[0].from to d[0].to)
        assertEquals(900, d[1].s)
        // 여는 부호는 다음 낱말에
        val q = WordSpots.display(listOf(WordAt("«", 0, 0), WordAt("Oui", 10, 200), WordAt("»", 200, 200), WordAt("—", 200, 200), WordAt("dit-il", 300, 500)))
        assertEquals(listOf("«Oui » —", "dit-il"), q.map { it.t })
        assertEquals(listOf("¿Dónde", "está?"), WordSpots.display(listOf(WordAt("¿Dónde", 0, 100), WordAt("está?", 100, 200))).map { it.t })
        assertEquals(listOf("Oui,", "ça", "va.", "Et", "toi ?"),
            WordSpots.display("Oui, ça va. Et toi ?".split(" ").mapIndexed { i, t -> WordAt(t, i * 100, i * 100 + 90) }).map { it.t })
    }

    @Test fun noticeButtonNamesTheScreenUnderIt() {
        val room = Route.Letter("vincent", 1, 1, Route.Inbox)
        assertEquals(Next.Under.Step(ReplyMode.ALOUD), Next.under(Route.Play(room, ReplyMode.ALOUD)))
        assertEquals(Next.Under.Today, Next.under(Route.Inbox))
        assertEquals(Next.Under.Library, Next.under(Route.Library))
        assertEquals(Next.Under.Finished, Next.under(Route.Done(room)))
        assertEquals(Next.Under.Review, Next.under(Route.Session()))
        assertEquals(Next.Under.Words, Next.under(Route.Words))
        assertEquals(Next.Under.Letter, Next.under(Route.Seal(room)))
        assertEquals(Next.Under.Gallery, Next.under(Route.Exhibition))
        assertEquals(Next.Under.Shop, Next.under(Route.Purchase))
        assertEquals(Next.Under.Record, Next.under(Route.Medals))
    }

    @Test fun milestoneEyebrowIsInTheWorksLanguage() {
        assertEquals("Jalon", Wall.milestone(Lang.FR))
        assertEquals("Meilenstein", Wall.milestone(Lang.DE))
        assertEquals("이정표", Wall.milestone(Lang.KO))
    }
}
