package io.github.graviton94.todayletters.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 억양 · 리듬 채점 (모든 언어 공통, 기기 안에서만). 발음이 맞는지는 모른다: 소리의 높낮이와 흐름만 본다.
 * 배우는 사람을 북돋우는 채점 (v23): 원어민처럼이 아니라 ‘따라 했는가’를 잰다.
 *
 *  1. 10ms 마다 소리 크기(dB)와 음높이(YIN 0.20)를 잰다. 목소리 문턱은 그 녹음의 가장 큰 소리 − 35dB (마이크마다 달라도 같게).
 *  2. 음높이 다듬기: 5프레임 중앙값, 9반음 넘게 튀면 ±12 로 접기(옥타브 오류), 50ms 안 되는 조각은 버림.
 *  3. 반음 곡선: 각자의 가운데 높이 기준 → 남녀 · 아이 목소리 차이가 사라진다. 150ms 이하 틈만 이어 주고 긴 쉼은 뺀다.
 *  4. 억양: z-점수 곡선을 시간축으로 맞춘(DTW) 뒤의 상관 r → 100 × clamp((r + 0.2) / 0.9). 오르내림 폭은 따지지 않는다.
 *  5. 리듬: 길이 비율 0.6~1.8 은 그대로 100, 크기 흐름이 곧게 맞는가.
 *  6. 총점 = 0.4 억양 + 0.4 리듬 + 0.2 완성도 (목소리가 원래의 50% 이상이면 100, 이때 총점은 40 아래로 안 내려감).
 *
 * 별: 50 ★ · 70 ★★ · 85 ★★★ ([stars]). 기록(통과)은 [PASS].
 */
object Prosody {
    const val HOP_MS = 10
    /** 통과로 기록하는 점수 (v23: 60 → 55). */
    const val PASS = 55
    /** 별 하나 · 둘 · 셋. */
    const val STAR1 = 50
    const val STAR2 = 70
    const val STAR3 = 85
    /** 목소리를 충분히 냈을 때(원래의 50% 이상) 총점의 바닥. */
    const val FLOOR = 40
    /** 따라 읽는 한 마디의 낱말 수. */
    const val SPAN_MIN = 6
    const val SPAN_MAX = 10

    /** 목소리 문턱: 가장 큰 소리보다 이만큼 작으면 목소리로 보지 않는다 (아무리 커도 −60dB 아래는 버림). */
    private const val GATE_DB = 35f
    private const val GATE_ABS = -60f
    private const val YIN = 0.20
    private const val MEDIAN = 5
    private const val JUMP = 9f
    private const val MIN_PIECE_MS = 50
    private const val MAX_GAP_MS = 150
    /** z-점수의 표준편차 바닥 (반음): 한 음으로 밋밋하게 읽은 소리가 잡음으로 부풀지 않게. */
    private const val SD_FLOOR = 0.5

    /** 목소리가 들어간 녹음으로 치는 최소: 음높이가 잡힌 프레임이 모두 [VOICE_MS] 이상, 가장 큰 소리가 [VOICE_DB] 위. */
    const val VOICE_MS = 250
    const val VOICE_DB = -45f

    /**
     * 목소리가 들어간 녹음인가 (말하기는 믿고 맡긴다: 무슨 말인지는 알아듣지 않고, 목소리가 있는지만).
     * 침묵 · 작은 잡음 · 짧은 소리 (기침 · 툭 치기) 는 아니다. 따라 읽기 · 회화 · 답장 말해 보기 · 복습 말하기가 같은 것을 쓴다.
     */
    fun spoke(t: Track): Boolean = t.size > 0 && t.voiced * HOP_MS >= VOICE_MS && t.db.max() > VOICE_DB

    /** 음절 세기: 크기 곡선 다듬기 (프레임 수, 50ms) · 두 음절 사이 가장 짧은 거리 (100ms) · 봉우리 사이 골이 이만큼 깊어야 따로 (dB). */
    private const val SYL_SMOOTH = 5
    private const val SYL_GAP = 10
    private const val SYL_DIP = 3f
    /** 가장 큰 소리보다 이만큼 작은 봉우리는 음절로 보지 않는다 (dB). */
    private const val SYL_FLOOR = 25f

