package io.github.graviton94.todayletters.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 말하기 판에 그리는 억양 선 (시안 하루 흐름 ③, 2026-10-24): **한 줄로 이어진다**. 채점은 [Prosody.score] 그대로 (이것은 그리기만).
 *
 * 예전 선 ([Prosody.line]) 은 실제 낭독에서 4~19 조각, 폭의 64~91% 가 비었다 (YIN 0.20 이 낭독의 30% 남짓만 잡고, 다듬다 또 지움).
 * 그리기용 분석 ([analyze]) 과 선 ([line]):
 *
 *  1. YIN 문턱 0.35 (안 되면 가장 깊은 골이 0.5 아래일 때 그것), 이 소리 가운데 높이의 0.6~1.9배만 (ㅅ · ㅎ 같은 소리의 ‘높은 음’ 을 버림).
 *  2. 가장 큰 소리 − 25dB 아래 프레임은 믿지 않는다. 80ms 넘는 목소리 조각은 양 끝 30ms 를 뗀다 (시작 · 끝 갈고리).
 *  3. 옥타브: ±250ms 이웃 가운데에서 7반음 넘게 튀면 ±12 로 접고, 그래도 멀면 버림. Hampel (±200ms, 3.5×MAD 이고 3반음 넘게) 로 튄 점을 버림.
 *  4. 처음 · 마지막 목소리 사이의 모든 틈은 곧게 잇는다 (끊기지 않음). 250ms 넘는 쉼은 120ms 로 줄여 그린다 ([Line.x] 가 같은 시간축으로
 *     낱말 자리 · 재생 자리를 옮긴다).
 *  5. 5프레임 중앙값 → 약 70ms 가우스로 다듬고, 160 칸으로 칸마다 평균.
 *
 * 세로 폭은 예전 그대로 (95% 안쪽 폭, 3~12 반음). 내 선도 같은 길로 (메우고, 버리지 않는다) 만든 뒤 크기 흐름 DTW 로 원래 소리의 칸에 옮긴다.
 * 근거 · 시제품: 시안 audit/tools/prosody.py (continuous · analyze_display · compress_pauses), 이 파일은 그 옮김.
 */
object PitchLine {
    const val FRAMES = 160
    private const val HOP_MS = Prosody.HOP_MS
    private const val YIN = 0.35
    private const val YIN_FLOOR = 0.5
    private const val LOW = 0.6f
    private const val HIGH = 1.9f
    private const val GATE_DB = 35f
    private const val GATE_ABS = -60f
    private const val TRIM_DB = 30f
    private const val QUIET_DB = 25f
    /** 목소리 조각 양 끝에서 떼는 프레임 (30ms), 이보다 긴 조각만 (2 × 3 + 2 = 80ms). */
    private const val EDGE = 3
    private const val LOCAL = 25
    private const val OCTAVE = 7f
    private const val HAMPEL = 20
    private const val HAMPEL_K = 3.5f
    private const val HAMPEL_MIN = 3f
    private const val SMOOTH_MS = 70.0
    /** 쉼 줄이기: 가장 큰 소리 − 32dB 아래가 [PAUSE_MS] 넘게 이어지면 [KEEP_MS] 로. */
    private const val PAUSE_DB = 32f
    const val PAUSE_MS = 250
    const val KEEP_MS = 120
    private const val RANGE_MIN = 3f
    private const val RANGE_MAX = 12f

    /** 그리기용 분석: 10ms 마다 크기 (dB) 와 음높이 (Hz, 목소리 없음 0). 채점용 [Prosody.analyze] 와 따로 (다듬기는 [line] 이 한다). */
    fun analyze(samples: FloatArray, rate: Int): Prosody.Track {
        val hop = rate * HOP_MS / 1000
        val win = rate * 40 / 1000
        val n = if (samples.size < win) 0 else (samples.size - win) / hop
        val f0 = FloatArray(n); val db = FloatArray(n)
        for (i in 0 until n) {
            val off = i * hop
            var e = 0.0
            for (k in 0 until win) { val v = samples[off + k].toDouble(); e += v * v }
            db[i] = (20 * log10(sqrt(e / win).coerceAtLeast(1e-6))).toFloat()
        }
        if (n == 0) return Prosody.Track(f0, db)
        val gate = maxOf(db.max() - GATE_DB, GATE_ABS)
        for (i in 0 until n) f0[i] = if (db[i] > gate) relaxed(samples, i * hop, win, rate) else 0f
        // 말하는 사람의 높이 폭: 가운데 높이의 0.6~1.9배 밖은 마찰음 · 잡음의 ‘음높이’
        val v = f0.filter { it > 0f }
        if (v.isNotEmpty()) {
            val m = median(v.map { it.toDouble() }).toFloat()
            for (i in 0 until n) if (!(f0[i] > LOW * m && f0[i] < HIGH * m)) f0[i] = 0f
        }
        return Prosody.Track(f0, db)
    }

