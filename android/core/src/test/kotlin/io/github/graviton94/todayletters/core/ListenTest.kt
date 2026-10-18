package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** 듣기 빠르기: 등급 자동 · 직접 고른 값 · 작품 설정. */
class ListenTest {
    @Test fun autoFollowsGrade() {
        assertEquals(0.85f, Listen.speed(true, 1f, Reading.Band.A1))
        assertEquals(0.9f, Listen.speed(true, 1f, Reading.Band.A2))
        assertEquals(1f, Listen.speed(true, 0.7f, Reading.Band.B1))
        assertEquals(1f, Listen.speed(true, 0.7f, Reading.Band.B2))
    }

    @Test fun manualWinsWhenAutoIsOff() {
        assertEquals(0.7f, Listen.speed(false, 0.7f, Reading.Band.B2))
        assertEquals(0.85f, Listen.speed(false, 0.85f, Reading.Band.A1))
        assertEquals(1f, Listen.speed(false, 1f, Reading.Band.A1))
        // 고를 수 없는 값은 가장 가까운 칸으로
        assertEquals(0.85f, Listen.speed(false, 0.8f, Reading.Band.A1))
    }

    @Test fun seriesSettingsDefaultToAuto() {
        val s = SeriesSettings(learn = Lang.FR)
        assertEquals(0.85f, Listen.speed(s, Reading.Band.A1))
        assertEquals(0.7f, Listen.speed(s.copy(listenAuto = false, listenSpeed = 0.7f), Reading.Band.A1))
    }

    @Test fun labels() {
        assertEquals(listOf("0.7", "0.85", "1.0"), Listen.CHOICES.map(Listen::label))
    }
}

/** 맞힌 뒤 저절로 넘어가기: 정답 발음이 끝날 때까지 기다린다 (다음 문제 소리가 자르지 않게). */
class HoldAdvanceTest {
    @Test fun waitsAtLeastTheMinimum() {
        kotlin.test.assertTrue(Listen.holdAdvance(500, sounding = false, minMs = 1100))
        kotlin.test.assertFalse(Listen.holdAdvance(1100, sounding = false, minMs = 1100))
    }

    @Test fun keepsWaitingWhileTheAnswerIsSounding() {
        kotlin.test.assertTrue(Listen.holdAdvance(2500, sounding = true, minMs = 1100))
        kotlin.test.assertFalse(Listen.holdAdvance(2550, sounding = false, minMs = 1100))
    }

    @Test fun neverWaitsPastTheCap() {
        kotlin.test.assertFalse(Listen.holdAdvance(Listen.HOLD_CAP_MS, sounding = true, minMs = 1100))
    }
}
