package io.github.graviton94.todayletters.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterCardTest {
    @Test fun dates() {
        assertEquals("1888. 2. 21.", LetterCard.date("1888-02-21", Lang.KO))
        assertEquals("21 Feb 1888", LetterCard.date("1888-02-21", Lang.EN))
        assertEquals("1888. 10.", LetterCard.date("1888-10", Lang.KO))
        assertEquals("Oct 1888", LetterCard.date("1888-10", Lang.EN))
        assertEquals("1811", LetterCard.date("1811", Lang.EN))
        assertEquals("", LetterCard.date("", Lang.KO))
    }

    @Test fun footer() {
        assertEquals("빈센트 → 테오 · 1888. 2. 21.", LetterCard.footer("빈센트", "테오", "1888-02-21", Lang.KO))
        assertEquals("Vincent → Theo · 21 Feb 1888", LetterCard.footer("Vincent", "Theo", "1888-02-21", Lang.EN))
        // 받는 사람 · 날짜가 비면 그 칸을 뺀다
        assertEquals("Vincent · 21 Feb 1888", LetterCard.footer("Vincent", "", "1888-02-21", Lang.EN))
        assertEquals("빈센트 → 테오", LetterCard.footer("빈⁠센⁠트", "테오", "", Lang.KO))
    }

    @Test fun credit() {
        assertEquals("Vincent van Gogh · Self-Portrait with a Straw Hat · 1887",
            LetterCard.credit("Vincent van Gogh", "Self-Portrait with a Straw Hat", "1887 (Paris)"))
        assertEquals("퐁뇌프에서 본 파리 · 1763", LetterCard.credit("", "퐁뇌프에서 본 파리", "1763"))
    }

    @Test fun translation() {
        assertEquals("사랑하는 테오에게.", LetterCard.translation("Mon cher Théo,", "사랑하는 테오에게.", Lang.FR, Lang.KO))
        assertNull(LetterCard.translation("Dear Theo,", "Dear Theo,", Lang.EN, Lang.EN))
        assertNull(LetterCard.translation("Mon cher Théo,", " ", Lang.FR, Lang.KO))
        assertNull(LetterCard.translation("Mon cher Théo,", "Mon cher Théo,", Lang.FR, Lang.EN))
    }

    @Test fun sizes() {
        assertEquals(56f, LetterCard.originalPx(40))
        assertEquals(40f, LetterCard.originalPx(600))
        assertEquals("https://play.google.com/store/apps/details?id=io.github.graviton94.todayletters",
            LetterCard.storeUrl("io.github.graviton94.todayletters"))
    }

    @Test fun crop() {
        // 넓은 그림: 가운데에서 4:3
        val wide = LetterCard.crop(2000, 1000)
        assertEquals(1333, wide.width); assertEquals(1000, wide.height); assertEquals(333, wide.left)
        // 긴 그림 (자화상): 위쪽으로 치우쳐 4:3
        val tall = LetterCard.crop(900, 1200)
        assertEquals(900, tall.width); assertEquals(675, tall.height)
        assertEquals(((1200 - 675) * 0.35f).toInt(), tall.top)
        // 딱 4:3 이면 그대로
        assertEquals(LetterCard.Crop(0, 0, 800, 600), LetterCard.crop(800, 600))
    }
}
