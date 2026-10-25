package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 편지 방 읽기 흐름 (시안 room src4): 쉼 · 순간 쉼 · 긴 쉼 건너뛰기 · 낱말부터 이어 듣기 · 차례. */
class RoomFlowTest {
    private fun t(s: String) = Tri(mapOf(Lang.EN to s))

    @Test fun holdGrowsWithTranslationAndCaps() {
        assertEquals(1800L, RoomFlow.holdMs(0))
        assertEquals(1800L + 40 * 50, RoomFlow.holdMs(50))
        assertEquals(5000L, RoomFlow.holdMs(80))
        assertEquals(5000L, RoomFlow.holdMs(400))
    }

    @Test fun holdFollowsPauseSetting() {
        assertEquals(1900L, RoomFlow.holdMs(50, RoomFlow.Pause.SHORT))
        assertEquals(3800L, RoomFlow.holdMs(50, RoomFlow.Pause.NORMAL))
        assertEquals(6080L, RoomFlow.holdMs(50, RoomFlow.Pause.LONG))
        assertEquals(8000L, RoomFlow.holdMs(400, RoomFlow.Pause.LONG))
    }

    /** 순간도 볼 시간: 사진은 설명 길이만큼 (6초까지) · 위치 2.5초 · 날씨 · 알림 2초 · 곡은 듣거나 넘길 때까지 · 내 송금은 내 차례. */
    @Test fun momentsHoldToBeLookedAt() {
        val photo = Moment.Photo(0, "a.webp", t("Le pont"))
        assertEquals(3500L + 30 * 20, RoomFlow.momentHoldMs(photo, 20))
        assertEquals(6000L, RoomFlow.momentHoldMs(photo, 200))
        assertEquals(3000L, RoomFlow.momentHoldMs(photo, 200, RoomFlow.Pause.SHORT))
        assertEquals(2500L, RoomFlow.momentHoldMs(Moment.Location(0, "p", t("x"), "")))
        assertEquals(2000L, RoomFlow.momentHoldMs(Moment.Notice(0, t("x"))))
        assertEquals(2000L, RoomFlow.momentHoldMs(Moment.Weather(0, t("x"), t("y"), "")))
        assertEquals(3200L, RoomFlow.momentHoldMs(Moment.Weather(0, t("x"), t("y"), ""), pause = RoomFlow.Pause.LONG))
        assertNull(RoomFlow.momentHoldMs(Moment.Music(0, t("x"), "a.m4a")))
        val rent = Moment.Transfer(1, "15 francs", t("Rent"), t(""), outgoing = true)
        assertEquals(2000L, RoomFlow.momentHoldMs(rent))
        assertFalse(RoomFlow.blocks(rent))
        val theo = Moment.Transfer(-1, "50 francs", t("Theo sent"), t(""), outgoing = false)
        assertTrue(RoomFlow.blocks(theo))
        assertEquals(0L, RoomFlow.momentHoldMs(theo))
    }

    /** 누가 하는가: 편지 앞의 받는 사람 송금만 나의 일, 편지 중간에 받은 돈 (모차르트의 제안) 은 보낸 사람이 알려 주는 것. 데이터가 정하면 그것. */
    @Test fun readerActsOnlyOnTheirOwnTransfer() {
        assertTrue(Moment.Transfer(-1, "50", t(""), t("")).byReader)
        assertFalse(Moment.Transfer(2, "200 Fl.", t("Offer"), t("")).byReader)
        assertFalse(Moment.Transfer(-1, "15", t(""), t(""), outgoing = true).byReader)
        assertTrue(Moment.Transfer(3, "7", t(""), t(""), reader = true).byReader)
        assertFalse(Moment.Transfer(-1, "7", t(""), t(""), reader = false).byReader)
    }