    /**
     * 음절 수 (말 알아듣기 없이): 목소리가 잡힌 프레임의 크기 곡선 (50ms 로 다듬음) 에서 봉우리를 센다.
     * 목소리가 없는 프레임은 바닥으로 두어 끊긴 소리는 따로 세고, 이어진 소리 안에서는 골이 [SYL_DIP] dB 넘게 깊고
     * 100ms 넘게 떨어진 봉우리만 따로 (아니면 큰 쪽 하나로). 가장 큰 소리보다 25dB 넘게 작은 봉우리는 뺀다. 침묵은 0.
     */
    fun syllables(t: Track): Int {
        val n = t.size
        if (n == 0 || t.voiced == 0) return 0
        val top = (0 until n).filter { t.f0[it] > 0f }.maxOf { t.db[it] }
        val floor = top - 2 * SYL_FLOOR
        val raw = FloatArray(n) { if (t.f0[it] > 0f) t.db[it] else floor }
        val half = SYL_SMOOTH / 2
        val s = FloatArray(n) { i ->
            var sum = 0f; var k = 0
            for (j in maxOf(0, i - half)..minOf(n - 1, i + half)) { sum += raw[j]; k++ }
            sum / k
        }
        // 봉우리 후보: 목소리 프레임 가운데 앞보다 작지 않고 뒤보다 큰 곳 (평평한 꼭대기는 그 끝 하나)
        val cand = (0 until n).filter { i ->
            t.f0[i] > 0f && s[i] > top - SYL_FLOOR && (i == 0 || s[i] >= s[i - 1]) && (i == n - 1 || s[i] > s[i + 1])
        }
        val kept = ArrayList<Int>()
        for (p in cand) {
            val a = kept.lastOrNull()
            if (a == null) { kept += p; continue }
            var dip = Float.MAX_VALUE
            for (j in a..p) dip = minOf(dip, s[j])
            if (p - a < SYL_GAP || minOf(s[a], s[p]) - dip < SYL_DIP) { if (s[p] > s[a]) kept[kept.lastIndex] = p }
            else kept += p
        }
        return kept.size
    }

    /** 별 0~3. 다른 화면(봉인 · 이정표 등)도 이것으로 센다. */
    fun stars(score: Int): Int = when {
        score >= STAR3 -> 3
        score >= STAR2 -> 2
        score >= STAR1 -> 1
        else -> 0
    }

    /** 한 소리의 분석: 프레임마다 음높이(Hz, 목소리 없으면 0)와 크기(dB). */
    class Track(val f0: FloatArray, val db: FloatArray) {
        val size get() = f0.size
        val voiced get() = f0.count { it > 0f }
    }

    /** 억양 · 리듬 · 완성도 (0~100). */
    data class Score(val intonation: Int, val rhythm: Int, val complete: Int = 100) {
        val total: Int get() {
            if (complete == 0) return 0
            val t = (0.4 * intonation + 0.4 * rhythm + 0.2 * complete).roundToInt()
            return (if (complete >= 100) maxOf(t, FLOOR) else t).coerceIn(0, 100)
        }
        val passed get() = total >= PASS
        val stars get() = stars(total)
    }

    /** 소리(−1..1, [rate] Hz)를 분석한다. */
    fun analyze(samples: FloatArray, rate: Int): Track {
        val hop = rate * HOP_MS / 1000
        val win = rate * 40 / 1000
        val n = if (samples.size < win) 0 else (samples.size - win) / hop
        val f0 = FloatArray(n); val db = FloatArray(n)
        for (i in 0 until n) {
            val off = i * hop
            var e = 0.0
            for (k in 0 until win) { val v = samples[off + k]; e += v * v }
            db[i] = (20 * log10(sqrt(e / win).coerceAtLeast(1e-6))).toFloat()
        }
        if (n == 0) return Track(f0, db)
        val gate = maxOf(db.max() - GATE_DB, GATE_ABS)
        for (i in 0 until n) f0[i] = if (db[i] > gate) yin(samples, i * hop, win, rate, YIN) else 0f
        return Track(clean(f0), db)
    }

    /** 음높이 다듬기: 중앙값 → 옥타브 접기 → 짧은 조각 버림. */
    fun clean(raw: FloatArray): FloatArray {
        val n = raw.size
        // 1) 5프레임 중앙값 (목소리 있는 이웃만)
        val med = FloatArray(n) { i ->
            if (raw[i] <= 0f) 0f else {
                val w = (maxOf(0, i - MEDIAN / 2)..minOf(n - 1, i + MEDIAN / 2)).map { raw[it] }.filter { it > 0f }.sorted()
                w[w.size / 2]
            }
        }
        // 2) 가운데 높이에서 9반음 넘게 튄 값은 한 옥타브씩 접는다
        val voiced = med.filter { it > 0f }.sorted()
        if (voiced.isNotEmpty()) {
            val center = voiced[voiced.size / 2]
            for (i in 0 until n) if (med[i] > 0f) {
                var f = med[i]
                repeat(3) {
                    val st = 12 * ln(f / center) / ln(2f)
                    if (st > JUMP) f /= 2f else if (st < -JUMP) f *= 2f
                }
                med[i] = f
            }
        }
        // 3) 50ms 안 되는 목소리 조각은 버린다
        val minRun = MIN_PIECE_MS / HOP_MS
        var i = 0
        while (i < n) {
            if (med[i] <= 0f) { i++; continue }
            var j = i
            while (j < n && med[j] > 0f) j++
            if (j - i < minRun) for (k in i until j) med[k] = 0f
            i = j
        }
        return med
    }