    /** YIN (문턱 [YIN]), 안 잡히면 누적 정규화 차이의 가장 깊은 골이 [YIN_FLOOR] 아래일 때 그 자리. */
    private fun relaxed(x: FloatArray, off: Int, win: Int, rate: Int): Float {
        val maxLag = (rate / 70).coerceAtMost(win / 2)
        val minLag = (rate / 500).coerceAtLeast(2)
        val len = win - maxLag
        val d = DoubleArray(maxLag + 1)
        for (tau in 1..maxLag) {
            var s = 0.0
            for (j in 0 until len) { val diff = (x[off + j] - x[off + j + tau]).toDouble(); s += diff * diff }
            d[tau] = s
        }
        var run = 0.0
        val cm = DoubleArray(maxLag + 1) { 1.0 }
        for (tau in 1..maxLag) { run += d[tau]; cm[tau] = if (run == 0.0) 1.0 else d[tau] * tau / run }
        var tau = minLag
        while (tau < maxLag) {
            if (cm[tau] < YIN) {
                while (tau + 1 < maxLag && cm[tau + 1] < cm[tau]) tau++
                val a = cm[tau - 1]; val b = cm[tau]; val c = cm[tau + 1]
                val den = (2 * (a - 2 * b + c)).let { if (it == 0.0) 1.0 else it }
                return (rate / (tau + ((a - c) / den).coerceIn(-1.0, 1.0))).toFloat()
            }
            tau++
        }
        var best = minLag
        for (t in minLag until maxLag) if (cm[t] < cm[best]) best = t
        return if (cm[best] < YIN_FLOOR) rate.toFloat() / best else 0f
    }

    /**
     * 화면의 선 하나: [values] 는 [FRAMES] 칸 (짧으면 프레임 수만큼), -1..1 (반음 / [range]), NaN 없음 (한 줄).
     * [semis] 는 칸마다 반음 (가운데 0, 폭으로 나누기 전), [db] 는 칸마다 크기 (내 선을 옮길 때).
     * [ms] 는 앞뒤 침묵을 뺀 실제 길이 (녹음 고리 · 저절로 멈추기). 시간 → 가로 자리는 [x].
     */
    class Line internal constructor(
        val values: FloatArray, val semis: FloatArray, val db: FloatArray, val range: Float, val ms: Int,
        /** 분석한 소리의 프레임 → 쉼을 줄인 프레임. */
        private val map: IntArray,
        /** 쉼을 줄인 시간축에서 선이 시작하는 프레임 (앞 침묵을 뺀 자리) · 그 길이 (프레임). */
        private val first: Int, private val frames: Int,
    ) {
        /** 분석한 소리의 시작에서 [ms] 밀리초 → 판 가로 자리 0..1 (쉼을 줄인 시간축, 낱말 · 재생 자리가 선과 같게). */
        fun x(ms: Int): Float {
            if (map.isEmpty() || frames <= 0) return 0f
            val f = (ms / HOP_MS).coerceIn(0, map.size - 1)
            return ((map[f] - first).toFloat() / frames).coerceIn(0f, 1f)
        }
    }

    /** 원래 소리 (그리기용 분석 [analyze]) 의 선. 그릴 것이 거의 없으면 null. */
    fun line(t: Prosody.Track, cols: Int = FRAMES): Line? {
        val (f0, db, map) = compress(t)
        val c = continuous(f0, db) ?: return null
        val n = c.semis.size
        val m = maxOf(2, minOf(cols, n))
        val semis = bins(c.semis, m)
        val loud = bins(c.db, m)
        val range = reach(semis).coerceIn(RANGE_MIN, RANGE_MAX)
        // 녹음 고리 · 저절로 멈추기의 기준은 실제 길이 (쉼을 줄이기 전, 앞뒤 침묵만 뺀 것: 예전 Prosody.preview 와 같게)
        val top = t.db.max()
        val ms = (t.db.indexOfLast { it > top - TRIM_DB } - t.db.indexOfFirst { it > top - TRIM_DB } + 1).coerceAtLeast(0) * HOP_MS
        return Line(norm(semis, range), semis, loud, range, ms, map, c.first, n)
    }

