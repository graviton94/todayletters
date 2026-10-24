package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 녹음 저절로 멈추기 (시안 rec_controls 2 · B) · 녹음 중 선 · 원래 소리 미리 보기 선. */
class AutoStopTest {
    private val step = 40L
    /** [from]~[to] 밀리초를 40ms 프레임으로: 말소리 (−20dB · 180Hz) 또는 조용함 (−60dB). 멈추라고 한 시각 (없으면 null). */
    private fun feed(a: AutoStop, from: Long, to: Long, voice: Boolean): Long? {
        var t = from
        while (t < to) {
            if (a.frame(t, if (voice) -20f else -60f, if (voice) 180f else 0f)) return t
            t += step
        }
        return null
    }

    @Test fun midSentencePauseBeforeReachNeverStops() {
        // 원래 3초. 0.5초 조용 → 1초 말 → 1.5초 쉼 (80% 전) → 다시 말
        val a = AutoStop(3000)
        assertNull(feed(a, 0, 500, false))
        assertNull(feed(a, 500, 1500, true))
        assertNull(feed(a, 1500, 3000, false), "80% (첫 목소리 0.5초 + 2.4초) 전의 쉼으로는 멈추지 않는다")
        assertNull(feed(a, 3000, 3200, true))
        assertTrue(a.heard)
    }

    @Test fun silenceAfterReachStopsAfterOneSecond() {
        val a = AutoStop(2000)
        assertNull(feed(a, 0, 300, false))
        // 첫 목소리 300ms, 80% 자리 = 300 + 1600 = 1900ms. 2.2초까지 말하고 조용
        assertNull(feed(a, 300, 2200, true))
        val stop = feed(a, 2200, 5000, false)
        assertNotNull(stop)
        // 마지막 말소리 2160ms 에서 1초 뒤
        assertTrue(stop in 3160L..3240L, "멈춘 시각 $stop")
    }

    @Test fun silenceStartingBeforeReachCountsFromReach() {
        // 원래 2초, 첫 목소리 0ms → 80% 자리 1600ms. 1.2초에 말을 멈추면 조용함은 1600ms 부터 센다 → 2600ms 쯤
        val a = AutoStop(2000)
        assertNull(feed(a, 0, 1200, true))
        val stop = feed(a, 1200, 6000, false)
        assertNotNull(stop)
        assertTrue(stop in 2600L..2680L, "멈춘 시각 $stop")
    }

    @Test fun noVoiceNeverAutoStopsUntilCap() {
        val a = AutoStop(2000)
        val stop = feed(a, 0, 20000, false)
        assertNotNull(stop); assertTrue(stop >= AutoStop.cap(2000) && stop < AutoStop.cap(2000) + step, "멈춘 시각 $stop")
        assertFalse(a.heard)
    }

    @Test fun capStopsEvenWhileTalking() {
        assertEquals(7000L, AutoStop.cap(2000))
        assertEquals(15000L, AutoStop.cap(null))
        val a = AutoStop(2000)
        val stop = feed(a, 0, 20000, true)
        assertNotNull(stop); assertTrue(stop >= 7000L && stop < 7000L + step)
        val b = AutoStop(null)
        val s2 = feed(b, 0, 30000, true)
        assertNotNull(s2); assertTrue(s2 >= 15000L && s2 < 15000L + step)
    }

    @Test fun unknownLengthOnlyCap() {
        // 원래 길이를 모르면 조용해져도 저절로 멈추지 않는다 (상한만)
        val a = AutoStop(null)
        assertNull(feed(a, 0, 1000, true))
        assertNull(feed(a, 1000, 14000, false))
        assertNull(a.progress())
    }

    @Test fun shortBlipIsNotVoice() {
        // 툭 소리 (0.1초) 뒤 조용함: 목소리가 들어가지 않아 멈추지 않는다
        val a = AutoStop(1000)
        assertNull(feed(a, 2000, 2100, true))
        assertNull(feed(a, 2100, 4900, false))
        assertFalse(a.heard)
    }

    @Test fun roomNoiseAboveGateStillCountsAsQuiet() {
        // 방 소음 −40dB (문턱 −45 위) 이지만 말소리 −15dB 보다 25dB 작다 → 조용함
        val a = AutoStop(1000)
        var t = 0L
        while (t < 1000) { assertFalse(a.frame(t, -15f, 200f)); t += step }
        var stop: Long? = null
        while (t < 4000 && stop == null) { if (a.frame(t, -40f, 0f)) stop = t; t += step }
        assertNotNull(stop); assertTrue(stop in 1900L..2000L, "멈춘 시각 $stop")
    }

    @Test fun progressFromOnset() {
        val a = AutoStop(2000)
        a.frame(0, -60f, 0f)
        assertEquals(0f, a.progress())
        a.frame(500, -20f, 180f)
        a.frame(1500, -20f, 180f)
        assertEquals(0.5f, a.progress()!!, 0.001f)
        a.frame(4000, -20f, 180f)
        assertEquals(1f, a.progress())
        assertEquals(1000, AutoStop.estimateMs(4))
        assertNull(AutoStop.estimateMs(0))
    }

    @Test fun liveLineSharesTimeAxisAndRange() {
        // 첫 목소리 200ms, 원래 1000ms: 700ms 의 점은 x = 0.5. 한 옥타브 위로 튄 프레임은 접혀 가운데 근처
        val at = listOf(0L, 200L, 300L, 450L, 700L, 950L)
        val db = listOf(-60f, -20f, -20f, -20f, -20f, -20f)
        val hz = listOf(0f, 200f, 200f, 200f, 400f, 224.5f)
        val pts = LiveLine.points(at, db, hz, onset = 200, refMs = 1000, range = 4f)
        assertNull(pts[0])
        assertEquals(0f, pts[1]!!.first, 0.001f)
        assertEquals(0.5f, pts[4]!!.first, 0.001f)
        assertEquals(0f, pts[4]!!.second, 0.01f)
        // 224.5Hz ≈ 2 반음 위 → 2 / 4 = 0.5
        assertEquals(0.5f, pts[5]!!.second, 0.02f)
        // 원래보다 길게 말하면 끝 점이 1 (선 전체가 줄어든다)
        val long = LiveLine.points(listOf(0L, 3000L), listOf(-20f, -20f), listOf(200f, 200f), 0, 1000, 4f)
        assertEquals(1f, long[1]!!.first, 0.001f)
    }

    @Test fun previewMatchesFinalRefCurve() {
        // 미리 보기 선 = 채점 뒤 원래 선 (내 선이 원래보다 좁을 때 같은 폭)
        val rate = 16000
        fun tone(sec: Double, hz: (Double) -> Double): FloatArray {
            var ph = 0.0
            return FloatArray((sec * rate).toInt()) { k -> val t = k.toDouble() / rate; ph += 2 * Math.PI * hz(t) / rate; (0.5 * Math.sin(ph)).toFloat() }
        }
        val ref = Prosody.analyze(tone(1.2) { t -> 180 + 50 * Math.sin(t * 4) }, rate)
        val mine = Prosody.analyze(tone(1.2) { t -> 150 + 5 * Math.sin(t * 4) }, rate)
        val pv = Prosody.preview(ref)!!
        val c = Prosody.curves(ref, mine)!!
        assertEquals(c.ref.size, pv.ref.size)
        for (i in pv.ref.indices) if (!pv.ref[i].isNaN()) assertEquals(c.ref[i], pv.ref[i], 0.001f)
        assertTrue(pv.ms in 1000..1250, "원래 길이 ${pv.ms}")
    }
}