    /** YIN: 70~500Hz 사이의 기본 주파수. 목소리가 아니면 0. */
    fun yin(x: FloatArray, off: Int, win: Int, rate: Int, threshold: Double = YIN): Float {
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

    /**
     * 반음 곡선: 목소리 있는 프레임의 가운데 높이 기준. 150ms 이하의 틈만 앞뒤를 이어 메우고,
     * 그보다 긴 쉼과 앞뒤 무성 구간은 NaN (그리지도 채점하지도 않는다). 목소리가 거의 없으면 null.
     */
    fun semitones(f0: FloatArray): FloatArray? {
        val voiced = f0.filter { it > 0f }
        if (voiced.size < 5) return null
        val med = voiced.sorted()[voiced.size / 2]
        val out = FloatArray(f0.size) { if (f0[it] > 0f) (12 * ln(f0[it] / med) / ln(2f)) else Float.NaN }
        val maxGap = MAX_GAP_MS / HOP_MS
        var i = 0
        while (i < out.size) {
            if (!out[i].isNaN()) { i++; continue }
            var j = i
            while (j < out.size && out[j].isNaN()) j++
            if (i > 0 && j < out.size && j - i <= maxGap) {
                val a = out[i - 1]; val b = out[j]
                for (k in i until j) out[k] = a + (b - a) * (k - i + 1) / (j - i + 1)
            }
            i = j
        }
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
        if (r.size < 5 || y.size < 5 || y.voiced == 0) return Score(0, 0, 0)
        // 완성도: 목소리가 원래의 절반 이상이면 100
        val complete = if (r.voiced == 0) 100 else (100.0 * y.voiced / (0.5 * r.voiced)).roundToInt().coerceIn(0, 100)
        return Score(intonation(r, y), rhythm(r, y), complete)
    }

    /** 억양: z-점수 곡선을 DTW 로 맞춘 뒤의 상관. 오르내림 폭은 따지지 않는다. */
    private fun intonation(r: Track, y: Track): Int {
        val rs = semitones(r.f0)?.filter { !it.isNaN() }?.toFloatArray()
        val ys = semitones(y.f0)?.filter { !it.isNaN() }?.toFloatArray()
        if (rs == null || ys == null || rs.size < 5 || ys.size < 5) return 0
        val a = shrink(rs); val b = shrink(ys)
        val (path, _) = dtw(zs(a), zs(b))
        val pa = path.map { a[it.first].toDouble() }; val pb = path.map { b[it.second].toDouble() }
        val ma = pa.average(); val mb = pb.average()
        val cov = pa.indices.sumOf { (pa[it] - ma) * (pb[it] - mb) } / pa.size
        val sa = sqrt(pa.sumOf { (it - ma) * (it - ma) } / pa.size).coerceAtLeast(SD_FLOOR)
        val sb = sqrt(pb.sumOf { (it - mb) * (it - mb) } / pb.size).coerceAtLeast(SD_FLOOR)
        val corr = (cov / (sa * sb)).coerceIn(-1.0, 1.0)
        return (100 * ((corr + 0.2) / 0.9).coerceIn(0.0, 1.0)).roundToInt()
    }

    /** 리듬: 길이 비율 0.6~1.8 은 감점 없음 + 크기 흐름을 맞춘 길이 대각선에서 얼마나 벗어나나. */
    private fun rhythm(r: Track, y: Track): Int {
        val ratio = y.size.toDouble() / r.size
        val dur = when {
            ratio < 0.6 -> exp(-ln(0.6 / ratio) * 3)
            ratio > 1.8 -> exp(-ln(ratio / 1.8) * 3)
            else -> 1.0
        }
        val re = zs(shrink(r.db)); val ye = zs(shrink(y.db))
        val (path, _) = dtw(re, ye)
        val dev = path.map { (i, j) -> abs(i.toDouble() / re.size - j.toDouble() / ye.size) }.average()
        val straight = (1 - dev * 3).coerceIn(0.0, 1.0)
        return (100 * (0.5 * dur + 0.5 * straight)).roundToInt().coerceIn(0, 100)
    }

    /**
     * 긴 문장을 따라 읽을 마디로: 낭독 덩어리(낱말 수 [words])를 차례로 묶어 6~10 낱말씩.
     * 덩어리는 쪼개지 않는다. 문장이 10 낱말 이하면 통째로 하나. 돌려주는 것은 덩어리 번호의 범위들.
     */
    fun spans(words: List<Int>, min: Int = SPAN_MIN, max: Int = SPAN_MAX): List<IntRange> {
        if (words.isEmpty()) return emptyList()
        if (words.sum() <= max) return listOf(words.indices)
        val out = ArrayList<IntRange>()
        var from = 0; var count = 0
        for (k in words.indices) {
            if (count > 0 && count >= min && count + words[k] > max) { out += from until k; from = k; count = 0 }
            count += words[k]
        }
        // 끝에 남은 짧은 마디(최소의 절반 미만)는 앞 마디에 붙인다
        if (out.isNotEmpty() && count < min / 2) out[out.lastIndex] = out.last().first until words.size
        else out += from until words.size
        return out
    }

    /** 평균 0 · 표준편차 1 로 (표준편차 바닥 [SD_FLOOR]). */
    private fun zs(x: FloatArray): FloatArray {
        val m = x.average(); val sd = sqrt(x.map { (it - m) * (it - m) }.average()).coerceAtLeast(SD_FLOOR)
        return FloatArray(x.size) { ((x[it] - m) / sd).toFloat() }
    }
}
