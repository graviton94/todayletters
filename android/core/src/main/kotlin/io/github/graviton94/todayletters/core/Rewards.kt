package io.github.graviton94.todayletters.core

/**
 * 보상 (모든 시리즈 공통 틀). 화폐는 시리즈마다 따로 (반 고흐 = 우표, 모차르트 = 두카트: 이름만 키트에서).
 * 배운 만큼 모이고, 같은 일 반복에는 하루 상한. 배움은 막지 않는다: 편지 · 복습 · 힌트는 화폐 없이 늘 열려 있고,
 * 화폐로는 쉼표 · 다음 편지 미리 열기 · 갤러리 소장품 고르기와 꾸미기(액자 · 카드 테두리)만 산다.
 */
enum class Earn(val amount: Int, val dailyCap: Int) {
    LETTER(6, 18),          // 새 편지 다 읽기
    PRACTICE(2, 18),        // 낱말 맞추기 · 따라 읽기 · 답장
    REVIEW_RIGHT(1, 12),    // 복습에서 맞힌 낱말 하나
    SHADOW_PASS(2, 20),     // 따라 읽기 마디 통과
    READ_ALONE(5, 50),      // 혼자 읽기 1% 오를 때마다
    QUESTS(10, 10),         // 오늘의 할 일 셋 다
    RECITAL(15, 15),        // 일요일 낭독회
    STREAK(4, 4),           // 이어 읽은 날 (하루 한 번)
    ACHIEVEMENT(10, 100),   // 이정표
    SEAL(20, 100),          // 편지 한 통 완독 봉인 (혼자 다 읽고 다 따라 읽음)
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

/** 이정표 판정에 쓰는 숫자들. 시리즈 하나의 것이거나 (시리즈 이정표) 모두 합친 것 (전체 이정표). */
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
    /** 한 번이라도 시작한 시리즈 수 · 따라 읽기를 한 언어 수 (전체 이정표용). */
    val seriesStarted: Int = 0,
    val langsSpoken: Int = 0,
)

/** 이정표 하나: [metric] 이 [goal] 이상이면 받는다. 이름은 글자 목록의 ach_<id>. [all] 이면 전체 이정표. */
data class Achievement(val id: String, val goal: Int, val all: Boolean = false, val metric: (Stats) -> Int)

object Achievements {
    /** 시리즈 이정표: 어느 시리즈에나 같은 틀. 몇 개마다 갤러리에 명작 한 점이 확정으로 걸린다. */
    val series = listOf(
        Achievement("alone_1", 1) { it.lettersSealed },
        Achievement("streak_7", 7) { it.bestStreak },
        Achievement("words_50", 50) { it.wordsKnown },
        Achievement("chapter_1", 1) { it.chapters },
        Achievement("shadow_100", 50) { it.shadowPassed },   // 열쇠는 그대로, v18 부터 문장 50개
        Achievement("pieces_12", 12) { it.pieces },
        Achievement("read_50", 50) { it.readAlone },
        Achievement("read_90", 90) { it.readAlone },
    )

    /** 전체 이정표: 모든 시리즈를 합쳐서. */
    val overall = listOf(
        Achievement("all_series_2", 2, true) { it.seriesStarted },
        Achievement("all_langs_2", 2, true) { it.langsSpoken },
        Achievement("all_letters_30", 30, true) { it.lettersDone },
        Achievement("all_streak_30", 30, true) { it.bestStreak },
        Achievement("all_streak_100", 100, true) { it.bestStreak },
    )

    /** 지금 숫자로 받을 수 있는데 아직 안 받은 이정표. [have] 의 키는 시리즈 것은 "<시리즈>:<id>", 전체는 "<id>". */
    fun newly(list: List<Achievement>, stats: Stats, have: Set<String>, series: String? = null): List<Achievement> =
        list.filter { key(it, series) !in have && it.metric(stats) >= it.goal }

    fun key(a: Achievement, series: String?) = if (a.all || series == null) a.id else "$series:${a.id}"

    /** 이정표의 진행 (0~1). */
    fun progress(a: Achievement, stats: Stats): Float = (a.metric(stats).toFloat() / a.goal).coerceIn(0f, 1f)

    /** 이 이정표를 받으면 명작 한 점이 확정인가: 시리즈 이정표 세 개마다. */
    fun grantsMasterpiece(index: Int) = (index + 1) % 3 == 0
}

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
