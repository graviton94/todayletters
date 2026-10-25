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

    /** 목소리 구간 앞뒤로 남기는 여유 (밀리초): 첫 자음 · 끝 숨이 잘리지 않게. */
    const val SPAN_MARGIN_MS = 120
    /** 목소리 구간의 프레임: 음높이가 잡혔고 가장 큰 소리보다 이만큼 (dB) 안쪽. */
    private const val SPAN_DB = 30f

    /**
     * 목소리 구간 (프레임): 첫 목소리 프레임 − [SPAN_MARGIN_MS] ~ 마지막 목소리 프레임 + [SPAN_MARGIN_MS].
     * 목소리 프레임 = 음높이가 잡혔고 (짧은 조각은 [clean] 이 이미 뺌) 가장 큰 소리 − 30dB 위. 목소리가 없으면 null.
     * 저절로 멈춘 녹음 ([AutoStop]) 은 마지막 목소리 뒤 1초를 더 담는다: 폰 마이크의 방 소음은 [trim] 의 30dB 안쪽이라
     * 크기만으로는 잘리지 않아 채점 · 선에 들어갔다 (2026-10-25 폰 시험, 리듬 56). 이것으로 그 꼬리 · 첫 목소리 앞을 뗀다.
     */
    fun span(t: Track): IntRange? {
        if (t.size == 0 || t.voiced == 0) return null
        val top = t.db.max()
        val first = (0 until t.size).firstOrNull { t.f0[it] > 0f && t.db[it] > top - SPAN_DB } ?: return null
        val last = (t.size - 1 downTo 0).first { t.f0[it] > 0f && t.db[it] > top - SPAN_DB }
        val m = SPAN_MARGIN_MS / HOP_MS
        return maxOf(0, first - m)..minOf(t.size - 1, last + m)
    }

    /** [span] 만 남긴 분석 (목소리가 없으면 그대로). */
    fun voicedOnly(t: Track): Track {
        val r = span(t) ?: return t
        if (r.first == 0 && r.last == t.size - 1) return t
        return Track(t.f0.copyOfRange(r.first, r.last + 1), t.db.copyOfRange(r.first, r.last + 1))
    }

    /**
     * 내 녹음 소리를 목소리 구간 ([span], 앞뒤 여유 120ms) 만 남겨 자른다: 채점 ([score]) · 판의 선 ([PitchLine.curves]) · 음절이 모두 같은 녹음을 보게.
     * 목소리가 없으면 그대로 (침묵 판정은 [spoke] 가 한다).
     */
    fun voicedSamples(samples: FloatArray, rate: Int, t: Track = analyze(samples, rate)): FloatArray {
        val r = span(t) ?: return samples
        val hop = rate * HOP_MS / 1000
        val win = rate * 40 / 1000
        val a = (r.first * hop).coerceIn(0, samples.size)
        val b = (r.last * hop + win).coerceIn(a, samples.size)
        return if (a == 0 && b == samples.size) samples else samples.copyOfRange(a, b)
    }

    /** 채점: 두 소리 모두 목소리 구간 ([voicedOnly]) → 앞뒤 침묵 자르기 ([trim]) 뒤에 잰다. */
    fun score(ref: Track, mine: Track): Score {
        val r = trim(voicedOnly(ref)); val y = trim(voicedOnly(mine))
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

    // ── 화면에 그리는 두 음높이 선 (채점과 따로: 채점은 위의 score 그대로) ──────────────
    /** 화면 선: 가까운 이웃 (±150ms) 의 가운데에서 이만큼 (반음) 넘게 떨어지면 옥타브를 접고, 그래도 떨어지면 뺀다. */
    private const val LINE_JUMP = 6f
    private const val LINE_LOCAL = 15
    private const val LINE_MEDIAN = 7
    /** 이보다 짧은 목소리 조각 (프레임) 은 그리지 않는다. */
    private const val LINE_MIN_RUN = 6
    /** 가장 큰 소리보다 이만큼 작은 프레임의 음높이는 믿지 않는다 (dB). */
    private const val LINE_QUIET = 30f
    /** 두 선을 같은 폭으로: 반음 폭의 바닥 · 천장. */
    private const val LINE_RANGE_MIN = 3f
    private const val LINE_RANGE_MAX = 12f

    /**
     * 그리기용 음높이 선 (반음, 이 소리의 가운데 높이가 0, 목소리 없음 · 믿기 어려운 프레임은 NaN 이라 선이 끊긴다):
     * 작은 소리 프레임 버림 → 이웃 가운데에서 6반음 넘게 튀면 옥타브 접기 (아니면 버림) → 7프레임 중앙값 → 60ms 안 되는 조각 버림.
     * 그릴 것이 거의 없으면 null.
     */
    fun line(t: Track): FloatArray? {
        val n = t.size
        if (n == 0 || t.voiced == 0) return null
        val top = t.db.max()
        val st = FloatArray(n) { if (t.f0[it] > 0f && t.db[it] > top - LINE_QUIET) 12 * ln(t.f0[it]) / ln(2f) else Float.NaN }
        val raw = st.filter { !it.isNaN() }.sorted()
        if (raw.size < LINE_MIN_RUN) return null
        val center = raw[raw.size / 2]
        for (i in 0 until n) if (!st[i].isNaN()) st[i] -= center
        // 옥타브 오류: 이웃 (±[LINE_LOCAL]) 의 가운데에서 크게 튄 값
        val fixed = FloatArray(n) { i ->
            val v = st[i]
            if (v.isNaN()) return@FloatArray Float.NaN
            val w = (maxOf(0, i - LINE_LOCAL)..minOf(n - 1, i + LINE_LOCAL)).map { st[it] }.filter { !it.isNaN() }.sorted()
            val m = w[w.size / 2]
            var f = v
            repeat(2) { if (f - m > LINE_JUMP) f -= 12f else if (m - f > LINE_JUMP) f += 12f }
            if (abs(f - m) > LINE_JUMP) Float.NaN else f
        }
        // 중앙값 다듬기 (끊긴 곳은 넘지 않는다)
        val half = LINE_MEDIAN / 2
        val smooth = FloatArray(n) { i ->
            if (fixed[i].isNaN()) Float.NaN else {
                var a = i; while (a > i - half && a > 0 && !fixed[a - 1].isNaN()) a--
                var b = i; while (b < i + half && b < n - 1 && !fixed[b + 1].isNaN()) b++
                val w = (a..b).map { fixed[it] }.sorted()
                w[w.size / 2]
            }
        }
        dropShort(smooth, LINE_MIN_RUN)
        val left = smooth.filter { !it.isNaN() }.sorted()
        if (left.size < LINE_MIN_RUN) return null
        val mid = left[left.size / 2]
        for (i in 0 until n) if (!smooth[i].isNaN()) smooth[i] -= mid
        return smooth
    }

    /** [min] 프레임 안 되는 조각을 NaN 으로. */
    private fun dropShort(x: FloatArray, min: Int) {
        var i = 0
        while (i < x.size) {
            if (x[i].isNaN()) { i++; continue }
            var j = i
            while (j < x.size && !x[j].isNaN()) j++
            if (j - i < min) for (k in i until j) x[k] = Float.NaN
            i = j
        }
    }

    /** 화면의 두 선: 같은 가로 칸 수 (원래 소리의 시간축), 값은 -1..1 (두 선이 같은 반음 폭으로), NaN 은 끊김. */
    class Curves(val ref: FloatArray, val mine: FloatArray?)

    /**
     * 원래 소리와 내 소리를 같은 판에 그릴 선 ([frames] 칸): 둘 다 [line] 으로 다듬고, 내 선은 크기 흐름을 DTW 로 맞춘 길을 따라
     * 원래 소리의 시간축으로 옮긴다 (빨리 · 느리게 말해도 같은 자리에 겹친다). 두 선을 같은 반음 폭 (3~12) 으로 -1..1.
     * 원래 소리의 선이 없으면 null, 내 선이 없으면 mine 이 null.
     */
    fun curves(ref: Track, mine: Track, frames: Int = 160): Curves? {
        val r = trim(ref); val y = trim(mine)
        val ra = refLine(r, frames) ?: return null
        val n = ra.size
        val b = if (y.size >= 5) line(y) else null
        val ya = b?.let { bl ->
            val m = y.size.coerceAtMost(200)
            val re = zs(FloatArray(n) { k -> r.db[(k.toLong() * r.size / n).toInt()] })
            val ye = zs(FloatArray(m) { k -> y.db[(k.toLong() * y.size / m).toInt()] })
            val (path, _) = dtw(re, ye)
            val got = Array(n) { ArrayList<Float>() }
            path.forEach { (i, j) -> val v = bl[(j.toLong() * y.size / m).toInt()]; if (!v.isNaN()) got[i] += v }
            val out = FloatArray(n) { i -> got[i].sorted().let { g -> if (g.isEmpty()) Float.NaN else g[g.size / 2] } }
            dropShort(out, 3)
            out.takeIf { o -> o.any { !it.isNaN() } }
        }
        // 같은 폭: 두 선의 95% 안쪽 폭 가운데 큰 것
        val range = maxOf(reach(ra), ya?.let { reach(it) } ?: 0f).coerceIn(LINE_RANGE_MIN, LINE_RANGE_MAX)
        return Curves(norm(ra, range), ya?.let { norm(it, range) })
    }

    /** 앞뒤를 자른 원래 소리 [r] 의 선을 [frames] 칸으로 (원래 소리의 시간축). 그릴 것이 없으면 null. */
    private fun refLine(r: Track, frames: Int): FloatArray? {
        val a = line(r) ?: return null
        val n = frames.coerceAtMost(r.size).coerceAtLeast(2)
        return FloatArray(n) { k -> a[(k.toLong() * r.size / n).toInt()] }
    }
    /** 선의 95% 안쪽 폭 (반음). */
    private fun reach(x: FloatArray): Float = x.filter { !it.isNaN() }.map { abs(it) }.sorted().let { if (it.isEmpty()) 0f else it[(it.size * 0.95).toInt().coerceAtMost(it.size - 1)] }
    private fun norm(x: FloatArray, range: Float) = FloatArray(x.size) { if (x[it].isNaN()) Float.NaN else (x[it] / range).coerceIn(-1f, 1f) }

    /**
     * 녹음 전 · 녹음 중 판의 원래 소리 선 (시안 rec_controls 2 · D): [curves] 의 원래 선과 같은 칸 · 같은 다듬기 · 같은 폭 잡기
     * (원래 선만으로 잰 폭 [range] 반음, 그래서 채점 뒤 선 모양이 거의 그대로다. 내 선이 더 넓으면 채점 뒤에 그만큼 넓어진다).
     * [ms] 는 앞뒤 침묵을 뺀 원래 소리의 길이 (녹음 고리 · 저절로 멈추기의 기준, core 의 [AutoStop]). 그릴 것이 없으면 null.
     */
    class Preview(val ref: FloatArray, val range: Float, val ms: Int)
    fun preview(ref: Track, frames: Int = 160): Preview? {
        val r = trim(ref)
        val ra = refLine(r, frames) ?: return null
        val range = reach(ra).coerceIn(LINE_RANGE_MIN, LINE_RANGE_MAX)
        return Preview(norm(ra, range), range, r.size * HOP_MS)
    }

    /** 평균 0 · 표준편차 1 로 (표준편차 바닥 [SD_FLOOR]). */
    private fun zs(x: FloatArray): FloatArray {
        val m = x.average(); val sd = sqrt(x.map { (it - m) * (it - m) }.average()).coerceAtLeast(SD_FLOOR)
        return FloatArray(x.size) { ((x[it] - m) / sd).toFloat() }
    }
}
