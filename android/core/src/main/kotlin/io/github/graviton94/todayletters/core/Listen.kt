package io.github.graviton94.todayletters.core

/**
 * 듣기 빠르기 (시안 slow.html): 소리 파일은 그대로, 재생할 때 빠르기만 바꾼다 (높낮이 유지).
 * 소리가 나는 곳마다 단추 둘: 스피커 = 이 작품의 기본 빠르기 ([speed]), 0.7× = [SLOW] 로 한 번 (기본 빠르기는 바꾸지 않음).
 * 기본 빠르기는 작품마다: ‘등급에 맞춰 자동’ (켜짐이 처음) 이면 언어 등급대로 A1 0.85 · A2 0.9 · B1 이상 1.0,
 * 끄면 고른 값 ([CHOICES] 중 하나). 직접 고르면 자동이 꺼진다 (고른 것이 우선).
 */
object Listen {
    /** 천천히 단추의 빠르기. */
    const val SLOW = 0.7f
    /** 설정에서 고를 수 있는 기본 빠르기. */
    val CHOICES = listOf(0.7f, 0.85f, 1f)

    /** 등급에 맞춘 빠르기. */
    fun auto(band: Reading.Band): Float = when (band) {
        Reading.Band.A1 -> 0.85f
        Reading.Band.A2 -> 0.9f
        else -> 1f
    }

    /** 이 작품의 기본 빠르기: 자동이면 등급대로, 아니면 고른 값 (고를 수 없는 값이면 가장 가까운 것). */
    fun speed(auto: Boolean, manual: Float, band: Reading.Band): Float =
        if (auto) auto(band) else CHOICES.minBy { kotlin.math.abs(it - manual) }

    fun speed(s: SeriesSettings, band: Reading.Band): Float = speed(s.listenAuto, s.listenSpeed, band)

    /** 정답 발음을 기다리는 가장 긴 시간 (소리가 멈춘 채 남아도 퀴즈가 서 있지 않게). */
    const val HOLD_CAP_MS = 8000L

    /**
     * 맞힌 뒤 저절로 다음 문제로 넘어가기 전에 더 기다릴까: 적어도 [minMs] 는 두고, 정답 발음이 아직 울리면([sounding]) 끝날 때까지
     * (최대 [capMs]). 다음 문제의 자동 낭독이 정답 발음을 자르지 않게. 사람이 ‘다음’ 을 누르면 이것과 상관없이 멈추고 넘어간다.
     */
    fun holdAdvance(elapsedMs: Long, sounding: Boolean, minMs: Long, capMs: Long = HOLD_CAP_MS): Boolean =
        elapsedMs < capMs && (elapsedMs < minMs || sounding)

    /** 설정 칸의 글자 ("0.7" · "0.85" · "1.0"). */
    fun label(v: Float): String = if (v == 1f) "1.0" else v.toString().trimEnd('0')
}
