package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** 듣기 빠르기: 등급 자동 · 직접 고른 값 · 작품 설정. */
class ListenTest {
    /** 낭독은 이미 차분하게 만들어 두어서 등급과 상관없이 만든 그대로 (2026-10-25 시안 room 3). */
    @Test fun autoIsAsRecordedForEveryBand() {
        Reading.Band.entries.forEach { assertEquals(1f, Listen.speed(true, 0.7f, it)) }
    }

    @Test fun manualWinsWhenAutoIsOff() {
        assertEquals(0.8f, Listen.speed(false, 0.8f, Reading.Band.B2))
        assertEquals(0.9f, Listen.speed(false, 0.9f, Reading.Band.A1))
        assertEquals(1f, Listen.speed(false, 1f, Reading.Band.A1))
    }

    /** 예전에 고른 값: 0.7 → 0.8, 0.85 → 0.9, 그 밖은 가장 가까운 칸. */
    @Test fun storedSpeedsMigrate() {
        assertEquals(0.8f, Listen.migrate(0.7f))
        assertEquals(0.8f, Listen.migrate(0.5f))
        assertEquals(0.9f, Listen.migrate(0.85f))
        assertEquals(0.8f, Listen.migrate(0.8f))
        assertEquals(0.9f, Listen.migrate(0.9f))
        assertEquals(1f, Listen.migrate(1f))
        assertEquals(1f, Listen.migrate(1.2f))
        assertEquals(0.8f, Listen.speed(false, 0.7f, Reading.Band.A1))
        assertEquals(0.9f, Listen.speed(false, 0.85f, Reading.Band.A1))
    }

    @Test fun seriesSettingsDefaultToAuto() {
        val s = SeriesSettings(learn = Lang.FR)
        assertEquals(1f, Listen.speed(s, Reading.Band.A1))
        assertEquals(0.8f, Listen.speed(s.copy(listenAuto = false, listenSpeed = 0.7f), Reading.Band.A1))
    }

    @Test fun slowIsPointEight() {
        assertEquals(0.8f, Listen.SLOW)
        assertEquals(listOf("0.8", "0.9", "1.0"), Listen.CHOICES.map(Listen::label))
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
