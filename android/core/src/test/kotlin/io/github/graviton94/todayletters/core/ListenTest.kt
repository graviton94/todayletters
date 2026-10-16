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