    /**
     * 채점 뒤 두 선 ([Prosody.Curves], 칸 수 같음, -1..1, NaN 없음): 원래 선 그대로, 내 선은 같은 길로 만든 뒤 크기 흐름을 DTW 로 맞춰
     * 원래 칸에 옮기고 빈 칸은 곧게 잇는다. 두 선은 같은 폭 (두 선의 95% 안쪽 폭 가운데 큰 것, 3~12 반음).
     * 원래 선이 없으면 null, 내 선이 없으면 mine 이 null.
     */
    fun curves(ref: Prosody.Track, mine: Prosody.Track, cols: Int = FRAMES): Prosody.Curves? {
        val r = line(ref, cols) ?: return null
        val y = mine(r, mine)
        val range = both(r, y)
        return Prosody.Curves(norm(r.semis, range), y?.let { norm(it, range) })
    }

    private fun both(r: Line, y: FloatArray?): Float = maxOf(reach(r.semis), y?.let { reach(it) } ?: 0f).coerceIn(RANGE_MIN, RANGE_MAX)

    /** 내 선 (반음, 원래 선의 칸에 맞춤). */
    private fun mine(r: Line, mine: Prosody.Track): FloatArray? {
        // 저절로 멈춘 녹음의 꼬리 (마지막 목소리 뒤 1초) · 첫 목소리 앞은 뗀다 (채점과 같은 [Prosody.voicedOnly])
        val (f0, db, _) = compress(Prosody.voicedOnly(mine))
        val c = continuous(f0, db) ?: return null
        val n = r.semis.size
        val m = minOf(200, c.semis.size).coerceAtLeast(2)
        val ys = bins(c.semis, m); val yd = bins(c.db, m)
        val (path, _) = Prosody.dtw(zs(r.db), zs(yd))
        val w = warp(path, n, m)
        // 맞춘 자리 (소수) 에서 내 선을 곧게 읽는다: 원래 칸 여럿이 내 칸 하나에 묶여 생기던 평평한 계단이 없다
        val out = FloatArray(n) { i ->
            val x = w[i]; val a = x.toInt().coerceIn(0, m - 1); val b = (a + 1).coerceAtMost(m - 1)
            ys[a] + (ys[b] - ys[a]) * (x - a).toFloat()
        }
        val mid = median(out.map { it.toDouble() }).toFloat()
        return FloatArray(n) { out[it] - mid }
    }

    /** 맞춘 길이 한 칸에 머무는 가장 작은 기울기 (내 칸 / 원래 칸, 고른 기울기의 몫). */
    private const val WARP_MIN_SLOPE = 0.35
    /** 맞춘 길 다듬기 (원래 칸, 가우스 시그마 = 칸 수 / 이것). */
    private const val WARP_SMOOTH = 16.0

    /**
     * DTW 길 → 원래 칸마다 내 자리 (소수, 0..[m]−1). 크기 흐름 DTW 는 원래 칸 여럿을 내 칸 하나에 (또는 반대로) 묶는다 —
     * 그대로 옮기면 내 선이 긴 평평한 계단 → 뛰어오름이 된다 (2026-10-25 폰 시험). 칸마다 평균 자리 → 가우스로 다듬기 →
     * 늘 [WARP_MIN_SLOPE] 만큼은 앞으로 (물러나거나 멈추지 않음) → 처음 0 · 끝 m−1 로 맞춘다.
     */
    internal fun warp(path: List<Pair<Int, Int>>, n: Int, m: Int): DoubleArray {
        val sum = DoubleArray(n); val cnt = IntArray(n)
        path.forEach { (i, j) -> if (i in 0 until n) { sum[i] += j.toDouble(); cnt[i]++ } }
        val diag = { i: Int -> if (n <= 1) 0.0 else i.toDouble() * (m - 1) / (n - 1) }
        val raw = DoubleArray(n) { if (cnt[it] > 0) sum[it] / cnt[it] else diag(it) }
        val sig = (n / WARP_SMOOTH).coerceAtLeast(1.0)
        val half = (3 * sig).toInt()
        val g = DoubleArray(2 * half + 1) { k -> val x = (k - half).toDouble(); exp(-x * x / (2 * sig * sig)) }
        // 끝은 비춰 늘려 (거울) 다듬는다: 처음 · 끝이 가운데로 끌려 들어가지 않게
        fun at(q: Int): Double = when {
            q < 0 -> 2 * raw[0] - raw[(-q).coerceAtMost(n - 1)]
            q >= n -> 2 * raw[n - 1] - raw[(2 * (n - 1) - q).coerceAtLeast(0)]
            else -> raw[q]
        }
        val sm = DoubleArray(n) { q -> var s = 0.0; var t = 0.0; for (k in g.indices) { s += g[k] * at(q + k - half); t += g[k] }; s / t }
        val step = if (n <= 1) 0.0 else WARP_MIN_SLOPE * (m - 1) / (n - 1)
        for (q in 1 until n) sm[q] = maxOf(sm[q], sm[q - 1] + step)
        val a = sm[0]; val b = sm[n - 1]
        return DoubleArray(n) { q -> if (b - a <= 1e-9) diag(q) else ((sm[q] - a) / (b - a) * (m - 1)).coerceIn(0.0, (m - 1).toDouble()) }
    }

