package io.github.graviton94.todayletters.core

/**
 * 보상 (모든 시리즈 공통 틀). 화폐는 시리즈마다 따로 (반 고흐 = 우표, 모차르트 = 두카트: 이름만 키트에서).
 * 화폐는 오늘의 여정에서 실제로 한 걸음으로만 모인다 (편지 · 편지 걸음 · 오늘의 복습 · 여정 완료) — 그리고 배움의 이정표
 * (봉인 · 혼자 읽기 · 메달 · 전시 · 낭독회). 같은 일 반복에는 하루 상한. 배움은 막지 않는다: 편지 · 복습 · 힌트는 화폐 없이 늘 열려 있고,
 * 화폐로는 쉼표 · 다음 편지 미리 열기 · 갤러리 소장품 고르기와 꾸미기(액자 · 카드 테두리)만 산다.
 */
enum class Earn(val amount: Int, val dailyCap: Int) {
    LETTER(6, 18),          // 새 편지 다 읽기
    PRACTICE(2, 18),        // 편지 걸음 (낱말 맞추기 · 따라 읽기 · 답장) 을 건너뛰지 않고 했을 때
    REVIEW_RIGHT(1, 12),    // 오늘의 복습에서 맞힌 낱말 하나
    SHADOW_PASS(2, 20),     // 따라 읽기 마디 통과
    READ_ALONE(5, 50),      // 혼자 읽기 1% 오를 때마다
    JOURNEY(10, 10),        // 오늘의 여정 완료 (작품마다 하루 한 번)
    RECITAL(15, 15),        // 일요일 낭독회
    STREAK(4, 4),           // 이어 읽은 날 (하루 한 번)
    ACHIEVEMENT(10, 100),   // 메달 (열쇠 이름은 예전 이정표 그대로: 지갑에 저장된 이름)
    SEAL(20, 100),          // 편지 한 통 완독 봉인 (낱말을 다 떠올리고 따라 읽기를 함)
    EXHIBITION(50, 50),     // 이달의 전시를 다 모음 (v21 3단계 E4, 전시마다 한 번)
}

/** 지갑: 잔액 · 오늘 날짜 · 오늘 종류별로 받은 양. */
data class Wallet(val balance: Int = 0, val day: Long = -1, val today: Map<Earn, Int> = emptyMap(), val total: Int = 0)

object Rewards {
    /** [e] 를 [times] 번 했을 때 받는 화폐 (하루 상한 안에서). 날이 바뀌면 오늘 몫을 새로. */
    fun earn(w: Wallet, e: Earn, day: Long, times: Int = 1): Pair<Wallet, Int> {
        val base = if (w.day == day) w else w.copy(day = day, today = emptyMap())
        val already = base.today[e] ?: 0
        val gain = (e.amount * times).coerceAtMost((e.dailyCap - already).coerceAtLeast(0))
        if (gain <= 0) return base to 0
        return base.copy(balance = base.balance + gain, total = base.total + gain, today = base.today + (e to already + gain)) to gain
    }

    fun spend(w: Wallet, cost: Int): Wallet? = if (w.balance >= cost) w.copy(balance = w.balance - cost) else null

    /** 오늘 받은 화폐 합. */
    fun todayTotal(w: Wallet, day: Long): Int = if (w.day == day) w.today.values.sum() else 0
}

/** 화폐로 사는 것 (모든 시리즈 같은 값). */
enum class Spend(val cost: Int) {
    /** 하루 쉬어도 이어 읽기가 끊기지 않는다 (최대 [Streak.MAX_RESTS] 개). */
    REST(20),
    /** 내일 올 편지를 오늘 미리 연다. */
    EARLY_LETTER(120),
    /** 갤러리의 빈칸 하나를 골라 건다. */
    PICK_PIECE(200),
    /** 가진 작품 하나의 액자를 바꾼다 (v21 3단계 E3). */
    FRAME(40),
    /** 작품 카드 테두리 하나 (한 번 사면 계속, E3). */
    BORDER(60),
}

/** 작품 카드 테두리 (E5). 기본은 처음부터 있다. */
enum class Border { PLAIN, POSTMARK, GILT, PAINT }

/**
 * 메달 · 레벨 판정에 쓰는 숫자들 (모든 작품을 합친 것, 또는 작품 하나의 것).
 * [readAlongStars] 는 따라 읽기 마디마다 받은 가장 많은 별의 합 ([Prosody.Score.stars]), [walksDone] 은 끝까지 걸은 그림 산책 수,
 * [seriesCompleted] 는 편지를 다 읽은 작품 id.
 */
data class Stats(
    val lettersDone: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val wordsKnown: Int = 0,
    val wordsOwned: Int = 0,
    val readAlone: Int = 0,
    val lettersAlone: Int = 0,
    val lettersSealed: Int = 0,
    val chapters: Int = 0,
    val shadowPassed: Int = 0,
    val pieces: Int = 0,
    val readAlongStars: Int = 0,
    val walksDone: Int = 0,
    val seriesCompleted: Set<String> = emptySet(),
)

/** 이어 읽기. 하루를 건너뛰어도 쉼표가 있으면 이어진다. */
object Streak {
    const val MAX_RESTS = 2

    fun after(lastDay: Long, count: Int, today: Long): Int = afterWithRest(lastDay, count, today, 0).first

    /** (새 이어 읽기 수, 쓴 쉼표 수). 빈 날마다 쉼표 하나. */
    fun afterWithRest(lastDay: Long, count: Int, today: Long, rests: Int): Pair<Int, Int> {
        val gap = today - lastDay
        return when {
            gap <= 0L -> count.coerceAtLeast(1) to 0
            gap == 1L -> count + 1 to 0
            count > 0 && gap - 1 <= rests -> count + 1 to (gap - 1).toInt()
            else -> 1 to 0
        }
    }

    /** 오늘 보이는 이어 읽기 수: 어제까지 이어졌거나 쉼표로 메울 수 있으면 그대로, 아니면 0. */
    fun shown(lastDay: Long, count: Int, today: Long, rests: Int): Int =
        if (today - lastDay <= 1 + rests) count else 0
}
