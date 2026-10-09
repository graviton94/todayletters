package io.github.graviton94.todayletters.core

/**
 * 보상 (모든 시리즈 공통). 우표 = 쓰는 재화. 배운 만큼 모이고, 같은 일 반복에는 하루 상한.
 * 배움은 막지 않는다: 편지 · 복습 · 힌트는 우표 없이 늘 열려 있고, 우표로는 꾸밈 · 답례 · 편의만 산다.
 */
enum class Earn(val amount: Int, val dailyCap: Int) {
    LETTER(6, 18),          // 새 편지 다 읽기
    PRACTICE(2, 18),        // 낱말 맞추기 · 따라 읽기 · 답장
    REVIEW_RIGHT(1, 12),    // 복습에서 맞힌 낱말 하나
    VISITOR(4, 8),          // 손님 미니게임
    READ_ALONE(5, 50),      // 혼자 읽기 1% 오를 때마다
    DAY_COMPLETE(5, 5),     // 오늘의 일 다 함
    ACHIEVEMENT(10, 100),   // 업적
    SEAL(20, 100),          // 편지 한 통 완독 봉인 (혼자 다 읽고 다 따라 읽음)
}

/** 지갑: 잔액 · 오늘 날짜 · 오늘 종류별로 받은 양. */
data class Wallet(val balance: Int = 0, val day: Long = -1, val today: Map<Earn, Int> = emptyMap(), val total: Int = 0)

object Rewards {
    /** [e] 를 [times] 번 했을 때 받는 우표 (하루 상한 안에서). 날이 바뀌면 오늘 몫을 새로. */
    fun earn(w: Wallet, e: Earn, day: Long, times: Int = 1): Pair<Wallet, Int> {
        val base = if (w.day == day) w else w.copy(day = day, today = emptyMap())
        val already = base.today[e] ?: 0
        val gain = (e.amount * times).coerceAtMost((e.dailyCap - already).coerceAtLeast(0))
        if (gain <= 0) return base to 0
        return base.copy(balance = base.balance + gain, total = base.total + gain, today = base.today + (e to already + gain)) to gain
    }

    fun spend(w: Wallet, cost: Int): Wallet? = if (w.balance >= cost) w.copy(balance = w.balance - cost) else null

    /** 오늘 받은 우표 합. */
    fun todayTotal(w: Wallet, day: Long): Int = if (w.day == day) w.today.values.sum() else 0
}

/** 업적 판정에 쓰는 숫자들 (모든 시리즈 합). */
data class Stats(
    val lettersDone: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val wordsKnown: Int = 0,
    val wordsOwned: Int = 0,
    val readAlone: Int = 0,
    val lettersAlone: Int = 0,
    val lettersSealed: Int = 0,
    val visitors: Int = 0,
    val parcels: Int = 0,
    val chapters: Int = 0,
)

/** 업적 하나: [metric] 이 [goal] 이상이면 받는다. 이름 · 설명은 글자 목록의 ach_<id>. */
data class Achievement(val id: String, val goal: Int, val metric: (Stats) -> Int)

object Achievements {
    /**
     * 이정표 다섯 (모든 시리즈 공통). 매 문제가 아니라 ‘혼자 읽게 된 만큼’에만 걸린다:
     * 첫 봉인(편지 한 통을 혼자) · 혼자 읽기 25 / 50 / 90% · 한 장 완주.
     */
    val all = listOf(
        Achievement("alone_1", 1) { it.lettersSealed },
        Achievement("read_25", 25) { it.readAlone },
        Achievement("read_50", 50) { it.readAlone },
        Achievement("read_90", 90) { it.readAlone },
        Achievement("chapter_1", 1) { it.chapters },
    )

    /** 지금 숫자로 받을 수 있는데 아직 안 받은 업적. */
    fun newly(stats: Stats, have: Set<String>): List<Achievement> = all.filter { it.id !in have && it.metric(stats) >= it.goal }

    /** 업적의 진행 (0~1). */
    fun progress(a: Achievement, stats: Stats): Float = (a.metric(stats).toFloat() / a.goal).coerceIn(0f, 1f)
}

/**
 * 정기 소포 (모든 시리즈 공통 틀): 한 달에 한 번, 우표 [cost] 장으로 보내는 사람에게 소포를 보내면 답례가 온다.
 * 달은 달력의 달 (yyyy*12+month). 같은 달에는 한 번.
 */
object Parcel {
    fun month(year: Int, month: Int) = year * 12 + (month - 1)
    fun canSend(w: Wallet, cost: Int, lastMonth: Int, thisMonth: Int) = lastMonth != thisMonth && w.balance >= cost
}