    /** 가장 긴 평평한 칸 수 (이웃 칸 차이가 [eps] 아래로 이어진 길이, 시험 · 근거용). */
    fun plateau(x: FloatArray?, eps: Float = 1e-3f): Int {
        if (x == null || x.isEmpty()) return 0
        var best = 1; var run = 1
        for (q in 1 until x.size) { if (abs(x[q] - x[q - 1]) < eps) run++ else run = 1; best = maxOf(best, run) }
        return best
    }

    // ── 시제품 그대로의 길 ─────────────────────────────

    /** 쉼 줄이기: 안쪽의 [PAUSE_MS] 넘는 조용한 구간을 [KEEP_MS] 로. 돌려주는 map 은 원래 프레임 → 줄인 프레임. */
    internal fun compress(t: Prosody.Track): Triple<FloatArray, FloatArray, IntArray> {
        val n = t.size
        if (n == 0) return Triple(t.f0, t.db, IntArray(0))
        val top = t.db.max()
        val keep = BooleanArray(n) { true }
        var i = 0
        while (i < n) {
            if (t.db[i] >= top - PAUSE_DB) { i++; continue }
            var j = i
            while (j < n && t.db[j] < top - PAUSE_DB) j++
            if ((j - i) * HOP_MS >= PAUSE_MS && i > 0 && j < n) {
                val k = KEEP_MS / HOP_MS
                for (q in i + k / 2 until j - k / 2) keep[q] = false
            }
            i = j
        }
        val map = IntArray(n); var c = -1
        for (q in 0 until n) { if (keep[q]) c++; map[q] = c.coerceAtLeast(0) }
        val f = ArrayList<Float>(n); val d = ArrayList<Float>(n)
        for (q in 0 until n) if (keep[q]) { f += t.f0[q]; d += t.db[q] }
        return Triple(f.toFloatArray(), d.toFloatArray(), map)
    }

    internal class Cont(val semis: FloatArray, val db: FloatArray, val first: Int)