    @Test fun timelineOrdersMomentsAroundMessages() {
        val b = RoomFlow.timeline(3, listOf(-1, 0, 2, 0, 9), note = true)
        assertEquals(listOf(
            RoomFlow.Beat.Mo(0), RoomFlow.Beat.Msg(0), RoomFlow.Beat.Mo(1), RoomFlow.Beat.Mo(3), RoomFlow.Beat.Msg(1),
            RoomFlow.Beat.Msg(2), RoomFlow.Beat.Mo(2), RoomFlow.Beat.Mo(4), RoomFlow.Beat.Note,
        ), b)
        // 다시 열 때: 문장 2개가 놓였으면 그 사이 순간까지, 다 놓였으면 노트까지
        assertEquals(0, RoomFlow.visibleFor(b, 0))
        assertEquals(5, RoomFlow.visibleFor(b, 2))
        assertEquals(b.size, RoomFlow.visibleFor(b, 3))
        assertEquals(1, RoomFlow.firstMsgFrom(b, 2))
        assertNull(RoomFlow.firstMsgFrom(b, 6))
    }

    @Test fun wordAtFollowsStarts() {
        val w = listOf(600..760, 800..900, 2500..2700)
        assertEquals(-1, RoomFlow.wordAt(0, w))
        assertEquals(0, RoomFlow.wordAt(570, w))
        assertEquals(1, RoomFlow.wordAt(1200, w))
        assertTrue(RoomFlow.lingering(1100, w, 1))
        assertFalse(RoomFlow.lingering(1200, w, 1))
        assertEquals(2, RoomFlow.wordAt(2600, w))
    }

    /** 긴 쉼 (600ms 넘게) 은 350ms 쉰 뒤 다음 낱말 250ms 앞으로. 옮긴 자리에서는 다시 옮기지 않는다 (시안의 되풀이 버그). */
    @Test fun skipSilenceOnceAndNeverLoops() {
        val w = listOf(600..900, 950..1200, 3200..3400)
        assertNull(RoomFlow.skipSilence(700, w))
        assertNull(RoomFlow.skipSilence(1000, w))       // 짧은 쉼 (50ms) 은 그대로
        assertNull(RoomFlow.skipSilence(1500, w))       // 끝 + 350 안
        assertEquals(2950, RoomFlow.skipSilence(1600, w))
        assertEquals(2950, RoomFlow.skipSilence(2800, w))
        assertNull(RoomFlow.skipSilence(2950, w))       // 옮긴 자리
        assertNull(RoomFlow.skipSilence(3000, w))
        assertNull(RoomFlow.skipSilence(3300, w))
        assertNull(RoomFlow.skipSilence(5000, w))       // 마지막 낱말 뒤
        // 옮긴 뒤 위치를 차례로 흘려도 한 번뿐
        var pos = 1250; var seeks = 0
        while (pos < 3500) { RoomFlow.skipSilence(pos, w)?.let { pos = it; seeks++ }; pos += 33 }
        assertEquals(1, seeks)
    }

    @Test fun resumeFromWordStartsJustBefore() {
        assertEquals(2350, RoomFlow.resumeSeek(2500))
        assertEquals(0, RoomFlow.resumeSeek(100))
    }

    @Test fun wordRangesMatchTokens() {
        val text = "Pour ce qui est  du travail, j'ai"
        val r = RoomFlow.wordRanges(text)
        assertEquals(WordTimes.tokens(text), r.map { text.substring(it.first, it.last + 1) })
        val ko = Breaks.keepAll("오늘 15호 캔버스를")
        assertEquals(WordTimes.tokens(ko).size, RoomFlow.wordRanges(ko).size)
    }

    /** 낱말 시각이 없으면 마디 시각에서 (앞 쉼을 뺀 목소리 몫을 글자 수로). */
    @Test fun chunkFallbackSkipsLeadPadding() {
        val text = "Mon cher Théo, merci beaucoup"
        val w = RoomFlow.chunkWords(text, listOf(0, 2000), listOf(2000, 4000), listOf("Mon cher Théo,", "merci beaucoup"))
        assertEquals(5, w.size)
        assertEquals(Spoken.LEAD_MS, w[0].first)
        assertTrue(w.zipWithNext().all { (a, b) -> a.first <= b.first })
        assertEquals(2000 + Spoken.LEAD_MS, w[3].first)
        assertTrue(w[2].last < 2000)
        assertEquals(emptyList(), RoomFlow.chunkWords(text, emptyList(), emptyList(), emptyList()))
    }
}
