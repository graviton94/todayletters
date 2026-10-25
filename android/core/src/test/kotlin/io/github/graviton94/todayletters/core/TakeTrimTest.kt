package io.github.graviton94.todayletters.core

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 폰 시험 1.0.0 (13) 회화: 저절로 멈춘 녹음의 점수 · 내 선 (2026-10-25).
 * 저절로 멈추기 ([AutoStop]) 는 마지막 목소리 뒤 1초 조용해야 멈춘다 — 그 꼬리 (와 첫 목소리 앞) 가 채점에 들어가 리듬이 낮았다.
 */
class TakeTrimTest {
    private val rate = 16000
    private val rnd = java.util.Random(11)

    /** 가짜 목소리 (음절 다섯, 음높이가 [from] → [to] 로 미끄러짐). */
    private fun speech(seconds: Double, from: Double, to: Double, syllables: Int = 5): FloatArray {
        val n = (seconds * rate).toInt(); var ph = 0.0
        return FloatArray(n) { k ->
            val t = k.toDouble() / rate
            ph += 2 * Math.PI * (from + (to - from) * t / seconds) / rate
            val env = 0.25 + 0.25 * (1 - Math.cos(2 * Math.PI * syllables * t / seconds)) / 2
            (env * (0.6 * Math.sin(ph) + 0.3 * Math.sin(2 * ph) + 0.1 * Math.sin(3 * ph))).toFloat()
        }
    }

    /** 방 소음 (폰 마이크): 가장 큰 소리보다 약 [below] dB 작은 쉿 소리. */
    private fun room(seconds: Double, below: Double = 24.0): FloatArray {
        val a = 0.5 * Math.pow(10.0, -below / 20) * Math.sqrt(3.0)
        return FloatArray((seconds * rate).toInt()) { (a * (rnd.nextDouble() * 2 - 1)).toFloat() }
    }

    private fun cat(vararg x: FloatArray): FloatArray { val out = FloatArray(x.sumOf { it.size }); var o = 0; for (a in x) { a.copyInto(out, o); o += a.size }; return out }

    private val ref by lazy { speech(1.6, 180.0, 240.0) }
    private val me by lazy { speech(1.7, 130.0, 170.0) }
    /** 저절로 멈춘 녹음: 첫 목소리 앞 0.5초 · 뒤 1초 넘게 방 소음 (AutoStop 이 기다린 꼬리). */
    private val stopped by lazy { cat(room(0.5), me, room(1.1)) }

    @Test fun autoStopTailDoesNotLowerTheScore() {
        val r = Prosody.analyze(ref, rate)
        val clean = Prosody.score(r, Prosody.analyze(me, rate))
        val tail = Prosody.score(r, Prosody.analyze(stopped, rate))
        // 예전: 리듬 100 → 73 (꼬리 · 앞 소음이 그대로 들어감). 남는 차이는 앞뒤 여유 120ms 의 소음뿐
        assertTrue(kotlin.math.abs(clean.rhythm - tail.rhythm) <= 6, "rhythm $clean vs $tail")
        assertTrue(kotlin.math.abs(clean.intonation - tail.intonation) <= 3, "intonation $clean vs $tail")
        assertTrue(kotlin.math.abs(clean.total - tail.total) <= 3, "total ${clean.total} vs ${tail.total}")
    }

    @Test fun voicedSamplesKeepOnlyTheVoiceWithMargins() {
        val cut = Prosody.voicedSamples(stopped, rate)
        val ms = cut.size * 1000 / rate
        // 목소리 1.7초 + 앞뒤 여유 (각 120ms 남짓) 쯤, 꼬리 1.1초 · 앞 0.5초는 빠진다
        assertTrue(ms in 1700..2100, "ms $ms")
        // 목소리가 없으면 그대로
        val quiet = room(1.0)
        assertTrue(Prosody.voicedSamples(quiet, rate).size == quiet.size)
        // 이미 목소리뿐이면 (거의) 그대로
        assertTrue(Prosody.voicedSamples(me, rate).size >= me.size - rate / 10)
    }

    @Test fun mineLineHasNoPlateaus() {
        val c = PitchLine.curves(PitchLine.analyze(ref, rate), PitchLine.analyze(stopped, rate))!!
        val mine = c.mine!!
        assertTrue(PitchLine.plateau(c.ref) <= 6, "ref plateau ${PitchLine.plateau(c.ref)}")
        // 미끄러지는 음높이: 평평한 계단이 8칸을 넘지 않는다 (예전: 16 ~ 40칸)
        assertTrue(PitchLine.plateau(mine) <= 8, "mine plateau ${PitchLine.plateau(mine)}: ${mine.joinToString { "%.2f".format(it) }}")
        // 늘 오른다 (원래 · 내 음높이가 모두 올라가는 말): 뛰어오름 없이
        val jumps = mine.toList().zipWithNext().maxOf { (a, b) -> kotlin.math.abs(b - a) }
        assertTrue(jumps < 0.08f, "jump $jumps")
        assertTrue(mine.last() - mine.first() > 1f, "rises ${mine.first()} → ${mine.last()}")
    }

    @Test fun warpNeverStalls() {
        // 원래 칸 0..39 가 모두 내 칸 0 에 묶인 길 (계단): 다듬은 자리는 멈추지 않고 끝을 맞춘다
        val path = (0 until 40).map { it to 0 } + (40 until 100).map { it to (it - 40) * 2 }
        val w = PitchLine.warp(path, 100, 120)
        assertTrue(w.first() == 0.0 && kotlin.math.abs(w.last() - 119) < 1e-6)
        assertTrue(w.toList().zipWithNext().all { (a, b) -> b - a > 0.3 * 119 / 99 })
    }
}