    /** 한 줄 곡선 (프레임마다 반음, 가운데 0). 앞뒤 침묵 자르기 포함. */
    internal fun continuous(f0all: FloatArray, dball: FloatArray): Cont? {
        if (dball.isEmpty()) return null
        val top0 = dball.max()
        val a = dball.indexOfFirst { it > top0 - TRIM_DB }.coerceAtLeast(0)
        val b = dball.indexOfLast { it > top0 - TRIM_DB }.coerceAtLeast(a)
        val f = f0all.copyOfRange(a, b + 1); val d = dball.copyOfRange(a, b + 1)
        val n = f.size
        if (n < 6) return null
        val top = d.max()
        val st = DoubleArray(n) { if (f[it] > 0f && d[it] > top - QUIET_DB) 12 * ln(f[it].toDouble()) / ln(2.0) else Double.NaN }
        // 목소리 조각마다 양 끝 (시작 · 끝 갈고리) 을 뗀다
        val ok = BooleanArray(n) { !st[it].isNaN() }
        var i = 0
        while (i < n) {
            if (!ok[i]) { i++; continue }
            var j = i
            while (j < n && ok[j]) j++
            if (j - i > 2 * EDGE + 2) { for (q in i until i + EDGE) st[q] = Double.NaN; for (q in j - EDGE until j) st[q] = Double.NaN }
            i = j
        }
        val v = st.filter { !it.isNaN() }
        if (v.size < 6) return null
        val c0 = median(v)
        for (q in 0 until n) st[q] -= c0
        // 옥타브: ±250ms 이웃 가운데에서
        val out = DoubleArray(n) { Double.NaN }
        for (q in 0 until n) {
            if (st[q].isNaN()) continue
            val w = (maxOf(0, q - LOCAL) until minOf(n, q + LOCAL + 1)).map { st[it] }.filter { !it.isNaN() }
            val m = median(w)
            var x = st[q]
            repeat(2) { if (x - m > OCTAVE) x -= 12 else if (m - x > OCTAVE) x += 12 }
            out[q] = if (abs(x - m) > OCTAVE) Double.NaN else x
        }
        // Hampel (그 자리에서 고친다: 뒤의 창은 앞에서 버린 점을 보지 않는다)
        for (q in 0 until n) {
            if (out[q].isNaN()) continue
            val w = (maxOf(0, q - HAMPEL) until minOf(n, q + HAMPEL + 1)).map { out[it] }.filter { !it.isNaN() }
            val m = median(w)
            val mad = median(w.map { abs(it - m) }) + 0.5
            if (abs(out[q] - m) > HAMPEL_K * mad && abs(out[q] - m) > HAMPEL_MIN) out[q] = Double.NaN
        }
        val idx = (0 until n).filter { !out[it].isNaN() }
        if (idx.size < 6) return null
        val full = interp(n, idx, idx.map { out[it].toFloat() })
        // 5프레임 중앙값 → 가우스 (약 70ms)
        val med = DoubleArray(n) { q -> median((maxOf(0, q - 2) until minOf(n, q + 3)).map { full[it].toDouble() }) }
        val sig = SMOOTH_MS / HOP_MS / 2.355 * 2
        val half = (3 * sig).toInt()
        val g = DoubleArray(2 * half + 1) { k -> val x = (k - half).toDouble(); exp(-x * x / (2 * sig * sig)) }
        val gs = g.sum(); for (k in g.indices) g[k] /= gs
        val sm = DoubleArray(n) { q -> var s = 0.0; for (k in g.indices) s += g[k] * med[(q + k - half).coerceIn(0, n - 1)]; s }
        val mid = median(sm.toList())
        return Cont(FloatArray(n) { (sm[it] - mid).toFloat() }, d, a)
    }

    /** [n] 칸으로 칸마다 평균 (numpy linspace 가장자리). */
    internal fun bins(x: FloatArray, m: Int): FloatArray {
        val n = x.size
        return FloatArray(m) { q ->
            val e0 = (q.toLong() * n / m).toInt(); val e1 = maxOf(((q + 1).toLong() * n / m).toInt(), e0 + 1).coerceAtMost(n)
            var s = 0.0; for (k in e0 until e1) s += x[k]; (s / (e1 - e0).coerceAtLeast(1)).toFloat()
        }
    }

    /** 곧게 잇기 ([idx] 의 값 [v], 앞 · 뒤는 끝 값 그대로). */
    private fun interp(n: Int, idx: List<Int>, v: List<Float>): FloatArray {
        val out = FloatArray(n)
        var k = 0
        for (q in 0 until n) {
            while (k + 1 < idx.size && idx[k + 1] <= q) k++
            out[q] = when {
                q <= idx.first() -> v.first()
                q >= idx.last() -> v.last()
                else -> { val a = idx[k]; val b = idx[k + 1]; v[k] + (v[k + 1] - v[k]) * (q - a).toFloat() / (b - a) }
            }
        }
        return out
    }

    private fun median(x: List<Double>): Double {
        if (x.isEmpty()) return 0.0
        val s = x.sorted(); val h = s.size / 2
        return if (s.size % 2 == 1) s[h] else (s[h - 1] + s[h]) / 2
    }

    private fun reach(x: FloatArray): Float = x.map { abs(it) }.sorted().let { if (it.isEmpty()) 0f else it[(it.size * 0.95).toInt().coerceAtMost(it.size - 1)] }
    private fun norm(x: FloatArray, range: Float) = FloatArray(x.size) { (x[it] / range).coerceIn(-1f, 1f) }
    private fun zs(x: FloatArray): FloatArray {
        val m = x.average(); val sd = sqrt(x.map { (it - m) * (it - m) }.average()).coerceAtLeast(0.5)
        return FloatArray(x.size) { ((x[it] - m) / sd).toFloat() }
    }

    /** 몇 조각으로 그려지나 (NaN 으로 끊긴 수, 시험 · 근거용). */
    fun pieces(x: FloatArray?): Int {
        if (x == null) return 0
        var n = 0; var prev = true
        for (v in x) { if (!v.isNaN() && prev) n++; prev = v.isNaN() }
        return n
    }
}
