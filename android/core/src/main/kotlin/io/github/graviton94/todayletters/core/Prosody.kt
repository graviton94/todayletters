package io.github.graviton94.todayletters.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 억양 · 리듬 채점 (모든 언어 공통, 기기 안에서만). 발음이 맞는지는 모른다: 소리의 높낮이와 흐름만 본다.
 *
 *  1. 10ms 마다 음높이(YIN)와 소리 크기(dB)를 잰다.
 *  2. 앞뒤 침묵을 잘라 낸다.
 *  3. 음높이는 각자의 가운데 높이에서 몇 반음 오르내렸나로 바꾼다 → 남녀 · 아이 목소리 차이가 사라진다.
 *  4. 두 곡선을 시간축으로 맞춰(DTW) 반음 차이의 평균 → 억양 점수.
 *  5. 길이 비율과 맞춘 길이 얼마나 곧은가(서두르거나 늘어진 곳) → 리듬 점수.
 */
object Prosody {
    const val HOP_MS = 10
    /** 통과 (v18: 70 → 60). 75 좋아요 · 90 훌륭해요는 화면의 말. */
    const val PASS = 60
    const val GOOD = 75
    const val GREAT = 90

    /** 한 소리의 분석: 프레임마다 음높이(Hz, 목소리 없으면 0)와 크기(dB). */
    class Track(val f0: FloatArray, val db: FloatArray) {
        val size get() = f0.size
    }

    data class Score(val intonation: Int, val rhythm: Int) {
        val total get() = (intonation + rhythm) / 2
        val passed get() = total >= PASS
    }

    /** 소리(−1..1, [rate] Hz)를 분석한다. */
    fun analyze(samples: FloatArray, rate: Int): Track {
        val hop = rate * HOP_MS / 1000
        val win = rate * 40 / 1000
        val n = ((samples.size - win) / hop).coerceAtLeast(0)
        val f0 = FloatArray(n); val db = FloatArray(n)
        for (i in 0 until n) {
            val off = i * hop
            var e = 0.0
            for (k in 0 until win) { val v = samples[off + k]; e += v * v }
            val rms = sqrt(e / win)
            db[i] = (20 * log10(rms.coerceAtLeast(1e-6))).toFloat()
            f0[i] = if (db[i] > -45f) yin(samples, off, win, rate) else 0f
        }
        return Track(f0, db)
    }

    /** YIN: 70~500Hz 사이의 기본 주파수. 목소리가 아니면 0. */
    fun yin(x: FloatArray, off: Int, win: Int, rate: Int, threshold: Double = 0.15): Float {
        val maxLag = (rate / 70).coerceAtMost(win / 2)
        val minLag = rate / 500
        val d = DoubleArray(maxLag + 1)
        for (tau in 1..maxLag) {
            var s = 0.0
            for (j in 0 until win - maxLag) { val diff = x[off + j] - x[off + j + tau]; s += diff * diff }
            d[tau] = s
        }
        // 누적 평균으로 나눈 차이
        var run = 0.0
        val cm = DoubleArray(maxLag + 1) { 1.0 }
        for (tau in 1..maxLag) { run += d[tau]; cm[tau] = if (run == 0.0) 1.0 else d[tau] * tau / run }
        var tau = minLag.coerceAtLeast(2)
        while (tau < maxLag) {
            if (cm[tau] < threshold) {
                while (tau + 1 < maxLag && cm[tau + 1] < cm[tau]) tau++
                // 포물선 보간
                val a = cm[tau - 1]; val b = cm[tau]; val c = cm[tau + 1]
                val shift = (a - c) / (2 * (a - 2 * b + c)).let { if (it == 0.0) 1.0 else it }
                return (rate / (tau + shift.coerceIn(-1.0, 1.0))).toFloat()
            }
            tau++
        }
        return 0f
    }

    /** 앞뒤 침묵 자르기: 가장 큰 소리보다 [below] dB 넘게 작은 프레임. */
    fun trim(t: Track, below: Float = 30f): Track {
        if (t.size == 0) return t
        val top = t.db.max()
        val first = t.db.indexOfFirst { it > top - below }.coerceAtLeast(0)
        val last = t.db.indexOfLast { it > top - below }.coerceAtLeast(first)
        return Track(t.f0.copyOfRange(first, last + 1), t.db.copyOfRange(first, last + 1))
    }

