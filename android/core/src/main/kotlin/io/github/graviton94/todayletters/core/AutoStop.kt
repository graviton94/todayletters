package io.github.graviton94.todayletters.core

import kotlin.math.ln

/**
 * 말하기 녹음을 저절로 멈추기 (시안 rec_controls 2 · B, 따라 읽기 · 회화 · 답장 말해 보기 · 복습 말하기 공통).
 * 녹음 프레임마다 (녹음 시작부터 밀리초, 크기 dB, 음높이 Hz · 목소리 없으면 0) 를 [frame] 에 넣으면 멈출 때 true.
 *
 * 저절로 멈추는 것은 셋이 모두 맞을 때뿐:
 *  1. 이번 녹음에 목소리가 들어갔다 (음높이가 잡힌 [Prosody.VOICE_DB] 위의 소리가 모두 [Prosody.VOICE_MS] 이상, [Prosody.spoke] 와 같은 문턱).
 *  2. 첫 목소리부터 원래 문장 길이 [refMs] 의 [REACH] (80%) 를 지났다.
 *  3. 그 뒤로 [QUIET_MS] (1초) 내내 조용하다. 조용함은 80% 자리부터 센다: 문장 가운데의 쉼 (80% 전) 으로는 절대 멈추지 않는다.
 * 조용함 = 목소리 문턱 ([Prosody.VOICE_DB]) 아래이거나 이번 녹음의 가장 큰 소리보다 [QUIET_DB] 넘게 작은 소리 (방 소음이 문턱 위여도).
 * 원래 길이를 모르면 (null) 저절로 멈추지 않고 [cap] 만. [cap] (원래 × 2 + 3초, 모르면 15초) 을 넘으면 언제나 멈춘다. 직접 멈추기는 그대로.
 */
class AutoStop(val refMs: Int?) {
    companion object {
        const val REACH = 0.8
        const val QUIET_MS = 1000L
        const val QUIET_DB = 20f
        const val CAP_EXTRA_MS = 3000
        const val NO_REF_CAP_MS = 15000
        /** 원래 소리가 없을 때 글자로 어림한 음절 하나의 길이 (배우는 사람의 빠르기, 밀리초). */
        const val SYLLABLE_MS = 250
        private const val MAX_FRAME_MS = 200L

        /** 이 녹음의 상한 (밀리초, 녹음 시작부터). */
        fun cap(refMs: Int?): Long = if (refMs == null || refMs <= 0) NO_REF_CAP_MS.toLong() else refMs * 2L + CAP_EXTRA_MS

        /** 원래 소리가 없을 때: 글자로 어림한 음절 수 ([SpeakCheck.estimate]) 로 문장 길이를 어림. 모르면 null. */
        fun estimateMs(syllables: Int?): Int? = syllables?.takeIf { it > 0 }?.let { it * SYLLABLE_MS }
    }

    /** 첫 목소리의 시각 (아직이면 -1). 녹음 고리 · 실시간 선의 0 자리. */
    var onset = -1L
        private set
    private var voicedMs = 0L
    private var peak = Float.NEGATIVE_INFINITY
    private var lastLoud = -1L
    private var lastAt = -1L
    /** 마지막 프레임의 시각 (아직 없으면 0). */
    val now: Long get() = lastAt.coerceAtLeast(0L)

    /** 이번 녹음에 목소리가 들어갔는가 (1). */
    val heard: Boolean get() = voicedMs >= Prosody.VOICE_MS

    /** 원래 길이를 얼마나 지났나 (0..1, 첫 목소리부터). 원래 길이를 모르면 null (고리 없음). */
    fun progress(at: Long = now): Float? {
        val r = refMs?.takeIf { it > 0 } ?: return null
        if (onset < 0) return 0f
        return ((at - onset).toFloat() / r).coerceIn(0f, 1f)
    }

    /** 프레임 하나 ([at] 녹음 시작부터 밀리초). 멈출 때가 되면 true. */
    fun frame(at: Long, db: Float, hz: Float): Boolean {
        // 프레임 길이 (첫 프레임 · 끊긴 뒤의 긴 틈은 한 프레임만큼만 센다)
        val dt = if (lastAt < 0) 0L else (at - lastAt).coerceIn(0L, MAX_FRAME_MS)
        lastAt = at
        val voice = hz > 0f && db > Prosody.VOICE_DB
        if (voice) {
            if (onset < 0) onset = at
            voicedMs += dt
        }
        if (db > Prosody.VOICE_DB) peak = maxOf(peak, db)
        if (db > Prosody.VOICE_DB && db > peak - QUIET_DB) lastLoud = at
        if (at >= cap(refMs)) return true
        val r = refMs?.takeIf { it > 0 } ?: return false
        if (!heard || onset < 0) return false
        val reach = onset + (REACH * r).toLong()
        if (at < reach) return false
        return at - maxOf(lastLoud, reach) >= QUIET_MS
    }
}

/**
 * 녹음 중 내 음높이 선 (시안 rec_controls 2 · D): 원래 소리 점선과 같은 판 · 같은 시간축 · 같은 폭.
 * 가로 = (프레임 시각 − 첫 목소리) / max(원래 길이, 지금까지 말한 길이) → 0..1 (원래보다 길게 말하면 선 전체가 줄어든다),
 * 세로 = 내 목소리 가운데 높이 (지금까지의 중앙값) 기준 반음 / [range] (원래 선과 같은 폭, [Prosody.Preview.range]) → -1..1.
 * 작은 소리 (가장 큰 소리 − 30dB 아래) 는 버리고, 가운데에서 6반음 넘게 튄 값은 옥타브를 접는다 (그래도 멀면 버림) — 채점 뒤 선 ([Prosody.line]) 과 같은 규칙.
 */
object LiveLine {
    private const val QUIET = 30f
    private const val JUMP = 6f

    /** 점 하나 (x 0..1, y -1..1). 목소리가 없는 프레임은 null (선이 끊긴다). */
    fun points(at: List<Long>, db: List<Float>, hz: List<Float>, onset: Long, refMs: Int?, range: Float): List<Pair<Float, Float>?> {
        val n = minOf(at.size, db.size, hz.size)
        if (n == 0 || onset < 0) return emptyList()
        val top = (0 until n).maxOf { db[it] }
        val st = FloatArray(n) { if (hz[it] > 0f && db[it] > top - QUIET && at[it] >= onset) 12 * ln(hz[it]) / ln(2f) else Float.NaN }
        val ok = st.filter { !it.isNaN() }.sorted()
        if (ok.isEmpty()) return emptyList()
        val center = ok[ok.size / 2]
        val span = maxOf((refMs ?: 0).toLong(), at[n - 1] - onset, 1L).toFloat()
        val r = range.coerceAtLeast(1f)
        return (0 until n).map { i ->
            if (st[i].isNaN()) return@map null
            var v = st[i] - center
            repeat(2) { if (v > JUMP) v -= 12f else if (v < -JUMP) v += 12f }
            if (kotlin.math.abs(v) > JUMP + 6f) null
            else ((at[i] - onset) / span).coerceIn(0f, 1f) to (v / r).coerceIn(-1f, 1f)
        }
    }
}
