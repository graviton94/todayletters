package io.github.graviton94.todayletters.core

/**
 * 성장 곡선 (모든 시리즈 공통). 낱말 하나가 거치는 여섯 단계와, 그것으로 재는 실력 지표.
 *
 *  만남 → 알아보기 → 떠올리기 → 쓰기 → 말하기 → 내 것
 *  (뜻을 본 적 있음 → 뜻을 고를 수 있음 → 문장 속에서 떠올림 → 듣고 씀 → 소리 내어 말함 → 한 달 넘게 기억)
 *
 * ‘혼자 읽기’ = 받은 편지의 낱말 가운데 떠올리기 단계 이상인 비율. 이 단계부터 편지 속 작은 뜻풀이가 지워진다.
 */
object Growth {
    enum class Stage { MEET, RECOGNIZE, RECALL, WRITE, SPEAK, OWN }

    fun stage(c: Card?): Stage = Stage.entries[(c?.box ?: 0).coerceIn(0, Memory.TOP)]

    /** 편지에서 뜻풀이를 지워도 되는가 (혼자 읽을 수 있는 낱말). */
    fun known(c: Card?): Boolean = c != null && c.box >= Stage.RECALL.ordinal

    /** 혼자 읽기 %: [keys] (받은 편지들의 낱말 카드 키) 가운데 [known] 의 비율. 낱말이 없으면 0. */
    fun readAlone(keys: Collection<String>, cards: Map<String, Card>): Int =
        if (keys.isEmpty()) 0 else keys.count { known(cards[it]) } * 100 / keys.size

    /** 편지 한 통을 처음부터 끝까지 혼자 읽을 수 있는가 (그 편지 낱말이 모두 [known]). */
    fun letterAlone(keys: Collection<String>, cards: Map<String, Card>): Boolean =
        keys.isNotEmpty() && keys.all { known(cards[it]) }

    /** 혼자 읽기 이정표. */
    val milestones = listOf(10, 25, 50, 75, 90, 100)
    fun nextMilestone(pct: Int): Int? = milestones.firstOrNull { it > pct }

    /** [from] 에서 [to] 로 오르며 넘은 이정표들. */
    fun crossed(from: Int, to: Int): List<Int> = milestones.filter { it in (from + 1)..to }
}