    /** 반음 곡선: 목소리 있는 프레임의 가운데 높이 기준. 목소리 없는 곳은 앞뒤 값으로 메운다. 목소리가 거의 없으면 null. */
    fun semitones(f0: FloatArray): FloatArray? {
        val voiced = f0.filter { it > 0f }
        if (voiced.size < 5) return null
        val med = voiced.sorted()[voiced.size / 2]
        val out = FloatArray(f0.size) { if (f0[it] > 0f) (12 * ln(f0[it] / med) / ln(2f)) else Float.NaN }
        var last = out.firstOrNull { !it.isNaN() } ?: 0f
        for (i in out.indices) if (out[i].isNaN()) out[i] = last else last = out[i]
        return out
    }

    /** DTW: 두 곡선의 맞춘 길(쌍의 목록)과 평균 거리. */
    fun dtw(a: FloatArray, b: FloatArray, band: Double = 0.25): Pair<List<Pair<Int, Int>>, Float> {
        val n = a.size; val m = b.size
        val inf = Float.MAX_VALUE / 4
        val c = Array(n + 1) { FloatArray(m + 1) { inf } }
        c[0][0] = 0f
        // 너무 멀리 휘지 않게: 대각선에서 [band] 만큼만 (밋밋한 소리를 억지로 맞추지 못하게)
        val w = (band * maxOf(n, m)).toInt().coerceAtLeast(abs(n - m) + 1)
        for (i in 1..n) {
            val center = (i.toLong() * m / n).toInt()
            for (j in maxOf(1, center - w)..minOf(m, center + w)) {
                c[i][j] = abs(a[i - 1] - b[j - 1]) + minOf(c[i - 1][j], c[i][j - 1], c[i - 1][j - 1])
            }
        }
        val path = ArrayList<Pair<Int, Int>>()
        var i = n; var j = m
        while (i > 0 && j > 0) {
            path += (i - 1) to (j - 1)
            val d = c[i - 1][j - 1]; val u = c[i - 1][j]; val l = c[i][j - 1]
            when { d <= u && d <= l -> { i--; j-- }; u < l -> i--; else -> j-- }
        }
        path.reverse()
        return path to (c[n][m] / path.size.coerceAtLeast(1))
    }

    /** 긴 곡선은 [max] 프레임으로 줄여 계산을 가볍게. */
    private fun shrink(x: FloatArray, max: Int = 200): FloatArray =
        if (x.size <= max) x else FloatArray(max) { k -> x[(k.toLong() * x.size / max).toInt()] }

    fun score(ref: Track, mine: Track): Score {
        val r = trim(ref); val y = trim(mine)
        if (r.size < 5 || y.size < 5) return Score(0, 0)
        val rs = semitones(r.f0); val ys = semitones(y.f0)
        // 억양: 맞춘 뒤 반음 차이 평균 (0 반음 → 100, 1.5 반음 → 약 55) × 오르내림 폭이 원래만큼인가 (밋밋하면 깎임)
        val intonation = if (rs == null || ys == null) 0 else {
            val (_, mean) = dtw(shrink(rs), shrink(ys))
            val swing = sqrt((sd(ys) / sd(rs).coerceAtLeast(0.3)).coerceAtMost(1.0))
            (100 * exp(-mean / 2.5) * swing).toInt().coerceIn(0, 100)
        }
        // 리듬: 길이 비율 + 크기 흐름을 맞춘 길이 대각선에서 얼마나 벗어나나
        val dur = exp(-abs(ln(y.size.toDouble() / r.size)) * 1.5)
        val re = zs(shrink(r.db)); val ye = zs(shrink(y.db))
        val (path, _) = dtw(re, ye)
        val dev = path.map { (i, j) -> abs(i.toDouble() / re.size - j.toDouble() / ye.size) }.average()
        val straight = (1 - dev * 4).coerceIn(0.0, 1.0)
        val rhythm = (100 * (0.5 * dur + 0.5 * straight)).toInt().coerceIn(0, 100)
        return Score(intonation, rhythm)
    }

    private fun sd(x: FloatArray): Double { val m = x.average(); return sqrt(x.map { (it - m) * (it - m) }.average()) }

    /** 평균 0 · 표준편차 1 로. */
    private fun zs(x: FloatArray): FloatArray {
        val m = x.average(); val sd = sqrt(x.map { (it - m) * (it - m) }.average()).coerceAtLeast(1e-3)
        return FloatArray(x.size) { ((x[it] - m) / sd).toFloat() }
    }
}
