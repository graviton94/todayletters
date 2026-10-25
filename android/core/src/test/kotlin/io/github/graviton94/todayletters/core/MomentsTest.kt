package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 주고받기 순간 (시안 moments, 2026-10-25 확정): 누가 하는가 · 소포 · 목록 · 소식 · 맡기기 · 이동 위치 공유 · 금액 읽기. */
class MomentsTest {
    private fun t(s: String) = Tri(mapOf(Lang.EN to s, Lang.KO to s))
    private fun letter(id: String, place: String, vararg moments: Moment) =
        Letter(id, "1770", place, "calm", listOf(Message(t("a")), Message(t("b"))), emptyList(), null, null, moments = moments.toList())

    /** 내가 하는 일 (송금 · 소포 · 맡기기) 은 막고, 보낸 사람의 소포 · 목록 · 소식은 사진처럼 본다 (3500 + 30 × 글, 6000 까지). */
    @Test fun readerActionsBlockAndWriterThingsAreLookedAt() {
        val mine = Moment.Parcel(-1, listOf(Item(t("toiles"), "toile")), t("Paris → Saint-Rémy"), reader = true)
        val his = Moment.Parcel(2, listOf(Item(t("croquis"))), t("ci-inclus"), reader = false)
        val hand = Moment.Handover(3, "courier", t("Ta réponse"), t("Paris → Tortona"))
        assertTrue(RoomFlow.blocks(mine)); assertEquals(0L, RoomFlow.momentHoldMs(mine))
        assertTrue(RoomFlow.blocks(hand)); assertEquals(0L, RoomFlow.momentHoldMs(hand))
        assertFalse(RoomFlow.blocks(his))
        assertEquals(3500L + 30 * 16, RoomFlow.momentHoldMs(his, RoomFlow.captionOf(his, Lang.EN)))
        val list = Moment.Ledger(3, t("Cuenta"), List(6) { Row(t("Un apartado de toros"), "4000 reales") })
        assertFalse(RoomFlow.blocks(list))
        assertEquals(6000L, RoomFlow.momentHoldMs(list, RoomFlow.captionOf(list, Lang.EN)))
        val news = Moment.News(0, t("Pintor del Rey"), "ya soy Pintor del Rey")
        assertEquals(3500L + 30 * (14 + 21), RoomFlow.momentHoldMs(news, RoomFlow.captionOf(news, Lang.EN)))
        assertEquals(1750L + 15L * 35, RoomFlow.momentHoldMs(news, RoomFlow.captionOf(news, Lang.EN), RoomFlow.Pause.SHORT))
    }

    /** 송금의 "by": reader 면 편지 중간이라도 내 차례, writer 면 편지 앞이라도 보낸 사람이 알리는 것. */
    @Test fun transferByFollowsData() {
        assertTrue(RoomFlow.blocks(Moment.Transfer(0, "100 francs", t(""), t(""), reader = true)))
        assertFalse(RoomFlow.blocks(Moment.Transfer(-1, "50 francs", t(""), t(""), reader = false)))
        assertEquals(2000L, RoomFlow.momentHoldMs(Moment.Transfer(2, "200 Fl.", t(""), t(""), reader = false)))
    }

    /** 이동: 편지 쓴 곳이 앞 편지와 다르면 편지 앞에 위치 공유 (앞 곳에서), 같은 곳 · 이미 위치가 있는 편지 · 첫 편지는 그대로. */
    @Test fun travelSharesLocationWhenThePlaceChanges() {
        val names = mapOf("Rom" to Tri(mapOf(Lang.DE to "Rom", Lang.EN to "Rome", Lang.KO to "로마")))
        val photo = Moment.Photo(0, "a.webp", t("x"))
        val out = Travel.apply(listOf(
            letter("p1", "Salzburg"),
            letter("c1", "Mailand", photo),
            letter("c2", "Rom", photo),
            letter("c3", "Rom"),
            letter("c4", "Neapel", Moment.Location(0, "naples", t("Neapel"), "")),
        ), auto = true, dedupe = false, names = names)
        assertTrue(out[0].moments.isEmpty())
        val first = out[1].moments.first() as Moment.Location
        assertEquals(-1, first.after); assertTrue(first.travel)
        assertEquals("Mailand", first.title[Lang.KO]); assertEquals("Salzburg", first.from!![Lang.EN])
        val rome = out[2].moments.first() as Moment.Location
        assertEquals("로마", rome.title[Lang.KO]); assertEquals("Mailand", rome.from!![Lang.KO])
        assertEquals(listOf<Moment>(photo), out[2].moments.drop(1))
        assertTrue(out[3].moments.isEmpty())
        assertEquals(1, out[4].moments.size); assertFalse((out[4].moments[0] as Moment.Location).travel)
    }

    /** 나폴레옹: 새로 놓지 않고, 같은 곳에서 연달아 쓴 편지의 위치 순간은 뺀다. */
    @Test fun dedupeDropsRepeatedLocations() {
        fun loc(id: String) = Moment.Location(0, id, t(id), "")
        val out = Travel.apply(listOf(
            letter("a", "Trianon", loc("trianon")),
            letter("b", "Trianon", loc("trianon"), Moment.Notice(-1, t("n"))),
            letter("c", "Paris", loc("tuileries")),
            letter("d", "Fontainebleau"),
        ), auto = false, dedupe = true)
        assertEquals(1, out[0].moments.size)
        assertEquals(listOf<Moment>(Moment.Notice(-1, t("n"))), out[1].moments)
        assertEquals(1, out[2].moments.size)
        assertTrue(out[3].moments.isEmpty())
        // 규칙이 없는 작품은 그대로
        val same = listOf(letter("x", "A"), letter("y", "B"))
        assertEquals(same, Travel.apply(same, auto = false, dedupe = false))
    }

    /** 금액의 한국어 읽기: 숫자 + 화폐 낱말 하나일 때만. */
    @Test fun amountsReadInKoreanOnlyWhenSimple() {
        assertEquals("100프랑", Amounts.ko("100 francs"))
        assertEquals("1프랑", Amounts.ko("1 franc"))
        assertEquals("100,000프랑", Amounts.ko("100 000 francs"))
        assertEquals("1,200플로린", Amounts.ko("1200 Fl."))
        assertEquals("4,000레알", Amounts.ko("4000 reales"))
        assertEquals("3두카트", Amounts.ko("3 Ducaten"))
        assertEquals("7루이도르", Amounts.ko("7 Louisdor"))
        assertNull(Amounts.ko("3,50 francs"))
        assertNull(Amounts.ko("23 cafés"))
        assertNull(Amounts.ko("200 Duc. + 50 Duc."))
    }
}
